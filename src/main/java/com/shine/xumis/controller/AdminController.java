package com.shine.xumis.controller;

import com.shine.xumis.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 后台仪表盘：查看订单、定制想法、询盘、订阅与待审评价，并对评价进行审核。
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

    @Value("${app.admin-key:admin}")
    private String adminKey;

    public AdminController(CustomIdeaService ideaService, InquiryService inquiryService,
                           OrderService orderService, SubscriptionService subscriptionService,
                           ReviewService reviewService, ProductService productService) {
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
        this.orderService = orderService;
        this.subscriptionService = subscriptionService;
        this.reviewService = reviewService;
        this.productService = productService;
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
        if (key == null || !key.equals(adminKey)) {
            return "redirect:/admin?denied=1";
        }
        reviewService.approve(id);
        return "redirect:/admin?key=" + key;
    }
}
