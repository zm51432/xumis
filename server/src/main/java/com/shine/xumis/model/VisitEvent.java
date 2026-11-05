package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 站内轻量埋点（P2-3）：VIEW / ADD_CART / ORDER / SUBSCRIBE，不依赖第三方。 */
@Entity
@Table(name = "visit_event", indexes = {
        @Index(name = "idx_ve_day_type", columnList = "day,type"),
        @Index(name = "idx_ve_created", columnList = "created_at")
})
public class VisitEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 20) private String type;
    @Column(length = 300) private String path;
    private Long productId;
    /** 会话哈希，不存原始 IP / Cookie，符合最小化采集 */
    @Column(length = 64) private String visitor;
    @Column(length = 10) private String day;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public VisitEvent() {}
    public VisitEvent(String type, String path, Long productId, String visitor) {
        this.type = type; this.path = path; this.productId = productId; this.visitor = visitor;
        LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.day = now.toLocalDate().toString();
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getVisitor() { return visitor; }
    public void setVisitor(String visitor) { this.visitor = visitor; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
