package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * HTTP 实现：将业务数据以 JSON POST 到外部 ERP 接口（预留对接骨架）。
 * 具体报文端点、鉴权头按需对接目标 ERP 调整。
 * 注意：任何网络异常都被吞掉并记录日志，确保不影响主业务流程。
 */
public class HttpErpClient implements ErpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpErpClient.class);

    private final ErpProperties props;
    private final RestTemplate restTemplate;

    public HttpErpClient(ErpProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
        // 简单超时设置（生产可换用 HttpComponentsClientHttpRequestFactory）
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(props.getTimeoutMs());
        rf.setReadTimeout(props.getTimeoutMs());
        this.restTemplate.setRequestFactory(rf);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            h.set("X-ERP-API-KEY", props.getApiKey());
        }
        return h;
    }

    private void post(String path, Object body) {
        try {
            String url = props.getBaseUrl().replaceAll("/$", "") + path;
            HttpEntity<Object> entity = new HttpEntity<>(body, headers());
            restTemplate.postForEntity(url, entity, String.class);
            log.info("[ERP:http] POST {} OK", url);
        } catch (Exception e) {
            log.warn("[ERP:http] POST {} 失败: {}", path, e.getMessage());
        }
    }

    @Override
    public void pushProduct(ErpProductDto product) {
        post("/products", product);
    }

    @Override
    public void pushCustomIdea(ErpCustomIdeaDto idea) {
        post("/custom-ideas", idea);
    }

    @Override
    public void pushInquiry(ErpInquiryDto inquiry) {
        post("/inquiries", inquiry);
    }

    @Override
    public void syncInventory() {
        post("/inventory/sync", Collections.emptyMap());
    }
}
