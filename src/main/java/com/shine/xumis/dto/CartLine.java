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

    public CartLine(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.lineTotal = product.getPrice() == null
                ? BigDecimal.ZERO
                : product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
