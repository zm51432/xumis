package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.Product;

import java.math.BigDecimal;

/**
 * 推送给 ERP 的商品报文（字段按通用 ERP 商品结构预留，可按目标 ERP 调整）
 */
public class ErpProductDto {
    public Long id;
    public String name;
    public String nameEn;
    public String category;
    public BigDecimal price;
    public String currency = "USD";
    public String material;
    public Integer stock;
    public Boolean customizable;
    public String imageUrl;

    public ErpProductDto() {}

    public static ErpProductDto from(Product p) {
        ErpProductDto d = new ErpProductDto();
        d.id = p.getId();
        d.name = p.getName();
        d.nameEn = p.getNameEn();
        d.category = p.getCategory() != null ? p.getCategory().name() : null;
        d.price = p.getPrice();
        d.material = p.getMaterial();
        d.stock = p.getStock();
        d.customizable = p.isCustomizable();
        d.imageUrl = p.getImageUrl();
        return d;
    }
}
