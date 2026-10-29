package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 客户自定义想法（客户上传的设计灵感 / 定制需求）
 */
@Entity
@Table(name = "custom_idea")
public class CustomIdea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 客户称呼 */
    @Column(nullable = false)
    private String customerName;

    /** 联系邮箱 */
    @Column(nullable = false)
    private String email;

    /** 国家/地区 */
    private String country;

    /** 想法描述（客户想要什么样的饰品/手工品） */
    @Column(length = 2000, nullable = false)
    private String ideaDescription;

    /** 期望分类 */
    @Enumerated(EnumType.STRING)
    private Category expectCategory;

    /** 参考图路径（客户上传） */
    private String referenceImageUrl;

    /** 处理状态：NEW / CONTACTED / DONE */
    @Column(nullable = false)
    private String status = "NEW";

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public CustomIdea() {
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getIdeaDescription() { return ideaDescription; }
    public void setIdeaDescription(String ideaDescription) { this.ideaDescription = ideaDescription; }
    public Category getExpectCategory() { return expectCategory; }
    public void setExpectCategory(Category expectCategory) { this.expectCategory = expectCategory; }
    public String getReferenceImageUrl() { return referenceImageUrl; }
    public void setReferenceImageUrl(String referenceImageUrl) { this.referenceImageUrl = referenceImageUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
