package com.shine.xumis.integration.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shine.xumis.integration.erp.dto.*;
import com.shine.xumis.model.*;
import com.shine.xumis.repository.ErpSyncEventRepository;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.ProductSkuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ERP 同步编排：所有事件留痕，失败进入重试队列（原报文重放），业务主流程永不阻塞。
 */
@Service
public class ErpSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpSyncService.class);
    private final ErpProperties props;
    private final ErpClient client;
    private final ErpSyncEventRepository events;
    private final ObjectMapper mapper;
    private final OrderRepository orderRepository;
    private final ProductSkuRepository skuRepository;

    public ErpSyncService(ErpProperties props, ErpClient client,
                          ErpSyncEventRepository events, ObjectMapper mapper,
                          OrderRepository orderRepository, ProductSkuRepository skuRepository) {
        this.props = props;
        this.client = client;
        this.events = events;
        this.mapper = mapper;
        this.orderRepository = orderRepository;
        this.skuRepository = skuRepository;
    }

    private String json(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    private void record(String entity, String ref, String endpoint, String status,
                        String message, String payload, int attempts, LocalDateTime next) {
        try {
            ErpSyncEvent e = new ErpSyncEvent();
            e.setDirection("OUT"); e.setEntity(entity); e.setRefNo(ref); e.setEndpoint(endpoint);
            e.setStatus(status);
            e.setMessage(message == null ? "" : (message.length() > 1000 ? message.substring(0, 1000) : message));
            e.setPayload(payload); e.setAttempts(attempts); e.setNextRetryAt(next);
            events.save(e);
        } catch (Exception ex) {
            log.warn("[ERP] 写同步日志失败: {}", ex.getMessage());
        }
    }

    /** 统一推送：成功/失败都落一条事件；失败不抛给业务 */
    private void push(String entity, String ref, String endpoint, Object payload) {
        if (!props.isEnabled()) return;
        String body = "{}";
        try {
            body = json(payload);
            client.postRaw(endpoint, body);
            record(entity, ref, endpoint, "OK", "同步成功", body, 0, null);
        } catch (Exception e) {
            log.warn("[ERP] {} {} 推送失败: {}", entity, ref, e.getMessage());
            record(entity, ref, endpoint, "FAIL", e.getMessage(), body, 0,
                    LocalDateTime.now().plusSeconds(10));
        }
    }

    // ---------------- 业务事件 ----------------

    /** 后台明确保存商品：用户在表单里设了库存，按网站数值覆盖 ERP */
    public void syncProduct(Product p) {
        if (p == null) return;
        push("PRODUCT", String.valueOf(p.getId()), "/products",
                ErpProductDto.from(p, true, skuRepository.findByProductIdOrderBySortOrderAscIdAsc(p.getId())));
    }

    /** 全量补推主数据：不覆盖 ERP 手工调整的库存 */
    public void syncAllProducts(List<Product> products) {
        if (!props.isEnabled() || products == null) return;
        log.info("[ERP] 全量补推商品主数据 {} 条", products.size());
        for (Product p : products) {
            push("PRODUCT", String.valueOf(p.getId()), "/products",
                    ErpProductDto.from(p, false, skuRepository.findByProductIdOrderBySortOrderAscIdAsc(p.getId())));
        }
    }

    public void syncCustomIdea(CustomIdea c) {
        if (c == null) return;
        push("CUSTOM_IDEA", String.valueOf(c.getId()), "/custom-ideas", ErpCustomIdeaDto.from(c));
    }

    public void syncInquiry(Inquiry i) {
        if (i == null) return;
        push("INQUIRY", String.valueOf(i.getId()), "/inquiries", ErpInquiryDto.from(i));
    }

    /**
     * 后台/接口传来的 Order 常已脱离事务，items 可能未初始化。
     * ErpOrderDto.from 对明细做了容错（读不到就只推主表字段），因此这里不能开只读事务：
     * push 还要写 erp_sync_event，只读连接会拒绝 INSERT 并把整个请求标记 rollback-only。
     */
    public void syncOrder(Order o) {
        if (o == null || o.getOrderNo() == null) return;
        push("ORDER", o.getOrderNo(), "/orders", ErpOrderDto.from(o));
    }

    /** 阶段2：单个 SKU 库存变动（下单 / 退货 / ERP 回写） */
    public void syncSkuStock(Long skuId, Integer stock) {
        if (skuId == null) return;
        push("SKU_STOCK", String.valueOf(skuId), "/sku-stock",
                Map.of("skuId", skuId, "stock", stock == null ? 0 : stock));
    }

    /** 阶段2：库存预占事件（HELD / CONFIRMED / RELEASED），ERP 据此计算可售库存 */
    public void syncReservation(String orderNo, Object payload) {
        if (orderNo == null) return;
        push("RESERVATION", orderNo, "/reservations", payload);
    }

    /** 网站侧退货审批通过：推送 ERP 生成已审批退货单（阶段1） */
    public void syncReturn(String ref, Object payload) {
        if (ref == null) return;
        push("RETURN", ref, "/returns", payload);
    }

    /** 后台删除订单：ERP 同步删除销售单与明细，避免幽灵单 */
    public void deleteOrder(String orderNo) {
        if (orderNo == null) return;
        push("ORDER_DELETE", orderNo, "/orders/delete", Map.of("orderNo", orderNo));
    }

    /** 后台删除商品：ERP 侧软删除（active=0），网站后续同步不会把它救回来 */
    public void deleteProduct(Long id) {
        if (id == null) return;
        push("PRODUCT_DELETE", String.valueOf(id), "/products/delete", Map.of("id", id));
    }

    /** 订单取消 / 预占超时：ERP 幂等回补库存并更新销售单状态 */
    public void cancelOrder(String orderNo) {
        if (orderNo == null) return;
        push("ORDER_CANCEL", orderNo, "/orders/cancel", Map.of("orderNo", orderNo));
    }

    /** 以网站库存为准拉齐 ERP（手动/后台触发，不自动跑） */
    public void inventorySync() {
        push("INVENTORY_SYNC", "ALL", "/inventory/sync", Map.of());
    }

    public String reconcile() { return client.reconcile(); }

    public void recordReconcile(Map<String, Object> diff) {
        record("RECONCILE", "ALL", "/reconcile",
                Boolean.TRUE.equals(diff.get("ok")) ? "OK" : "FAIL", json(diff), "{}", 0, null);
    }

    // ---------------- 失败重试 ----------------

    /** 每 60 秒扫描失败事件，按原报文重放；最多 5 次后标记 GAVE_UP */
    @Scheduled(fixedDelay = 15_000L, initialDelay = 10_000L)
    public synchronized int retryFailedNow() {
        int ok = 0;
        List<ErpSyncEvent> list;
        try {
            list = events.findByStatusAndNextRetryAtBefore("FAIL", LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[ERP] 读取重试队列失败: {}", e.getMessage());
            return 0;
        }
        if (!list.isEmpty()) log.info("[ERP] 同步重试扫描：待重放 {} 条", list.size());
        for (ErpSyncEvent e : list) {
            int a = e.getAttempts() == null ? 0 : e.getAttempts();
            if (a >= 5) {
                e.setStatus("GAVE_UP");
                e.setMessage("重试 5 次仍失败，请在 ERP 同步页手动全量补推");
                e.setNextRetryAt(null);
                events.save(e);
                continue;
            }
            try {
                client.postRaw(e.getEndpoint(), e.getPayload() == null ? "{}" : e.getPayload());
                e.setStatus("OK");
                e.setMessage("第 " + (a + 1) + " 次重试成功");
                e.setAttempts(a + 1);
                e.setNextRetryAt(null);
                ok++;
            } catch (Exception ex) {
                e.setAttempts(a + 1);
                e.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, 15L << Math.min(a, 4))));
                e.setMessage("重试失败：" + ex.getMessage());
            }
            events.save(e);
        }
        return ok;
    }

    /** 每天清理 30 天前的同步事件，避免表无限增长 */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void cleanup() {
        try { events.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(30)); }
        catch (Exception ignored) {}
    }
}
