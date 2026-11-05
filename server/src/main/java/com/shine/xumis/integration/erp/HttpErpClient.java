package com.shine.xumis.integration.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shine.xumis.integration.erp.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/** HTTP 实现：检查 HTTP 状态与 ERP 返回的 ok 字段，失败抛异常供上层记录/重试。 */
public class HttpErpClient implements ErpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpErpClient.class);
    private final ErpProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpErpClient(ErpProperties props) {
        this.props = props;
        this.restTemplate = new RestTemplate();
        var rf = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(props.getTimeoutMs());
        rf.setReadTimeout(props.getTimeoutMs());
        this.restTemplate.setRequestFactory(rf);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) h.set("X-ERP-API-KEY", props.getApiKey());
        return h;
    }

    private String url(String path) { return props.getBaseUrl().replaceAll("/$", "") + path; }

    @Override
    public void postRaw(String path, String jsonBody) {
        String endpoint = url(path);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    endpoint, new HttpEntity<>(jsonBody, headers()), String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("HTTP " + resp.getStatusCode().value());
            }
            String body = resp.getBody();
            if (body != null && body.contains("\"ok\":false")) {
                throw new IllegalStateException("ERP 拒绝：" + body.substring(0, Math.min(160, body.length())));
            }
            log.info("[ERP:http] POST {} OK", endpoint);
        } catch (Exception e) {
            log.warn("[ERP:http] POST {} 失败: {}", endpoint, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void postDto(String path, Object dto) {
        try { postRaw(path, mapper.writeValueAsString(dto)); }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
    }

    @Override public void pushProduct(ErpProductDto p) { postDto("/products", p); }
    @Override public void pushCustomIdea(ErpCustomIdeaDto c) { postDto("/custom-ideas", c); }
    @Override public void pushInquiry(ErpInquiryDto i) { postDto("/inquiries", i); }
    @Override public void pushOrder(ErpOrderDto o) { postDto("/orders", o); }
    @Override public void syncInventory() { postRaw("/inventory/sync", "{}"); }

    @Override
    public String reconcile() {
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url("/reconcile"), new HttpEntity<>("{}", headers()), String.class);
            return resp.getBody() == null ? "{\"ok\":false,\"error\":\"empty\"}" : resp.getBody();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
