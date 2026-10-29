package com.shine.xumis.service;

import com.shine.xumis.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 多币种服务：解析前台选中的币种，并提供汇率换算。
 * 基准币种为 USD（商品 price 字段以 USD 存储）。
 */
@Service
public class CurrencyService {

    private final AppProperties props;

    public CurrencyService(AppProperties props) {
        this.props = props;
    }

    public List<AppProperties.Currency> enabled() {
        return props.getCurrencies().stream()
                .filter(AppProperties.Currency::isEnabled)
                .toList();
    }

    public AppProperties.Currency defaultCurrency() {
        return props.getCurrencies().stream()
                .filter(AppProperties.Currency::isDefaultCurrency)
                .findFirst()
                .orElseGet(() -> enabled().isEmpty() ? fallback() : enabled().get(0));
    }

    public AppProperties.Currency resolve(String code) {
        if (code == null || code.isBlank()) {
            return defaultCurrency();
        }
        return props.getCurrencies().stream()
                .filter(c -> c.getCode().equalsIgnoreCase(code) && c.isEnabled())
                .findFirst()
                .orElse(defaultCurrency());
    }

    private AppProperties.Currency fallback() {
        AppProperties.Currency c = new AppProperties.Currency();
        c.setCode("USD");
        c.setSymbol("$");
        c.setRate(java.math.BigDecimal.ONE);
        return c;
    }
}
