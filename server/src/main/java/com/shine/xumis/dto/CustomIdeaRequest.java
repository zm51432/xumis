package com.shine.xumis.dto;

import com.shine.xumis.model.Category;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 客户自定义想法提交表单
 */
public class CustomIdeaRequest {

    @NotBlank(message = "请填写您的称呼")
    private String customerName;

    @NotBlank(message = "请填写联系邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    private String country;

    @NotBlank(message = "请描述您的想法")
    private String ideaDescription;

    private Category expectCategory;

    // getter / setter
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getIdeaDescription() { return ideaDescription; }
    public void setIdeaDescription(String ideaDescription) { this.ideaDescription = ideaDescription; }
    public Category getExpectCategory() { return expectCategory; }
    public void setExpectCategory(Category expectCategory) { this.expectCategory = expectCategory; }
}
