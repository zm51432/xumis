package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 未启用 ERP 时的空实现：只打日志，不产生外部调用。 */
public class NoopErpClient implements ErpClient {
    private static final Logger log = LoggerFactory.getLogger(NoopErpClient.class);

    @Override public void pushProduct(ErpProductDto p) { log.info("[ERP:noop] pushProduct {}", p == null ? "-" : p.id); }
    @Override public void pushCustomIdea(ErpCustomIdeaDto c) { log.info("[ERP:noop] pushCustomIdea"); }
    @Override public void pushInquiry(ErpInquiryDto i) { log.info("[ERP:noop] pushInquiry"); }
    @Override public void pushOrder(ErpOrderDto o) { log.info("[ERP:noop] pushOrder {}", o == null ? "-" : o.orderNo); }
    @Override public void syncInventory() { log.info("[ERP:noop] syncInventory"); }
    @Override public void postRaw(String path, String jsonBody) { log.info("[ERP:noop] postRaw {}", path); }
    @Override public String reconcile() { return "{\"ok\":false,\"error\":\"ERP 同步未启用\"}"; }
}
