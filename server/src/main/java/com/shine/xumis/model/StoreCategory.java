package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 分类实体（P2-1）：从枚举升级为可增删的数据表，旧 category 枚举值仍兼容。 */
@Entity
@Table(name = "store_category")
public class StoreCategory {
    @Id @Column(length = 60) private String code;
    @Column(length = 120) private String nameZh;
    @Column(length = 120) private String nameEn;
    private Integer sortOrder = 0;
    private boolean enabled = true;
    /** true = 由旧枚举映射出来的内置分类，不允许删除 */
    private boolean builtin = false;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
