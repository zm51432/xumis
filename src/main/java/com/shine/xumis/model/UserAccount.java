package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * C 端客户账号：注册后可保存收货信息、查看自己的订单，下单无需重复填写。
 * 口令同样使用 PBKDF2 哈希存储。
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 昵称 / 收货人姓名 */
    private String name;

    private String phone;

    private String country;

    @Column(length = 500)
    private String address;

    private boolean enabled = true;

    /** 邮箱是否已验证（注册后为 false，点击邮件链接后变为 true） */
    private boolean emailVerified = false;

    /** 邮箱验证令牌（一次性，带过期时间） */
    @Column(length = 64)
    private String verifyToken;

    private LocalDateTime verifyTokenExpiry;

    /** 找回密码令牌（一次性，带过期时间） */
    @Column(length = 64)
    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }
    public LocalDateTime getVerifyTokenExpiry() { return verifyTokenExpiry; }
    public void setVerifyTokenExpiry(LocalDateTime verifyTokenExpiry) { this.verifyTokenExpiry = verifyTokenExpiry; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
