package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.CustomIdea;

/**
 * 推送给 ERP 的客户定制需求报文（预留）
 */
public class ErpCustomIdeaDto {
    public Long id;
    public String customerName;
    public String email;
    public String country;
    public String ideaDescription;
    public String expectCategory;
    public String referenceImageUrl;
    public String createdAt;

    public static ErpCustomIdeaDto from(CustomIdea c) {
        ErpCustomIdeaDto d = new ErpCustomIdeaDto();
        d.id = c.getId();
        d.customerName = c.getCustomerName();
        d.email = c.getEmail();
        d.country = c.getCountry();
        d.ideaDescription = c.getIdeaDescription();
        d.expectCategory = c.getExpectCategory() != null ? c.getExpectCategory().name() : null;
        d.referenceImageUrl = c.getReferenceImageUrl();
        d.createdAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : null;
        return d;
    }
}
