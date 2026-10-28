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

    public static MobileProductDto from(Product p, boolean english) {
        MobileProductDto d = new MobileProductDto();
        d.id = p.getId();
        d.name = p.getName();
        d.nameEn = p.getNameEn();
        d.displayName = english ? p.getNameEn() : p.getName();
        d.category = p.getCategory() != null ? p.getCategory().getLabel() : null;
        d.price = p.getPrice();
        d.imageUrl = p.getImageUrl();
        d.customizable = p.isCustomizable();
        d.material = p.getMaterial();
        d.stock = p.getStock();
        return d;
    }
}
