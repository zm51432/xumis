package com.shine.xumis.integration.erp;

import com.shine.xumis.integration.erp.dto.ErpCustomIdeaDto;
import com.shine.xumis.integration.erp.dto.ErpInquiryDto;
import com.shine.xumis.integration.erp.dto.ErpOrderDto;
import com.shine.xumis.integration.erp.dto.ErpProductDto;

/**
 * ERP 同步客户端抽象接口（预留对接）。
 * 后续接入具体 ERP（如用友/金蝶/SAP/自研中台）时，只需新增一个实现类并在配置中切换 type。
 */
public interface ErpClient {

    /** 同步商品（上架/更新） */
    void pushProduct(ErpProductDto product);

    /** 同步客户自定义想法（定制需求） */
    void pushCustomIdea(ErpCustomIdeaDto idea);

    /** 同步询盘留言 */
    void pushInquiry(ErpInquiryDto inquiry);

    /** 同步订单（结算单） */
    void pushOrder(ErpOrderDto order);

    /** 拉取库存（预留，通常由 ERP 主动推送或定时任务触发） */
    void syncInventory();
}
