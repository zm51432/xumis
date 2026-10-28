package com.shine.xumis.controller;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 后台：仪表盘 + 商品管理（上传图片 / 修改信息与图片链接 / 上下架 / 删除）
 * 注意：仅用 app.admin-key 做演示性保护，生产环境务必接入正式鉴权（Spring Security / OAuth）。
 */
@Controller
public class AdminController {

    private final CustomIdeaService ideaService;
    private final InquiryService inquiryService;
    private final OrderService orderService;
    private final SubscriptionService subscriptionService;
    private final ReviewService reviewService;
    private final ProductService productService;
    private final FileStorageService fileStorageService;

    @Value("${app.admin-key:admin}")
    private String adminKey;

    public AdminController(CustomIdeaService ideaService, InquiryService inquiryService,
                           OrderService orderService, SubscriptionService subscriptionService,
                           ReviewService reviewService, ProductService productService,
                           FileStorageService fileStorageService) {
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
        this.orderService = orderService;
        this.subscriptionService = subscriptionService;
        this.reviewService = reviewService;
        this.productService = productService;
        this.fileStorageService = fileStorageService;
    }

    private boolean authorized(String key) {
        return key != null && key.equals(adminKey);
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/admin")
    public String admin(@RequestParam(value = "key", required = false) String key, Model model) {
        if (!authorized(key)) {
            model.addAttribute("denied", true);
            return "admin";
        }
        model.addAttribute("denied", false);
        model.addAttribute("key", key);
        model.addAttribute("ideas", ideaService.findAll());
        model.addAttribute("inquiries", inquiryService.findAll());
        model.addAttribute("orders", orderService.findAll());
        model.addAttribute("subscriptions", subscriptionService.findAll());
        model.addAttribute("pendingReviews", reviewService.pending());
        // 概览统计
        model.addAttribute("countProducts", productService.findAll().size());
        model.addAttribute("countOrders", orderService.countAll());
        model.addAttribute("countIdeas", ideaService.countAll());
        model.addAttribute("countInquiries", inquiryService.countAll());
        model.addAttribute("countSubs", subscriptionService.countAll());
        model.addAttribute("countPendingReviews", reviewService.pendingCount());
        model.addAttribute("countPaidOrders", orderService.countByStatus("PAID"));
        return "admin";
    }

    @PostMapping("/admin/review/approve")
    public String approveReview(@RequestParam String key,
                                @RequestParam Long id) {
        if (!authorized(key)) {
            return "redirect:/admin?denied=1";
        }
        reviewService.approve(id);
        return "redirect:/admin?key=" + key;
    }

    // ==================== 商品管理 ====================

    /**
     * 商品管理页：列表 + 新增/编辑表单。
     * 带 ?id= 时为编辑模式，表单会回填该商品。
     */
    @GetMapping("/admin/products")
    public String products(@RequestParam(value = "key", required = false) String key,
                           @RequestParam(value = "id", required = false) Long id,
                           @RequestParam(value = "saved", required = false) String saved,
                           @RequestParam(value = "err", required = false) String err,
                           Model model) {
        if (!authorized(key)) {
            model.addAttribute("denied", true);
            return "admin-products";
        }
        model.addAttribute("denied", false);
        model.addAttribute("key", key);
        model.addAttribute("categories", Category.values());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("edit", id != null ? productService.findById(id) : null);
        model.addAttribute("saved", saved);
        model.addAttribute("err", err);
        return "admin-products";
    }

    /**
     * 新增 / 修改商品。支持两种图片方式：
     * 1) 直接上传本地图片（保存到 ./uploads，访问路径 /files/xxx）
     * 2) 填写外部图片链接 URL
     * 两者都为空时保留原图（编辑场景）；新建且无图时使用占位图。
     */
    @PostMapping("/admin/product/save")
    public String saveProduct(@RequestParam String key,
                              @RequestParam(value = "id", required = false) Long id,
                              @RequestParam String name,
                              @RequestParam(required = false) String nameEn,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String price,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String descriptionEn,
                              @RequestParam(required = false) String material,
                              @RequestParam(required = false) String stock,
                              @RequestParam(required = false) String imageUrl,
                              @RequestParam(value = "customizable", defaultValue = "false") boolean customizable,
                              @RequestParam(value = "featured", defaultValue = "false") boolean featured,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        if (!authorized(key)) {
            return "redirect:/admin?denied=1";
        }
        Product p = (id != null) ? productService.findById(id) : null;
        boolean isNew = (p == null);
        if (isNew) {
            p = new Product();
        }

        p.setName(name == null || name.isBlank() ? "未命名商品" : name.trim());
        p.setNameEn(nameEn);
        p.setCategory(parseCategory(category));
        p.setPrice(parsePrice(price));
        p.setDescription(description);
        p.setDescriptionEn(descriptionEn);
        p.setMaterial(material);
        p.setStock(parseStock(stock));
        p.setCustomizable(customizable);
        p.setFeatured(featured);

        // 图片：上传优先，其次外部链接，都为空则保留原图
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                p.setImageUrl(fileStorageService.store(imageFile));
            } catch (IllegalArgumentException ex) {
                return "redirect:/admin/products?key=" + key + "&err=" + urlEncode(ex.getMessage());
            } catch (IOException ex) {
                return "redirect:/admin/products?key=" + key + "&err=" + urlEncode("图片保存失败，请检查上传目录权限");
            }
        } else if (imageUrl != null && !imageUrl.isBlank()) {
            p.setImageUrl(imageUrl.trim());
        } else if (isNew) {
            p.setImageUrl("https://placehold.co/600x600/FFE3EC/FF6F91?text=shine");
        }

        productService.save(p);
        return "redirect:/admin/products?key=" + key + "&saved=" + (isNew ? "created" : "updated");
    }

    @PostMapping("/admin/product/delete")
    public String deleteProduct(@RequestParam String key, @RequestParam Long id) {
        if (!authorized(key)) {
            return "redirect:/admin?denied=1";
        }
        productService.delete(id);
        return "redirect:/admin/products?key=" + key + "&saved=deleted";
    }

    /** 快捷开关：首页精选 / 支持定制 */
    @PostMapping("/admin/product/toggle")
    public String toggle(@RequestParam String key,
                         @RequestParam Long id,
                         @RequestParam String field) {
        if (!authorized(key)) {
            return "redirect:/admin?denied=1";
        }
        Product p = productService.findById(id);
        if (p != null) {
            if ("featured".equals(field)) {
                p.setFeatured(!p.isFeatured());
            } else if ("customizable".equals(field)) {
                p.setCustomizable(!p.isCustomizable());
            }
            productService.save(p);
        }
        return "redirect:/admin/products?key=" + key + "&saved=updated";
    }

    // ==================== 工具方法 ====================

    private Category parseCategory(String v) {
        if (v != null) {
            for (Category c : Category.values()) {
                if (c.name().equalsIgnoreCase(v)) {
                    return c;
                }
            }
        }
        return Category.OTHER;
    }

    private BigDecimal parsePrice(String v) {
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseStock(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
