package com.shine.xumis.service;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.*;
import com.shine.xumis.repository.OrderReturnRepository;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.repository.ReturnRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 网站侧退货闭环（阶段1）：客户申请 → 后台审批 → 生效退货 → 库存回滚 / 销量扣减 → ERP / 邮件。
 *
 * <p>唯一事实来源仍是 order_return（APPLIED），ERP 原有回调路径完全兼容。</p>
 */
@Service
public class ReturnService {

    public record Result(boolean ok, String message, String requestNo) {}

    private final ReturnRequestRepository requests;
    private final OrderReturnRepository returns;
    private final com.shine.xumis.repository.OrderRepository orderRepository;
    private final ProductService products;
    private final ProductSkuRepository skus;
    private final ErpSyncService erpSync;
    private final OrderMailService orderMail;
    private final MailService mail;
    private final SettingsService settings;

    @Value("${app.site-base-url:https://shine.xumois.cn}")
    private String siteBaseUrl;

    public ReturnService(ReturnRequestRepository requests, OrderReturnRepository returns,
                         com.shine.xumis.repository.OrderRepository orderRepository,
                         ProductService products, ProductSkuRepository skus,
                         ErpSyncService erpSync, OrderMailService orderMail,
                         MailService mail, SettingsService settings) {
        this.requests = requests;
        this.returns = returns;
        this.orderRepository = orderRepository;
        this.products = products;
        this.skus = skus;
        this.erpSync = erpSync;
        this.orderMail = orderMail;
        this.mail = mail;
        this.settings = settings;
    }

    /** 客户提交申请：校验订单归属、可退数量，写入 REQUESTED（不影响销量） */
    @Transactional
    public Result request(String orderNo, Long userId, Map<Long, Integer> qtyMap, String reason) {
        Order o = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (o == null) return new Result(false, "订单不存在", null);
        if (o.getUserId() == null || !o.getUserId().equals(userId))
            return new Result(false, "只能对自己的订单申请退货", null);
        if ("CANCELLED".equals(o.getStatus()))
            return new Result(false, "已取消的订单不能申请退货", null);
        if (o.getItems() == null || o.getItems().isEmpty())
            return new Result(false, "订单没有可退商品", null);

        String rn = "WR" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        List<ReturnRequest> created = new ArrayList<>();
        for (OrderItem it : o.getItems()) {
            Long pid = it.getProductId();
            int want = qtyMap.getOrDefault(pid, 0);
            if (want <= 0) continue;
            int bought = it.getQuantity() == null ? 0 : it.getQuantity();
            int done = returns.sumReturned(orderNo, pid);
            int pending = requests.sumPending(orderNo, pid);
            if (want > bought - done - pending) {
                return new Result(false, "商品 #" + pid + " 最多可再退 " + Math.max(0, bought - done - pending) + " 件", null);
            }
            ReturnRequest rr = new ReturnRequest();
            rr.setRequestNo(rn);
            rr.setOrderNo(orderNo);
            rr.setUserId(userId);
            rr.setProductId(pid);
            rr.setSkuId(it.getSkuId());
            rr.setProductName(it.getProductName());
            rr.setQuantity(want);
            rr.setRefundAmount((it.getPrice() == null ? BigDecimal.ZERO : it.getPrice())
                    .multiply(BigDecimal.valueOf(want)));
            rr.setReason(reason);
            rr.setStatus("REQUESTED");
            rr.setSource("WEB");
            created.add(rr);
        }
        if (created.isEmpty()) return new Result(false, "请至少选择一件商品", null);
        requests.saveAll(created);
        requests.flush();

        try { notify(o, rn, "退货申请已收到", "我们已收到你的退货申请，审核结果会邮件通知你。", created); }
        catch (Exception ignored) {}
        return new Result(true, "退货申请已提交，等待审核", rn);
    }

