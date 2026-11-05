package com.shine.xumis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 内容页（P2-5）：关于我们 / 品牌故事 / 材质保养 / 尺寸指南 / 配送退换，后台可编辑。 */
@Entity
@Table(name = "content_page")
public class ContentPage {
    @Id @Column(length = 60) private String slug;
    @Column(length = 200) private String titleZh;
    @Column(length = 200) private String titleEn;
    @Lob @Column(columnDefinition = "LONGTEXT") private String bodyZh;
    @Lob @Column(columnDefinition = "LONGTEXT") private String bodyEn;
    private boolean published = true;
    private Integer sortOrder = 0;
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate public void touch() { updatedAt = LocalDateTime.now(); }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }
    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }
    public String getBodyZh() { return bodyZh; }
    public void setBodyZh(String bodyZh) { this.bodyZh = bodyZh; }
    public String getBodyEn() { return bodyEn; }
    public void setBodyEn(String bodyEn) { this.bodyEn = bodyEn; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
