package com.shine.xumis.controller;

import com.shine.xumis.dto.InquiryRequest;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Inquiry;
import com.shine.xumis.service.InquiryService;
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

    public InquiryController(InquiryService inquiryService, ErpSyncService erpSyncService) {
        this.inquiryService = inquiryService;
        this.erpSyncService = erpSyncService;
    }

    @GetMapping("/inquiry")
    public String inquiryPage(Model model) {
        model.addAttribute("form", new InquiryRequest());
        return "inquiry";
    }

    @PostMapping("/inquiry")
    public String submit(@Valid @ModelAttribute("form") InquiryRequest form,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
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
