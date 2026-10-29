package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品评价：用户提交后默认 PENDING，经后台审核(APPROVED)才前台展示。
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String author;

    /** 评价人邮箱（登录账号邮箱，用于校验是否真的购买过） */
    @Column(length = 120)
    private String userEmail;

    /** 关联的订单号（购买凭证，便于后台核对） */
    @Column(length = 40)
    private String orderNo;

    /** 是否真实购买：由服务端按订单校验后写入，前台仅展示不允许伪造 */
    @Column(nullable = false)
    private boolean verifiedPurchase = false;

    /** 评分 1-5 */
    @Column(nullable = false)
    private Integer rating = 5;

    @Column(length = 1000)
    private String comment;

    /** PENDING / APPROVED */
    @Column(nullable = false)
    private String status = "PENDING";

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public boolean isVerifiedPurchase() { return verifiedPurchase; }
    public void setVerifiedPurchase(boolean verifiedPurchase) { this.verifiedPurchase = verifiedPurchase; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
