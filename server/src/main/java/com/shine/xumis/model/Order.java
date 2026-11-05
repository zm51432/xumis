package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单：对标主流独立站的购物车结算单。
 * 状态：PENDING(待处理) / PAID(已付款) / SHIPPED(已发货) / DONE(完成) / CANCELLED(取消)
 */
@Entity
@Table(name = "shop_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 订单号（业务可读，如 SH20261028XXXX） */
    @Column(nullable = false, unique = true)
    private String orderNo;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String country;

    @Column(length = 1000)
    private String address;

    @Column(length = 1000)
    private String note;

    /** 订单总额（按下单时币种计） */
    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** 下单币种代码 */
    @Column(nullable = false)
    private String currency = "USD";

    /** 支付渠道：PAYPAL / WECHAT / ALIPAY / MASTERCARD（记录客户选择的支付方式） */
    @Column(length = 20)
    private String paymentMethod;

    @Column(nullable = false)
    private String status = "PENDING";

    private LocalDateTime createdAt;

    /** 下单用户 ID（游客下单为 null） */
    private Long userId;

    /** 最近一次状态变更时间（订单进度时间线用） */
    private LocalDateTime updatedAt;

    /** 商品小计（P1-4 优惠前） */
    @Column(precision = 12, scale = 2)
    private BigDecimal subtotalAmount;
    /** 优惠金额 */
    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    /** 使用的优惠码 */
    @Column(length = 60)
    private String couponCode;
    /** 运费（USD 基准，阶段1 起计入应付总额） */
    @Column(precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;
    /** 税费（USD 基准） */
    @Column(precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    /** 下单时税率快照（%） */
    @Column(precision = 6, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    /** 物流单号 / 承运商 / 查询网址 / 发货时间（P1-3） */
    @Column(length = 100) private String trackingNo;
    @Column(length = 80) private String carrier;
    @Column(length = 300) private String trackingUrl;
    private LocalDateTime shippedAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "order", orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.updatedAt == null) {
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingUrl() { return trackingUrl; }
    public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime shippedAt) { this.shippedAt = shippedAt; }
}
