package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 商品规格/SKU（P1-2）：尺寸、颜色、材质等，可带价格增量与独立库存。 */
@Entity
@Table(name = "product_sku", indexes = @Index(name = "idx_psku_product", columnList = "product_id"))
public class ProductSku {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(length = 80) private String skuCode;
    /** 规格名：如 尺寸 / Size */
    @Column(length = 60) private String optionName;
    /** 规格值：如 18cm / 金色 */
    @Column(length = 120) private String optionValue;
    @Column(length = 120) private String optionValueEn;
    /** 相对基准价的加价（USD），可为 0 */
    @Column(precision = 10, scale = 2) private BigDecimal priceDelta = BigDecimal.ZERO;
    private Integer stock = 0;
    private boolean enabled = true;
    private Integer sortOrder = 0;

    public ProductSku() {}
    public String getLabel() {
        return (optionName == null ? "" : optionName + "：") + (optionValue == null ? "" : optionValue);
    }
    public String getLabelEn() {
        return (optionValueEn == null || optionValueEn.isBlank()) ? getLabel() : optionValueEn;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getOptionName() { return optionName; }
    public void setOptionName(String optionName) { this.optionName = optionName; }
    public String getOptionValue() { return optionValue; }
    public void setOptionValue(String optionValue) { this.optionValue = optionValue; }
    public String getOptionValueEn() { return optionValueEn; }
    public void setOptionValueEn(String optionValueEn) { this.optionValueEn = optionValueEn; }
    public BigDecimal getPriceDelta() { return priceDelta; }
    public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
