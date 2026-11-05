package com.shine.xumis.model;

import jakarta.persistence.*;

/**
 * 站点设置：单表 key-value。
 *
 * <p>把「店铺名、首页文案、滚动字幕、运费、SEO、分类显示名」这些
 * 以前写死在模板/枚举里的东西，改成后台可改的数据，避免每次改一句话都要重新发版。</p>
 */
@Entity
@Table(name = "site_setting")
public class SiteSetting {

    @Id
    @Column(name = "skey", length = 100, nullable = false)
    private String key;

    @Column(name = "svalue", length = 2000)
    private String value;

    public SiteSetting() {
    }

    public SiteSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
