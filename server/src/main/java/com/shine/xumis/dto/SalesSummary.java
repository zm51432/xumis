package com.shine.xumis.dto;

import java.math.BigDecimal;

/**
 * 后台销售汇总（金额均为基准币种 USD）。
 *
 * @param totalSales     累计销售额（不含已取消订单）
 * @param paidSales      已确认收入：已付款 / 已发货 / 已完成
 * @param pendingSales   待处理 / 未确认部分金额
 * @param orderCount     计入销售额的订单数
 * @param itemCount      累计售出商品件数
 * @param avgOrderValue  客单价（无订单时为 0）
 */
public record SalesSummary(BigDecimal totalSales,
                           BigDecimal paidSales,
                           BigDecimal pendingSales,
                           long orderCount,
                           long itemCount,
                           BigDecimal avgOrderValue) {
}
