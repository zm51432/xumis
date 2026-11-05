package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.ProductSku;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 商品报文；forceStock=true 才允许用网站库存覆盖 ERP 手工调整结果。阶段2 起携带 SKU 主数据与独立库存。 */
public class ErpProductDto {
    public Long id;
    public String name;
    public String nameEn;
    public String category;
    public String categoryCode;
    public BigDecimal price;
    public String currency = "USD";
    public String material;
    public Integer stock;
    public Boolean customizable;
    public String imageUrl;
    public Boolean forceStock;
    public List<SkuDto> skus = new ArrayList<>();

    public static class SkuDto {
        public Long skuId;
        public String skuCode;
        public String optionName;
        public String optionValue;
        public String optionValueEn;
        public BigDecimal priceDelta;
        public Integer stock;
        public Boolean enabled;
    }

    public ErpProductDto() {}

    public static ErpProductDto from(Product p) { return from(p, false, List.of()); }
    public static ErpProductDto from(Product p, boolean forceStock) { return from(p, forceStock, List.of()); }

    public static ErpProductDto from(Product p, boolean forceStock, List<ProductSku> skuList) {
        ErpProductDto d = new ErpProductDto();
        d.id = p.getId();
        d.name = p.getName();
        d.nameEn = p.getNameEn();
        d.category = p.getCategory() != null ? p.getCategory().name() : null;
        d.categoryCode = p.getCategoryCode();
        d.price = p.getPrice();
        d.material = p.getMaterial();
        d.stock = p.getStock();
        d.customizable = p.isCustomizable();
        d.imageUrl = p.getImageUrl();
        d.forceStock = forceStock;
        if (skuList != null) {
            for (ProductSku s : skuList) {
                SkuDto x = new SkuDto();
                x.skuId = s.getId();
                x.skuCode = s.getSkuCode();
                x.optionName = s.getOptionName();
                x.optionValue = s.getOptionValue();
                x.optionValueEn = s.getOptionValueEn();
                x.priceDelta = s.getPriceDelta();
                x.stock = s.getStock();
                x.enabled = s.isEnabled();
                d.skus.add(x);
            }
        }
        return d;
    }
}
