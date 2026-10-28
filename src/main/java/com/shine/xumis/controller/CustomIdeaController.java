package com.shine.xumis.controller;

import com.shine.xumis.dto.CustomIdeaRequest;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.CustomIdea;
import com.shine.xumis.service.CustomIdeaService;
import com.shine.xumis.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户自定义想法上传（来图定制）
 */
@Controller
public class CustomIdeaController {

    private final CustomIdeaService ideaService;
    private final FileStorageService fileStorageService;
    private final ErpSyncService erpSyncService;

    public CustomIdeaController(CustomIdeaService ideaService, FileStorageService fileStorageService, ErpSyncService erpSyncService) {
        this.ideaService = ideaService;
        this.fileStorageService = fileStorageService;
        this.erpSyncService = erpSyncService;
    }

    @GetMapping("/custom")
    public String customPage(Model model) {
        model.addAttribute("form", new CustomIdeaRequest());
        return "custom";
    }

    @PostMapping("/custom")
    public String submit(@Valid @ModelAttribute("form") CustomIdeaRequest form,
                         BindingResult result,
                         @RequestParam(value = "referenceImage", required = false) MultipartFile file,
                         Model model) {
        if (result.hasErrors()) {
            return "custom";
        }
        try {
            CustomIdea idea = new CustomIdea();
            idea.setCustomerName(form.getCustomerName());
            idea.setEmail(form.getEmail());
            idea.setCountry(form.getCountry());
            idea.setIdeaDescription(form.getIdeaDescription());
            idea.setExpectCategory(form.getExpectCategory());
            if (file != null && !file.isEmpty()) {
                idea.setReferenceImageUrl(fileStorageService.store(file));
            }
            ideaService.save(idea);
            erpSyncService.syncCustomIdea(idea);
            return "redirect:/custom?success=1";
        } catch (Exception e) {
            model.addAttribute("error", "提交失败：" + e.getMessage());
            return "custom";
        }
    }
}
