package com.shine.xumis.controller;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.OrderReturn;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.OrderReturnRepository;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP → 网站回调：退货生效、库存回写。
 *
 * <p>闭环的最后一环：ERP 审批退货后调用 /api/erp/callback/return，
 * 网站生成退货明细、回滚库存，并把这部分数量从「售出量 / 热销榜 / 推荐」里扣掉。</p>
 *
 * <p>安全：与网站→ERP 使用同一把 24 位 API Key（X-ERP-API-KEY），
 * 常量时间比较；/api/** 不走浏览器 CSRF 模型。</p>
 */
@RestController
@RequestMapping("/api/erp")
public class ErpCallbackController {

    private final OrderService orderService;
    private final ProductService productService;
    private final OrderReturnRepository returnRepository;
    private final com.shine.xumis.repository.ReturnRequestRepository requestRepository;
    private final ProductSkuRepository skuRepository;

    @Value("${app.erp.api-key:}")
    private String apiKey;

    public ErpCallbackController(OrderService orderService, ProductService productService,
                                 OrderReturnRepository returnRepository,
                                 com.shine.xumis.repository.ReturnRequestRepository requestRepository,
                                 ProductSkuRepository skuRepository) {
        this.orderService = orderService;
        this.productService = productService;
        this.returnRepository = returnRepository;
        this.requestRepository = requestRepository;
        this.skuRepository = skuRepository;
    }

    /** 退货单请求体 */
    public static class ReturnPayload {
        public String returnNo;
        public String orderNo;
        public String reason;
        public BigDecimal refundAmount;
        public List<ReturnLine> items;
    }

    public static class ReturnLine {
        public Long productId;
        public Integer quantity;
    }

