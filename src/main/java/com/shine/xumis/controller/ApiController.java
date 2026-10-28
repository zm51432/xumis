package com.shine.xumis.controller;

import com.shine.xumis.dto.CustomIdeaRequest;
import com.shine.xumis.dto.InquiryRequest;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.CustomIdea;
import com.shine.xumis.model.Inquiry;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对外 REST API（前端 SPA / 小程序 / 第三方对接可用）
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ProductService productService;
    private final CustomIdeaService ideaService;
    private final InquiryService inquiryService;
    private final FileStorageService fileStorageService;

    public ApiController(ProductService productService, CustomIdeaService ideaService,
                         InquiryService inquiryService, FileStorageService fileStorageService) {
        this.productService = productService;
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(value = "cat", required = false) Category cat) {
        return (cat != null) ? productService.findByCategory(cat) : productService.findAll();
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Product> product(@PathVariable Long id) {
        Product p = productService.findById(id);
        return (p != null) ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    @GetMapping("/categories")
    public Category[] categories() {
        return Category.values();
    }

    @PostMapping("/custom-ideas")
    public ResponseEntity<CustomIdea> postIdea(@Valid @ModelAttribute CustomIdeaRequest form,
                                               @RequestParam(value = "referenceImage", required = false) MultipartFile file) {
        CustomIdea idea = new CustomIdea();
        idea.setCustomerName(form.getCustomerName());
        idea.setEmail(form.getEmail());
        idea.setCountry(form.getCountry());
        idea.setIdeaDescription(form.getIdeaDescription());
        idea.setExpectCategory(form.getExpectCategory());
        try {
            if (file != null && !file.isEmpty()) {
                idea.setReferenceImageUrl(fileStorageService.store(file));
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(ideaService.save(idea));
    }

    @PostMapping("/inquiries")
    public ResponseEntity<Inquiry> postInquiry(@Valid @RequestBody InquiryRequest form) {
        Inquiry inquiry = new Inquiry();
        inquiry.setName(form.getName());
        inquiry.setEmail(form.getEmail());
        inquiry.setSubject(form.getSubject());
        inquiry.setMessage(form.getMessage());
        return ResponseEntity.ok(inquiryService.save(inquiry));
    }
}
