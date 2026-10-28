package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认实现：仅打印日志，不做真实对接。用于开发/演示阶段。
 */
public class NoopErpClient implements ErpClient {

    private static final Logger log = LoggerFactory.getLogger(NoopErpClient.class);

    @Override
    public void pushProduct(ErpProductDto product) {
        log.info("[ERP:noop] pushProduct sku={} name={}", product.id, product.name);
    }

    @Override
    public void pushCustomIdea(ErpCustomIdeaDto idea) {
        log.info("[ERP:noop] pushCustomIdea id={} from={}", idea.id, idea.email);
    }

    @Override
    public void pushInquiry(ErpInquiryDto inquiry) {
        log.info("[ERP:noop] pushInquiry id={} from={}", inquiry.id, inquiry.email);
    }

    @Override
    public void pushOrder(ErpOrderDto order) {
        log.info("[ERP:noop] pushOrder no={} total={} {}", order.orderNo, order.totalAmount, order.currency);
    }

    @Override
    public void syncInventory() {
        log.info("[ERP:noop] syncInventory (no-op)");
    }
}
