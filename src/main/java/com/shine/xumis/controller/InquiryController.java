package com.shine.xumis.controller;

import com.shine.xumis.dto.InquiryRequest;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Inquiry;
import com.shine.xumis.service.InquiryService;
import com.shine.xumis.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 询盘 / 联系
 */
@Controller
public class InquiryController {

    private final InquiryService inquiryService;
    private final ErpSyncService erpSyncService;
    private final RateLimitService rateLimitService;

    public InquiryController(InquiryService inquiryService, ErpSyncService erpSyncService,
                             RateLimitService rateLimitService) {
        this.inquiryService = inquiryService;
        this.erpSyncService = erpSyncService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/inquiry")
    public String inquiryPage(Model model) {
        model.addAttribute("form", new InquiryRequest());
        return "inquiry";
    }

    @PostMapping("/inquiry")
    public String submit(@Valid @ModelAttribute("form") InquiryRequest form,
                         BindingResult result, Model model, HttpServletRequest request) {
        if (result.hasErrors()) {
            return "inquiry";
        }
        // 防刷：同一 IP 10 分钟内最多 5 条询盘
        String key = "inquiry:" + ReviewController.clientIp(request);
        if (!rateLimitService.allow(key, 5, 10 * 60 * 1000L)) {
            model.addAttribute("rateLimited", rateLimitService.retryAfterSeconds(key, 5, 10 * 60 * 1000L));
            return "inquiry";
        }
        Inquiry inquiry = new Inquiry();
        inquiry.setName(form.getName());
        inquiry.setEmail(form.getEmail());
        inquiry.setSubject(form.getSubject());
        inquiry.setMessage(form.getMessage());
        inquiryService.save(inquiry);
        erpSyncService.syncInquiry(inquiry);
        return "redirect:/inquiry?success=1";
    }
}
