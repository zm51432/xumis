package com.shine.xumis.integration.erp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据 app.erp.type 选择 ERP 客户端实现：
 *   noop（默认）→ NoopErpClient，仅打日志
 *   http        → HttpErpClient，调用外部接口
 * 实际是否同步由 ErpProperties.enabled 总开关控制。
 */
@Configuration
public class ErpConfig {

    private final ErpProperties props;

    public ErpConfig(ErpProperties props) {
        this.props = props;
    }

    @Bean
    public ErpClient erpClient() {
        if ("http".equalsIgnoreCase(props.getType())) {
            return new HttpErpClient(props);
        }
        return new NoopErpClient();
    }
}
