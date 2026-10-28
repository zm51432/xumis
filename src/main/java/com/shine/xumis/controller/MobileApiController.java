package com.shine.xumis.controller;

import com.shine.xumis.dto.CustomIdeaRequest;
import com.shine.xumis.dto.InquiryRequest;
import com.shine.xumis.dto.MobileProductDto;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.CustomIdea;
import com.shine.xumis.model.Inquiry;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 手机端 API（预留 App / 小程序接入）。
 * 路径前缀 /api/m/**，支持分页；若配置了 app.mobile-api-key，则必须通过请求头 X-API-KEY 校验。
 */
@RestController
@RequestMapping("/api/m")
public class MobileApiController {

    private final ProductService productService;
    private final CustomIdeaService ideaService;
    private final InquiryService inquiryService;
    private final FileStorageService fileStorageService;
    private final ErpSyncService erpSyncService;

    @Value("${app.mobile-api-key:}")
    private String mobileApiKey;

    public MobileApiController(ProductService productService, CustomIdeaService ideaService,
                               InquiryService inquiryService, FileStorageService fileStorageService,
                               ErpSyncService erpSyncService) {
        this.productService = productService;
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
        this.fileStorageService = fileStorageService;
        this.erpSyncService = erpSyncService;
    }

    /** 校验手机端密钥：未配置则放行（开发期） */
    private boolean authorized(String key) {
        if (mobileApiKey == null || mobileApiKey.isBlank()) return true;
        return mobileApiKey.equals(key);
    }

    private boolean isEnglish(String acceptLang) {
        return acceptLang != null && acceptLang.toLowerCase().contains("en");
    }

    @GetMapping("/products")
    public ResponseEntity<?> products(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                     @RequestHeader(value = "Accept-Language", required = false) String acceptLang,
                                     @RequestParam(value = "cat", required = false) Category cat,
                                     @RequestParam(value = "page", defaultValue = "0") int page,
                                     @RequestParam(value = "size", defaultValue = "12") int size) {
        if (!authorized(key)) return unauthorized();
        boolean en = isEnglish(acceptLang);
        Page<Product> result = (cat != null)
                ? productService.findPageByCategory(cat, org.springframework.data.domain.PageRequest.of(page, size))
                : productService.findPage(org.springframework.data.domain.PageRequest.of(page, size));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent().stream().map(p -> MobileProductDto.from(p, en)).collect(Collectors.toList()));
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> product(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                    @RequestHeader(value = "Accept-Language", required = false) String acceptLang,
                                    @PathVariable Long id) {
        if (!authorized(key)) return unauthorized();
        Product p = productService.findById(id);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(MobileProductDto.from(p, isEnglish(acceptLang)));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(@RequestHeader(value = "X-API-KEY", required = false) String key) {
        if (!authorized(key)) return unauthorized();
        return ResponseEntity.ok(Arrays.stream(Category.values())
                .map(c -> Map.of("code", c.name(), "label", c.getLabel())).collect(Collectors.toList()));
    }

    /** 手机端搜索 / 分类 / 排序 / 分页 */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                    @RequestHeader(value = "Accept-Language", required = false) String acceptLang,
                                    @RequestParam(value = "q", required = false) String q,
                                    @RequestParam(value = "cat", required = false) Category cat,
                                    @RequestParam(value = "sort", defaultValue = "default") String sort,
                                    @RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "12") int size) {
        if (!authorized(key)) return unauthorized();
        var result = productService.search(q, cat, sort, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getItems().stream()
                .map(p -> MobileProductDto.from(p, isEnglish(acceptLang))).collect(Collectors.toList()));
        body.put("page", result.getPage());
        body.put("size", size);
        body.put("totalElements", result.getTotalItems());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/custom-ideas")
    public ResponseEntity<?> postIdea(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                     @Valid @ModelAttribute CustomIdeaRequest form,
                                     @RequestParam(value = "referenceImage", required = false) MultipartFile file) {
        if (!authorized(key)) return unauthorized();
        try {
            CustomIdea idea = new CustomIdea();
            idea.setCustomerName(form.getCustomerName());
            idea.setEmail(form.getEmail());
            idea.setCountry(form.getCountry());
            idea.setIdeaDescription(form.getIdeaDescription());
            idea.setExpectCategory(form.getExpectCategory());
            if (file != null && !file.isEmpty()) idea.setReferenceImageUrl(fileStorageService.store(file));
            CustomIdea saved = ideaService.save(idea);
            erpSyncService.syncCustomIdea(saved);
            return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/inquiries")
    public ResponseEntity<?> postInquiry(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                        @Valid @RequestBody InquiryRequest form) {
        if (!authorized(key)) return unauthorized();
        Inquiry inquiry = new Inquiry();
        inquiry.setName(form.getName());
        inquiry.setEmail(form.getEmail());
        inquiry.setSubject(form.getSubject());
        inquiry.setMessage(form.getMessage());
        Inquiry saved = inquiryService.save(inquiry);
        erpSyncService.syncInquiry(saved);
        return ResponseEntity.ok(Map.of("ok", true, "id", saved.getId()));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid or missing X-API-KEY"));
    }
}
