package com.shine.xumis.model;

/**
 * 商品分类枚举：饰品 / 手工品 / 其他
 *
 * <p>显示名可在后台「站点设置 → 分类名称」里修改，
 * {@code SettingsService} 启动与保存后会把中文/英文名写回枚举，
 * 模板里继续用 {@code c.label} 即可，无需改动任何页面。</p>
 */
public enum Category {
    JEWELRY("饰品", "Jewelry"),
    HANDMADE("手工品", "Handmade"),
    OTHER("其他", "Other");

    private String label;
    private String labelEn;

    Category(String label, String labelEn) {
        this.label = label;
        this.labelEn = labelEn;
    }

    public String getLabel() {
        return label;
    }

    public String getLabelEn() {
        return labelEn;
    }

    /** 由 SettingsService 调用；传 null 表示该语言未自定义，保留默认值 */
    public void setLabels(String zh, String en) {
        if (zh != null && !zh.isBlank()) this.label = zh.trim();
        if (en != null && !en.isBlank()) this.labelEn = en.trim();
    }
}
