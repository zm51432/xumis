package com.shine.xumis.dto;

import java.math.BigDecimal;

/**
 * 单个商品的售出汇总（供商品管理页展示）。
 *
 * @param quantity 累计售出件数
 * @param amount   累计售出金额（USD）
 */
public record ProductSales(long quantity, BigDecimal amount) {
}
