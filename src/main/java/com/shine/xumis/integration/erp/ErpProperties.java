package com.shine.xumis.integration.erp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ERP 对接配置（预留）。
 * 通过 application.yml 的 app.erp.* 注入：
 *   app.erp.enabled  是否启用同步（默认 false）
 *   app.erp.type     noop(默认, 仅打日志) / http(调用外部接口)
 *   app.erp.base-url 外部 ERP 接口地址
 *   app.erp.api-key  接口鉴权密钥
 */
@Component
@ConfigurationProperties(prefix = "app.erp")
public class ErpProperties {

    private boolean enabled = false;
    private String type = "noop";      // noop | http
    private String baseUrl = "";
    private String apiKey = "";
    private int timeoutMs = 5000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}
