package com.shine.xumis.model;

/**
 * 商品分类枚举：饰品 / 手工品 / 其他
 */
public enum Category {
    JEWELRY("饰品"),
    HANDMADE("手工品"),
    OTHER("其他");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
