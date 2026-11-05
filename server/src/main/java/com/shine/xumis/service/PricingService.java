package com.shine.xumis.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 订单金额口径（阶段1）：应付总额 = 商品小计 − 优惠 + 运费 + 税费。
 *
 * <p>站点设置中的运费/门槛/税率一律以 USD 基准存储（与商品价格一致），
 * 页面按 fxRate 换算展示；订单落库仍存 USD 基准值，避免多币种重复计价。</p>
 */
@Service
public class PricingService {

    public record Quote(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee,
                        BigDecimal taxAmount, BigDecimal total, BigDecimal taxRate,
                        BigDecimal freeShipThreshold, boolean freeShipping) {}

    private final SettingsService settings;

    public PricingService(SettingsService settings) { this.settings = settings; }

    public Quote quote(BigDecimal subtotal, BigDecimal discount, boolean couponFreeShip) {
        BigDecimal sub = nz(subtotal);
        BigDecimal disc = nz(discount);
        if (disc.signum() < 0) disc = BigDecimal.ZERO;
        if (disc.compareTo(sub) > 0) disc = sub;
        BigDecimal base = sub.subtract(disc).max(BigDecimal.ZERO);

        BigDecimal feeBase = decimal(SettingsService.SHOP_SHIP_FEE, "12");
        BigDecimal threshold = decimal(SettingsService.SHOP_FREE_SHIP, "99");
        if (feeBase.signum() < 0) feeBase = BigDecimal.ZERO;
        boolean free = couponFreeShip || (threshold.signum() > 0 && base.compareTo(threshold) >= 0);
        BigDecimal shipping = free ? BigDecimal.ZERO : feeBase;

        BigDecimal rate = decimal(SettingsService.SHOP_TAX_RATE, "0");
        if (rate.signum() < 0) rate = BigDecimal.ZERO;
        BigDecimal tax = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = base.add(shipping).add(tax).setScale(2, RoundingMode.HALF_UP);
        return new Quote(sub, disc, shipping.setScale(2, RoundingMode.HALF_UP), tax,
                         total, rate, threshold, free);
    }

    private BigDecimal decimal(String key, String fallback) {
        String v = settings.getOr(key, fallback);
        try { return new BigDecimal(v == null || v.isBlank() ? fallback : v.trim()); }
        catch (Exception e) { return new BigDecimal(fallback); }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
