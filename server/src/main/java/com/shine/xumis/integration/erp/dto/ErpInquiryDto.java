package com.shine.xumis.integration.erp.dto;

import com.shine.xumis.model.Inquiry;

/**
 * 推送给 ERP 的询盘报文（预留）
 */
public class ErpInquiryDto {
    public Long id;
    public String name;
    public String email;
    public String subject;
    public String message;
    public String createdAt;

    public static ErpInquiryDto from(Inquiry i) {
        ErpInquiryDto d = new ErpInquiryDto();
        d.id = i.getId();
        d.name = i.getName();
        d.email = i.getEmail();
        d.subject = i.getSubject();
        d.message = i.getMessage();
        d.createdAt = i.getCreatedAt() != null ? i.getCreatedAt().toString() : null;
        return d;
    }
}
