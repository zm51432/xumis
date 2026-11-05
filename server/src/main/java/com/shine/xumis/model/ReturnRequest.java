package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网站侧退货申请（阶段1）：客户在「我的订单」发起，后台审批。
 *
 * <p>与 {@link OrderReturn} 分离：只有审批通过才写入 order_return（APPLIED）
 * 并扣减销量、回滚库存；申请阶段不影响任何统计口径。一个申请单可含多行商品，
 * 共用 requestNo。</p>
 */
@Entity
@Table(name = "return_request",
       indexes = @Index(name = "idx_rr_order", columnList = "order_no"),
       uniqueConstraints = @UniqueConstraint(columnNames = {"request_no", "product_id"}))
public class ReturnRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_no", nullable = false, length = 64) private String requestNo;
    @Column(name = "order_no", nullable = false, length = 64) private String orderNo;
    @Column(name = "user_id") private Long userId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "sku_id") private Long skuId;
    @Column(name = "product_name", length = 255) private String productName;
    @Column(nullable = false) private Integer quantity = 1;
    @Column(name = "refund_amount", precision = 12, scale = 2) private BigDecimal refundAmount;
    @Column(length = 500) private String reason;
    /** REQUESTED / APPROVED / REJECTED */
    @Column(length = 24, nullable = false) private String status = "REQUESTED";
    @Column(length = 32) private String source = "WEB";
    @Column(name = "review_note", length = 500) private String reviewNote;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;

    public ReturnRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
