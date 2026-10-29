package com.shine.xumis.dto;

import com.shine.xumis.model.Product;

import java.math.BigDecimal;

/**
 * 手机端 API 商品视图（轻量字段，便于 App / 小程序消费）
 */
public class MobileProductDto {
    public Long id;
    public String name;
    public String nameEn;
    public String displayName;
    public String category;
    public BigDecimal price;
    public String imageUrl;
    public Boolean customizable;
    public String material;
    public Integer stock;
    /** 是否在首页精选展示 */
    public Boolean featured;
    /** 商品简介（中文） */
    public String description;
    /** 是否有货 */
    public Boolean inStock;

    private static final String DEFAULT_IMG = "/img/products/p01_necklace_star_heart.png";

    public static MobileProductDto from(Product p, boolean english) {
        MobileProductDto d = new MobileProductDto();
        d.id = p.getId();
        d.name = p.getName();
        d.nameEn = p.getNameEn();
        d.displayName = english ? p.getNameEn() : p.getName();
        d.category = p.getCategory() != null ? p.getCategory().getLabel() : null;
        d.price = p.getPrice();
        d.imageUrl = p.getImageUrl() != null && !p.getImageUrl().isBlank() ? p.getImageUrl() : DEFAULT_IMG;
        d.customizable = p.isCustomizable();
        d.material = p.getMaterial();
        d.stock = p.getStock();
        d.featured = p.isFeatured();
        d.description = p.getDescription();
        d.inStock = p.getStock() != null && p.getStock() > 0;
        return d;
    }
}
