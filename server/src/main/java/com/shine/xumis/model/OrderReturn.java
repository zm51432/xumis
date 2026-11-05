package com.shine.xumis.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货明细：一条退货单里某个商品退了几件。
 *
 * <p>这是「退货减销量」的唯一事实来源：售出量 = 有效订单明细数量 − 已生效退货数量。
 * 之所以单独建表而不直接改订单明细，是因为需要支持「部分退货」「同一订单多次退货」，
 * 也要保留原始成交记录供财务对账。</p>
 *
 * <p>由 ERP 审批退货后回调网站写入；return_no + product_id 唯一，保证重复回调不会重复扣减。</p>
 */
@Entity
@Table(name = "order_return",
        uniqueConstraints = @UniqueConstraint(columnNames = {"return_no", "product_id"}))
public class OrderReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, length = 64)
    private String returnNo;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(length = 500)
    private String reason;

    /** APPLIED 生效（计入扣减）；REVOKED 撤销退货 */
    @Column(length = 24, nullable = false)
    private String status = "APPLIED";

    @Column(length = 32)
    private String source = "ERP";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public OrderReturn() {
    }

    public Long getId() { return id; }
    public String getReturnNo() { return returnNo; }
    public void setReturnNo(String returnNo) { this.returnNo = returnNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
