package com.shine.xumis.controller;

import com.shine.xumis.service.CustomIdeaService;
import com.shine.xumis.service.InquiryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 简易后台：查看客户自定义想法与询盘。
 * 注意：模板仅用 app.admin-key 做演示性保护，生产环境务必接入正式鉴权（Spring Security / OAuth）。
 */
@Controller
public class AdminController {

    private final CustomIdeaService ideaService;
    private final InquiryService inquiryService;

    @Value("${app.admin-key:admin}")
    private String adminKey;

    public AdminController(CustomIdeaService ideaService, InquiryService inquiryService) {
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
    }

    @GetMapping("/admin")
    public String admin(@RequestParam(value = "key", required = false) String key, Model model) {
        if (key == null || !key.equals(adminKey)) {
            model.addAttribute("denied", true);
            return "admin";
        }
        model.addAttribute("denied", false);
        model.addAttribute("ideas", ideaService.findAll());
        model.addAttribute("inquiries", inquiryService.findAll());
        return "admin";
    }
}
