package com.shine.xumis.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 询盘/联系表单
 */
public class InquiryRequest {

    @NotBlank(message = "请填写您的姓名")
    private String name;

    @NotBlank(message = "请填写联系邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    private String subject;

    @NotBlank(message = "请填写留言内容")
    private String message;

    // getter / setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
