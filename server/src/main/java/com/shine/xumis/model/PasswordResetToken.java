package com.shine.xumis.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 找回密码令牌。
 * <p>
 * 与旧实现（UserAccount.resetToken 单字段、新请求覆盖旧令牌）不同，这里每次「忘记密码」
 * 都插入一条独立记录，因此同一账号可以有多条链接同时有效（各自 1 小时内有效）。
 * 只有真正用某条链接完成重置后，才会把该用户其余未使用的令牌一并作废。
 */
@Entity
@Table(name = "password_reset_token", indexes = {
        @Index(name = "idx_prt_token", columnList = "token", unique = true),
        @Index(name = "idx_prt_user", columnList = "user_id")
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属客户账号 id（不建外键约束，账号删除后令牌自然失效） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
