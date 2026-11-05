package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 优惠券 / 满减（P1-4）。FIXED=固定金额，PERCENT=百分比，FREE_SHIP=免运费。 */
@Entity
@Table(name = "coupon")
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String code;
    @Column(length = 20) private String type = "FIXED";
    @Column(precision = 10, scale = 2) private BigDecimal value = BigDecimal.ZERO;
    /** 满减门槛（USD 商品小计） */
    @Column(precision = 10, scale = 2) private BigDecimal minSubtotal = BigDecimal.ZERO;
    /** 百分比券封顶金额，0 表示不封顶 */
    @Column(precision = 10, scale = 2) private BigDecimal maxDiscount = BigDecimal.ZERO;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Integer usageLimit = 0;   // 0 = 不限
    private Integer usedCount = 0;
    private boolean enabled = true;
    @Column(length = 300) private String note;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getMinSubtotal() { return minSubtotal; }
    public void setMinSubtotal(BigDecimal minSubtotal) { this.minSubtotal = minSubtotal; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