    /** 后台审批通过：写生效退货、回滚库存、更新订单状态、推 ERP、发邮件 */
    @Transactional
    public Result approve(String requestNo, String reviewer, String note) {
        List<ReturnRequest> list = requests.findByRequestNoAndStatusOrderByIdAsc(requestNo, "REQUESTED");
        if (list.isEmpty()) return new Result(false, "申请不存在或已处理", requestNo);
        Order o = orderRepository.findByOrderNo(list.get(0).getOrderNo()).orElse(null);
        if (o == null) return new Result(false, "原订单不存在", requestNo);

        BigDecimal refund = BigDecimal.ZERO;
        List<Map<String, Object>> items = new ArrayList<>();
        for (ReturnRequest rr : list) {
            // 幂等：同一申请同一商品只生效一次
            if (returns.findByReturnNoAndProductId(requestNo, rr.getProductId()).isEmpty()) {
                OrderReturn r = new OrderReturn();
                r.setReturnNo(requestNo);
                r.setOrderNo(rr.getOrderNo());
                r.setProductId(rr.getProductId());
                r.setQuantity(rr.getQuantity());
                r.setRefundAmount(rr.getRefundAmount());
                r.setReason(rr.getReason());
                r.setStatus("APPLIED");
                r.setSource("WEB");
                returns.save(r);
                restock(rr.getProductId(), rr.getSkuId(), rr.getQuantity());
                refund = refund.add(rr.getRefundAmount() == null ? BigDecimal.ZERO : rr.getRefundAmount());
            }
            rr.setStatus("APPROVED");
            rr.setReviewedAt(LocalDateTime.now());
            rr.setReviewNote(note);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", rr.getProductId());
            m.put("quantity", rr.getQuantity());
            items.add(m);
        }
        requests.saveAll(list);
        returns.flush();
        updateOrderStatus(o);
        orderRepository.save(o);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("returnNo", requestNo);
        payload.put("orderNo", o.getOrderNo());
        payload.put("reason", list.get(0).getReason());
        payload.put("refundAmount", refund);
        payload.put("source", "WEB");
        payload.put("items", items);
        try { erpSync.syncReturn(requestNo, payload); } catch (Exception ignored) {}
        try { orderMail.statusChanged(o); } catch (Exception ignored) {}

        return new Result(true, "已批准：库存回滚、销量扣减并同步 ERP", requestNo);
    }

    @Transactional
    public Result reject(String requestNo, String reviewer, String note) {
        List<ReturnRequest> list = requests.findByRequestNoAndStatusOrderByIdAsc(requestNo, "REQUESTED");
        if (list.isEmpty()) return new Result(false, "申请不存在或已处理", requestNo);
        for (ReturnRequest rr : list) {
            rr.setStatus("REJECTED");
            rr.setReviewedAt(LocalDateTime.now());
            rr.setReviewNote(note);
        }
        requests.saveAll(list);
        return new Result(true, "已驳回申请", requestNo);
    }

    /** 库存回滚：商品级 + SKU 级（阶段2 会同步给 ERP） */
    private void restock(Long productId, Long skuId, int qty) {
        Product p = products.findById(productId);
        if (p != null) {
            p.setStock((p.getStock() == null ? 0 : p.getStock()) + qty);
            products.save(p);
        }
        if (skuId != null) {
            skus.findById(skuId).ifPresent(sku -> {
                sku.setStock((sku.getStock() == null ? 0 : sku.getStock()) + qty);
                skus.save(sku);
                erpSync.syncSkuStock(sku.getId(), sku.getStock());
            });
        }
    }

    private void updateOrderStatus(Order o) {
        int bought = 0;
        if (o.getItems() != null) for (OrderItem it : o.getItems()) bought += it.getQuantity() == null ? 0 : it.getQuantity();
        int returned = 0;
        for (OrderReturn r : returns.findByOrderNoOrderByIdAsc(o.getOrderNo())) {
            if ("APPLIED".equals(r.getStatus()) && r.getQuantity() != null) returned += r.getQuantity();
        }
        o.setStatus(returned >= bought ? "REFUNDED" : "PARTIAL_RETURN");
    }

    private void notify(Order o, String requestNo, String title, String lead, List<ReturnRequest> lines) {
        if (o.getEmail() == null || o.getEmail().isBlank()) return;
        String store = settings.getOr(SettingsService.SITE_NAME, "shine星心");
        StringBuilder sb = new StringBuilder("<ul>");
        for (ReturnRequest r : lines) {
            sb.append("<li>").append(r.getProductName() == null ? ("#" + r.getProductId()) : r.getProductName())
              .append(" × ").append(r.getQuantity()).append("</li>");
        }
        sb.append("</ul>");
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:auto;line-height:1.7\">"
                + "<h2>" + store + " · " + title + "</h2><p>" + lead + "</p>"
                + "<p><strong>申请单号：</strong>" + requestNo + "<br><strong>原订单：</strong>" + o.getOrderNo() + "</p>"
                + sb + "<p><a href=\"" + siteBaseUrl + "/order/track?no=" + o.getOrderNo() + "\">查看订单 →</a></p></div>";
        mail.sendHtml(o.getEmail(), store + " · " + title + "（" + requestNo + "）", html, "退货通知");
    }
}
