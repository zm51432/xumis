package com.shine.xumis.service;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.ProductSku;
import com.shine.xumis.model.StockReservation;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.ProductRepository;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 库存预占（P0-3）：下单时锁定 30 分钟，超时未支付自动释放库存并取消订单；
 * 支付成功即确认占用，取消订单立即释放。避免高并发下的长期占用与超卖。
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final StockReservationRepository reservationRepo;
    private final ProductRepository productRepo;
    private final ProductSkuRepository skuRepo;
    private final OrderRepository orderRepo;
    private final SettingsService settings;
    private final com.shine.xumis.integration.erp.ErpSyncService erpSyncService;

    public StockReservationService(StockReservationRepository reservationRepo,
                                   ProductRepository productRepo, ProductSkuRepository skuRepo,
                                   OrderRepository orderRepo, SettingsService settings,
                                   com.shine.xumis.integration.erp.ErpSyncService erpSyncService) {
        this.reservationRepo = reservationRepo;
        this.productRepo = productRepo;
        this.skuRepo = skuRepo;
        this.orderRepo = orderRepo;
        this.settings = settings;
        this.erpSyncService = erpSyncService;
    }

    public int holdMinutes() {
        int v = settings.getInt("shop.reserveMinutes", 30);
        return Math.max(1, Math.min(v, 24 * 60));
    }

    /** 下单后为每一行建立预占记录 */
    @Transactional
    public void hold(String orderNo, Long productId, Long skuId, int qty) {
        StockReservation r = new StockReservation();
        r.setOrderNo(orderNo);
        r.setProductId(productId);
        r.setSkuId(skuId);
        r.setQty(qty);
        r.setStatus("HELD");
        r.setExpiresAt(LocalDateTime.now().plusMinutes(holdMinutes()));
        reservationRepo.save(r);
        pushReservation(orderNo, "HELD", List.of(r));
    }

    /** 把预占状态同步给 ERP：ERP 用「实物库存 − HELD 预占」计算可售库存，两边口径一致 */
    private void pushReservation(String orderNo, String status, List<StockReservation> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        java.time.LocalDateTime exp = null;
        for (StockReservation x : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", x.getProductId());
            m.put("skuId", x.getSkuId());
            m.put("qty", x.getQty());
            items.add(m);
            if (exp == null || (x.getExpiresAt() != null && x.getExpiresAt().isBefore(exp))) exp = x.getExpiresAt();
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderNo", orderNo);
        p.put("status", status);
        p.put("items", items);
        p.put("expiresAt", exp == null ? null : exp.toString());
        try { erpSyncService.syncReservation(orderNo, p); } catch (Exception ignored) {}
    }

    /** 支付/确认：占用转为永久扣减 */
    @Transactional
    public void confirm(String orderNo) {
        List<StockReservation> rows = reservationRepo.findByOrderNo(orderNo);
        setState(orderNo, "CONFIRMED");
        pushReservation(orderNo, "CONFIRMED", rows);
    }

    /** 取消/退款：立即回滚库存 */
    @Transactional
    public synchronized void release(String orderNo) {
        List<StockReservation> list = reservationRepo.findByOrderNo(orderNo);
        for (StockReservation r : list) {
            if (!"HELD".equals(r.getStatus())) continue;
            restock(r);
            r.setStatus("RELEASED");
            reservationRepo.save(r);
            erpSyncService.syncSkuStock(r.getSkuId(), skuRepo.findById(r.getSkuId() == null ? -1L : r.getSkuId()).map(ProductSku::getStock).orElse(null));
        }
        pushReservation(orderNo, "RELEASED", list);
    }

    private void setState(String orderNo, String status) {
        for (StockReservation r : reservationRepo.findByOrderNo(orderNo)) {
            if ("HELD".equals(r.getStatus())) {
                r.setStatus(status);
                reservationRepo.save(r);
            }
        }
    }

    private void restock(StockReservation r) {
        int qty = r.getQty() == null ? 0 : r.getQty();
        if (qty <= 0) return;
        productRepo.findById(r.getProductId()).ifPresent(p -> {
            p.setStock((p.getStock() == null ? 0 : p.getStock()) + qty);
            productRepo.save(p);
        });
        if (r.getSkuId() != null) {
            skuRepo.findById(r.getSkuId()).ifPresent(s -> {
                s.setStock((s.getStock() == null ? 0 : s.getStock()) + qty);
                skuRepo.save(s);
            });
        }
    }

    /**
     * 每 30 秒扫描过期预占：PENDING 订单自动取消 + 回滚库存，避免"僵尸占库存"。
     * 时间比较放在 Java 侧完成：MySQL 是 datetime 无时区，而 Hibernate 配了 jdbc.time_zone=UTC，
     * 用 SQL 的 expiresAt < :now 会因两边时区解释不同而漏判（实测过），Java 比较则完全确定。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 15_000L)
    @Transactional
    public synchronized void releaseExpired() {
        List<StockReservation> held = reservationRepo.findByStatus("HELD");
        if (held.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        List<StockReservation> expired = new ArrayList<>();
        for (StockReservation r : held) {
            if (r.getExpiresAt() == null || !r.getExpiresAt().isAfter(now)) expired.add(r);
        }
        log.info("库存预占扫描：held={} expired={}", held.size(), expired.size());
        if (expired.isEmpty()) return;
        Map<String, List<StockReservation>> byOrder = new LinkedHashMap<>();
        for (StockReservation r : expired) {
            byOrder.computeIfAbsent(r.getOrderNo(), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<StockReservation>> e : byOrder.entrySet()) {
            Order o = orderRepo.findByOrderNo(e.getKey()).orElse(null);
            if (o == null || !"PENDING".equalsIgnoreCase(o.getStatus())) {
                // 已经付款/处理过：只把占用确认，不回滚
                for (StockReservation r : e.getValue()) {
                    r.setStatus("CONFIRMED");
                    reservationRepo.save(r);
                }
                continue;
            }
            for (StockReservation r : e.getValue()) {
                restock(r);
                r.setStatus("RELEASED");
                reservationRepo.save(r);
                if (r.getSkuId() != null) {
                    erpSyncService.syncSkuStock(r.getSkuId(),
                            skuRepo.findById(r.getSkuId()).map(ProductSku::getStock).orElse(null));
                }
            }
            pushReservation(e.getKey(), "RELEASED", e.getValue());
            o.setStatus("CANCELLED");
            orderRepo.save(o);
            try {
                erpSyncService.syncOrder(o);
                erpSyncService.cancelOrder(o.getOrderNo());
            } catch (Exception ignored) {}
            log.info("库存预占超时释放：订单 {} 已自动取消，回滚 {} 行", e.getKey(), e.getValue().size());
        }
    }

    public long heldCount() { return reservationRepo.countByStatus("HELD"); }
}