    private boolean authorized(String key) {
        if (apiKey == null || apiKey.isBlank() || key == null) return false;
        return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 退货生效：
     *  · 校验退货数量不超过该订单该商品的「已买 − 已退」
     *  · 幂等：同一退货单同一商品重复回调不重复扣减
     *  · 库存按退货数量回滚
     *  · 订单状态改为 REFUNDED（全退）或 PARTIAL_RETURN（部分退）
     */
    @PostMapping("/callback/return")
    @Transactional
    public ResponseEntity<Map<String, Object>> applyReturn(
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String key,
            @RequestBody ReturnPayload payload) {

        Map<String, Object> body = new LinkedHashMap<>();
        if (!authorized(key)) {
            body.put("ok", false);
            body.put("error", "unauthorized");
            return ResponseEntity.status(401).body(body);
        }
        if (payload == null || payload.returnNo == null || payload.orderNo == null
                || payload.items == null || payload.items.isEmpty()) {
            body.put("ok", false);
            body.put("error", "badRequest");
            return ResponseEntity.badRequest().body(body);
        }

        Order order = orderService.findByOrderNo(payload.orderNo);
        if (order == null) {
            body.put("ok", false);
            body.put("error", "orderNotFound");
            return ResponseEntity.status(404).body(body);
        }

        List<Map<String, Object>> applied = new ArrayList<>();
        for (ReturnLine line : payload.items) {
            if (line == null || line.productId == null || line.quantity == null || line.quantity <= 0) {
                continue;
            }
            // 幂等：已经生效的退货行直接跳过
            if (returnRepository.findByReturnNoAndProductId(payload.returnNo, line.productId).isPresent()) {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("productId", line.productId);
                skip.put("quantity", 0);
                skip.put("note", "alreadyApplied");
                applied.add(skip);
                continue;
            }
            int bought = 0;
            if (order.getItems() != null) {
                for (OrderItem it : order.getItems()) {
                    if (it.getProductId() != null && it.getProductId().equals(line.productId)) {
                        bought += it.getQuantity() == null ? 0 : it.getQuantity();
                    }
                }
            }
            int already = returnRepository.sumReturned(payload.orderNo, line.productId);
            if (bought <= 0 || line.quantity > bought - already) {
                body.put("ok", false);
                body.put("error", "quantityExceeded");
                body.put("productId", line.productId);
                body.put("bought", bought);
                body.put("alreadyReturned", already);
                return ResponseEntity.badRequest().body(body);
            }

            OrderReturn r = new OrderReturn();
            r.setReturnNo(payload.returnNo);
            r.setOrderNo(payload.orderNo);
            r.setProductId(line.productId);
            r.setQuantity(line.quantity);
            r.setReason(payload.reason);
            r.setStatus("APPLIED");
            r.setSource("ERP");
            r.setRefundAmount(payload.refundAmount);
            returnRepository.save(r);

            // 库存回滚
            Product p = productService.findById(line.productId);
            int stockAfter = -1;
            if (p != null) {
                int before = p.getStock() == null ? 0 : p.getStock();
                p.setStock(before + line.quantity);
                productService.save(p);
                stockAfter = p.getStock();
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", line.productId);
            m.put("quantity", line.quantity);
            m.put("stockAfter", stockAfter);
            applied.add(m);
        }

        returnRepository.flush();

        // 全退 or 部分退
        int totalBought = 0;
        if (order.getItems() != null) {
            for (OrderItem it : order.getItems()) {
                totalBought += it.getQuantity() == null ? 0 : it.getQuantity();
            }
        }
        int totalReturned = 0;
        for (OrderReturn r : returnRepository.findByOrderNoOrderByIdAsc(payload.orderNo)) {
            if ("APPLIED".equals(r.getStatus()) && r.getQuantity() != null) {
                totalReturned += r.getQuantity();
            }
        }
        // ERP 审批可能覆盖客户在网站提交的待审申请：标记已处理，避免重复申请造成超退
        try {
            for (var rr : requestRepository.findByOrderNoAndStatusOrderByIdAsc(payload.orderNo, "REQUESTED")) {
                rr.setStatus("APPROVED");
                rr.setReviewedAt(java.time.LocalDateTime.now());
                rr.setReviewNote("ERP 退货单 " + payload.returnNo + " 已审批");
            }
            requestRepository.flush();
        } catch (Exception ignored) {}
        String newStatus = totalReturned >= totalBought ? "REFUNDED" : "PARTIAL_RETURN";
        order.setStatus(newStatus);
        orderService.save(order);

        Map<Long, Integer> sold = productService.soldMap();
        for (Map<String, Object> m : applied) {
            Object pid = m.get("productId");
            if (pid instanceof Number n) m.put("soldAfter", sold.getOrDefault(n.longValue(), 0));
        }

        body.put("ok", true);
        body.put("message", "退货已生效，销量已扣减");
        body.put("orderNo", payload.orderNo);
        body.put("orderStatus", newStatus);
        body.put("returnedTotal", totalReturned);
        body.put("applied", applied);
        return ResponseEntity.ok(body);
    }

    /** ERP 库存调整回写网站（采购入库 / 盘点） */
    @PostMapping("/callback/stock")
    @Transactional
    public ResponseEntity<Map<String, Object>> applyStock(
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String key,
            @RequestBody Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!authorized(key)) {
            body.put("ok", false);
            body.put("error", "unauthorized");
            return ResponseEntity.status(401).body(body);
        }
        Object pidO = payload.get("productId");
        Object stockO = payload.get("stock");
        if (pidO == null || stockO == null) {
            body.put("ok", false);
            body.put("error", "badRequest");
            return ResponseEntity.badRequest().body(body);
        }
        Long pid = Long.valueOf(pidO.toString());
        int stock = Integer.parseInt(stockO.toString());
        Product p = productService.findById(pid);
        if (p == null) {
            body.put("ok", false);
            body.put("error", "productNotFound");
            return ResponseEntity.status(404).body(body);
        }
        p.setStock(Math.max(0, stock));
        productService.save(p);
        body.put("ok", true);
        body.put("productId", pid);
        body.put("stock", p.getStock());
        return ResponseEntity.ok(body);
    }

    /** 阶段2：ERP 调整 SKU 库存后回写网站（绝对值口径，与商品级回写一致） */
    @PostMapping("/callback/sku-stock")
    @Transactional
    public ResponseEntity<Map<String, Object>> applySkuStock(
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String key,
            @RequestBody Map<String, Object> payload) {
        if (!authorized(key)) return ResponseEntity.status(401).body(Map.of("ok", false, "error", "unauthorized"));
        Object idO = payload.get("skuId"), stockO = payload.get("stock");
        if (idO == null || stockO == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "error", "badRequest"));
        Long id = Long.valueOf(idO.toString());
        int stock = Math.max(0, Integer.parseInt(stockO.toString()));
        return skuRepository.findById(id).map(sku -> {
            sku.setStock(stock);
            skuRepository.save(sku);
            return ResponseEntity.ok(Map.<String, Object>of("ok", true, "skuId", id, "stock", stock));
        }).orElseGet(() -> ResponseEntity.status(404).body(Map.of("ok", false, "error", "skuNotFound")));
    }

    /** 供 ERP 查询当前有效售出量（对账用） */
    @GetMapping("/callback/sold")
    public ResponseEntity<Map<String, Object>> soldSnapshot(
            @RequestHeader(value = "X-ERP-API-KEY", required = false) String key) {
        if (!authorized(key)) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "error", "unauthorized"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        Map<String, Integer> sold = new LinkedHashMap<>();
        productService.soldMap().forEach((k, v) -> sold.put(k.toString(), v));
        body.put("sold", sold);
        return ResponseEntity.ok(body);
    }
}
