package com.shine.xumis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用级配置属性（绑定 application.yml 中 app.*）。
 * 目前承载多币种配置，便于跨境独立站展示美元/人民币/欧元等。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 支持展示的币种列表（相对基准币种 USD 的汇率） */
    private List<Currency> currencies = new ArrayList<>();

    public List<Currency> getCurrencies() {
        return currencies;
    }

    public void setCurrencies(List<Currency> currencies) {
        this.currencies = currencies;
    }

    public static class Currency {
        /** 币种代码，如 USD / CNY / EUR */
        private String code;
        /** 展示符号，如 $ / ¥ / € */
        private String symbol = "$";
        /** 相对基准币种(USD)的汇率，1 USD = rate * 本币种 */
        private BigDecimal rate = BigDecimal.ONE;
        /** 是否在前台可用 */
        private boolean enabled = true;
        /** 是否为默认币种 */
        private boolean defaultCurrency = false;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public BigDecimal getRate() { return rate; }
        public void setRate(BigDecimal rate) { this.rate = rate; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isDefaultCurrency() { return defaultCurrency; }
        public void setDefaultCurrency(boolean defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    }
}
