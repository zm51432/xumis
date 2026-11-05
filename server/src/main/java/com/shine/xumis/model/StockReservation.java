package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 库存预占（P0-3）：下单锁定 30 分钟，未支付超时自动释放并回滚库存。 */
@Entity
@Table(name = "stock_reservation", indexes = {
        @Index(name = "idx_sr_order", columnList = "order_no"),
        @Index(name = "idx_sr_expire", columnList = "status,expires_at")
})
public class StockReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_no", nullable = false, length = 40) private String orderNo;
    @Column(name = "product_id", nullable = false) private Long productId;
    private Long skuId;
    private Integer qty = 0;
    /** HELD / CONFIRMED / RELEASED */
    @Column(length = 20) private String status = "HELD";
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
