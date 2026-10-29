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

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
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
}
