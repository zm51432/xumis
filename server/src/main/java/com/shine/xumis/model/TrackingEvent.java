package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 物流轨迹事件（P1-3）：后台逐条补录，前台查单页按时间倒序展示。 */
@Entity
@Table(name = "tracking_event", indexes = @Index(name = "idx_te_order", columnList = "order_no"))
public class TrackingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_no", nullable = false, length = 40) private String orderNo;
    private LocalDateTime eventTime;
    @Column(length = 200) private String location;
    @Column(length = 500) private String description;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (eventTime == null) eventTime = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
