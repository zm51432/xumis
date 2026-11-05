package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.ErpCustomIdeaDto;
import com.shine.xumis.integration.erp.dto.ErpInquiryDto;
import com.shine.xumis.integration.erp.dto.ErpOrderDto;
import com.shine.xumis.integration.erp.dto.ErpProductDto;

/**
 * ERP 同步客户端抽象。postRaw 会抛出失败异常，由 ErpSyncService 统一落库并重试。
 */
public interface ErpClient {
    void pushProduct(ErpProductDto product);
    void pushCustomIdea(ErpCustomIdeaDto idea);
    void pushInquiry(ErpInquiryDto inquiry);
    void pushOrder(ErpOrderDto order);
    void syncInventory();

    /** 原始 JSON 推送：失败必须抛异常（不能静默吞掉） */
    void postRaw(String path, String jsonBody);

    /** 拉取两库差异（ERP 侧计算），返回 JSON 字符串 */
    String reconcile();
}
