package com.shine.xumis.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 商品实体：饰品 / 手工品等
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商品名称（多语言展示由前端 i18n 处理，这里存默认中文名） */
    @Column(nullable = false)
    private String name;

    /** 英文名称，用于外贸展示 */
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    /** 简介（中文） */
    @Column(length = 1000)
    private String description;

    /** 简介（英文） */
    @Column(length = 1000)
    private String descriptionEn;

    /** 价格，单位：美元（外贸默认结算币种） */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** 主图路径（相对 /uploads/ 或外部 URL） */
    private String imageUrl;

    /** 材质 */
    private String material;

    /** 是否支持客户自定义 */
    private boolean customizable = false;

    /** 库存 */
    private Integer stock = 0;

    /** 是否在首页精选展示 */
    private boolean featured = false;

    public Product() {
    }

    public Product(String name, String nameEn, Category category, BigDecimal price, String imageUrl) {
        this.name = name;
        this.nameEn = nameEn;
        this.category = category;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    // ===== getter / setter =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public boolean isCustomizable() { return customizable; }
    public void setCustomizable(boolean customizable) { this.customizable = customizable; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
}
