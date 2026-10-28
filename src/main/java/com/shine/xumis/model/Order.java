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

    @Column(nullable = false)
    private String status = "PENDING";

    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "order", orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
