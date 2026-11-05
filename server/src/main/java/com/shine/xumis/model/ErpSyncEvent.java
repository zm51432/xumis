package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 网站 → ERP 同步事件台账：每次推送都留痕，失败可重放。
 * 之前 HttpErpClient 把异常直接吞掉，ERP 挂了/超时完全无感知，也无法补推。
 */
@Entity
@Table(name = "erp_sync_event", indexes = {
        @Index(name = "idx_ese_status", columnList = "status,next_retry_at"),
        @Index(name = "idx_ese_created", columnList = "created_at")
})
public class ErpSyncEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    /** OUT = 网站推 ERP；IN = ERP 回调 */
    @Column(length = 8) private String direction = "OUT";
    /** PRODUCT / ORDER / ORDER_DELETE / PRODUCT_DELETE / ORDER_CANCEL / INQUIRY / CUSTOM_IDEA / INVENTORY_SYNC / RECONCILE */
    @Column(length = 32) private String entity;
    @Column(length = 80) private String refNo;
    @Column(length = 120) private String endpoint;
    @Column(length = 16) private String status = "OK";   // OK / FAIL / GAVE_UP
    @Lob @Column(columnDefinition = "LONGTEXT") private String message;
    /** 原始 JSON 报文，失败后按原样重放（不依赖实体当前状态） */
    @Lob @Column(columnDefinition = "LONGTEXT") private String payload;
    private Integer attempts = 0;
    private LocalDateTime createdAt;
    private LocalDateTime nextRetryAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }
    public String getRefNo() { return refNo; }
    public void setRefNo(String refNo) { this.refNo = refNo; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
}
