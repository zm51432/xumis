package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.*;
import com.shine.xumis.model.CustomIdea;
import com.shine.xumis.model.Inquiry;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ERP 同步编排服务：业务侧只需调用本服务，由它决定是否真正同步。
 * 受 app.erp.enabled 总开关保护；任何异常都被吞掉，绝不阻塞主流程。
 */
@Service
public class ErpSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpSyncService.class);

    private final ErpProperties props;
    private final ErpClient client;

    public ErpSyncService(ErpProperties props, ErpClient client) {
        this.props = props;
        this.client = client;
    }

    public void syncProduct(Product p) {
        if (!props.isEnabled() || p == null) return;
        try { client.pushProduct(ErpProductDto.from(p)); }
        catch (Exception e) { log.warn("[ERP] syncProduct 失败: {}", e.getMessage()); }
    }

    public void syncCustomIdea(CustomIdea c) {
        if (!props.isEnabled() || c == null) return;
        try { client.pushCustomIdea(ErpCustomIdeaDto.from(c)); }
        catch (Exception e) { log.warn("[ERP] syncCustomIdea 失败: {}", e.getMessage()); }
    }

    public void syncInquiry(Inquiry i) {
        if (!props.isEnabled() || i == null) return;
        try { client.pushInquiry(ErpInquiryDto.from(i)); }
        catch (Exception e) { log.warn("[ERP] syncInquiry 失败: {}", e.getMessage()); }
    }

    public void syncAllProducts(List<Product> products) {
        if (!props.isEnabled()) return;
        log.info("[ERP] 全量同步商品，共 {} 条", products.size());
        products.forEach(this::syncProduct);
    }

    public void syncOrder(Order o) {
        if (!props.isEnabled() || o == null) return;
        try { client.pushOrder(ErpOrderDto.from(o)); }
        catch (Exception e) { log.warn("[ERP] syncOrder 失败: {}", e.getMessage()); }
    }
}
