package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 邮件订阅（Newsletter）：用于跨境独立站留存客户。
 */
@Entity
@Table(name = "subscription", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private LocalDateTime createdAt;

    /** 一次性退订令牌（P1-5） */
    @Column(length = 64) private String unsubscribeToken;
    /** ACTIVE / UNSUBSCRIBED */
    @Column(length = 20) private String status = "ACTIVE";
    private LocalDateTime unsubscribedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (unsubscribeToken == null || unsubscribeToken.isBlank()) {
            unsubscribeToken = java.util.UUID.randomUUID().toString().replace("-", "")
                    + java.util.UUID.randomUUID().toString().replace("-", "");
        }
        if (status == null || status.isBlank()) status = "ACTIVE";
    }

    public Subscription() {
    }

    public Subscription(String email) {
        this.email = email;
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUnsubscribeToken() { return unsubscribeToken; }
    public void setUnsubscribeToken(String unsubscribeToken) { this.unsubscribeToken = unsubscribeToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getUnsubscribedAt() { return unsubscribedAt; }
    public void setUnsubscribedAt(LocalDateTime unsubscribedAt) { this.unsubscribedAt = unsubscribedAt; }
}
