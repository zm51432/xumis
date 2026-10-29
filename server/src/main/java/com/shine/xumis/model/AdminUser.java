package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 后台管理员账号：密码用 BCrypt 存储，配合 session 登录与角色控制（RBAC）。
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt 哈希，永不明文存储 */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role = AdminRole.OPERATOR;

    private boolean enabled = true;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public AdminUser() {
    }

    public AdminUser(String username, String passwordHash, String displayName, AdminRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    // ===== 便捷判断 =====
    public boolean isOwner() {
        return role == AdminRole.OWNER;
    }

    /** 是否可写（只读角色不能改任何数据） */
    public boolean canWrite() {
        return enabled && (role == AdminRole.OWNER || role == AdminRole.OPERATOR);
    }

    /** 是否可管理管理员账号（仅站长） */
    public boolean canManageAdmins() {
        return enabled && role == AdminRole.OWNER;
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public AdminRole getRole() { return role; }
    public void setRole(AdminRole role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
