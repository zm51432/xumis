package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 后台操作审计（阶段4）：记录所有 /admin/** 写请求，便于追溯谁在何时改了什么。 */
@Entity
@Table(name = "admin_audit_log", indexes = @Index(name = "idx_aal_time", columnList = "created_at"))
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 80) private String username;
    @Column(length = 40) private String role;
    @Column(length = 10) private String method;
    @Column(length = 200) private String path;
    @Column(length = 500) private String detail;
    @Column(length = 64) private String ip;
    @Column(name = "created_at") private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
