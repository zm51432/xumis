package com.shine.xumis.dto;

import com.shine.xumis.model.Product;

import java.math.BigDecimal;

/**
 * 购物车展示行：商品快照 + 数量 + 行小计（基准 USD）。
 */
public class CartLine {
    private final Product product;
    private final int quantity;
    private final BigDecimal lineTotal;
    /** 选中的规格（P1-2），未选规格时为空串 */
    private String skuLabel = "";
    private BigDecimal skuPriceDelta = BigDecimal.ZERO;

    public CartLine(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.lineTotal = product.getPrice() == null
                ? BigDecimal.ZERO
                : product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    public void applySku(com.shine.xumis.model.ProductSku sku) {
        if (sku == null) return;
        this.skuLabel = sku.getLabel();
        BigDecimal delta = sku.getPriceDelta() == null ? BigDecimal.ZERO : sku.getPriceDelta();
        this.skuPriceDelta = delta;
        if (product.getPrice() != null) {
            // lineTotal 是 final，规格价差通过单独的 getLineTotalWithSku() 暴露
            this.withSkuTotal = product.getPrice().add(delta).multiply(BigDecimal.valueOf(quantity));
        }
    }

    private BigDecimal withSkuTotal;

    public BigDecimal getLineTotalWithSku() {
        return withSkuTotal == null ? lineTotal : withSkuTotal;
    }
    public BigDecimal getSkuPriceDelta() { return skuPriceDelta; }
    public String getSkuLabel() { return skuLabel; }
    public boolean isHasSku() { return skuLabel != null && !skuLabel.isEmpty(); }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
