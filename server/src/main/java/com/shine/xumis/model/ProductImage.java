package com.shine.xumis.model;

import jakarta.persistence.*;

/** 商品附加图（P1-2）：一件商品 3-5 张图，主图仍在 product.image_url。 */
@Entity
@Table(name = "product_image", indexes = @Index(name = "idx_pi_product", columnList = "product_id"))
public class ProductImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(length = 500) private String url;
    private Integer sortOrder = 0;
    @Column(length = 200) private String alt;

    public ProductImage() {}
    public ProductImage(Long productId, String url, Integer sortOrder) {
        this.productId = productId; this.url = url; this.sortOrder = sortOrder;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }
}
