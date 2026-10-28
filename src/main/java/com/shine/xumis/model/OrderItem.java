package com.shine.xumis.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * 订单明细行：快照商品下单时的名称与价格。
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    private Long productId;

    private String productName;

    private String productNameEn;

    /** 下单时单价（基准币种 USD） */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Integer quantity = 1;

    private String imageUrl;

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductNameEn() { return productNameEn; }
    public void setProductNameEn(String productNameEn) { this.productNameEn = productNameEn; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
