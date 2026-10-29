package com.shine.xumis.controller;

import com.shine.xumis.dto.ProductSearchResult;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.RestockRequest;
import com.shine.xumis.repository.RestockRepository;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 店铺前台页面（Thymeleaf 视图）
 */
@Controller
public class StorefrontController {

    private final ProductService productService;
    private final ReviewService reviewService;
    private final RestockRepository restockRepository;
    private final OrderService orderService;

    public StorefrontController(ProductService productService, ReviewService reviewService,
                               RestockRepository restockRepository, OrderService orderService) {
        this.productService = productService;
        this.reviewService = reviewService;
        this.restockRepository = restockRepository;
        this.orderService = orderService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featured", productService.findFeatured());
        model.addAttribute("customizable", productService.findCustomizable());
        return "home";
    }

    @GetMapping("/products")
    public String products(@RequestParam(value = "cat", required = false) Category cat,
                           @RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "sort", defaultValue = "default") String sort,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "priceMin", required = false) String priceMinStr,
                           @RequestParam(value = "priceMax", required = false) String priceMaxStr,
                           Model model) {
        int size = 12;
        java.math.BigDecimal priceMin = parsePrice(priceMinStr);
        java.math.BigDecimal priceMax = parsePrice(priceMaxStr);
        ProductSearchResult result = productService.search(q, cat, sort, page, size, priceMin, priceMax);
        model.addAttribute("products", result.getItems());
        model.addAttribute("activeCat", cat);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort);
        model.addAttribute("pmin", priceMinStr == null ? "" : priceMinStr);
        model.addAttribute("pmax", priceMaxStr == null ? "" : priceMaxStr);
        model.addAttribute("page", result.getPage());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalItems", result.getTotalItems());
        return "products";
    }

    private java.math.BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(s.trim());
            return v.compareTo(java.math.BigDecimal.ZERO) < 0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GetMapping("/product/{id}")
    public String detail(@PathVariable Long id, Model model,
                         jakarta.servlet.http.HttpSession session,
                         jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Product p = productService.findById(id);
        if (p == null) {
            // 商品不存在：返回真正的 404（而不是 200 空白页，利于 SEO 与监控）
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        model.addAttribute("product", p);
        model.addAttribute("reviews", reviewService.approvedFor(p));
        model.addAttribute("avgRating", reviewService.avgRating(p));
        model.addAttribute("reviewCount", reviewService.approvedFor(p).size());

        // 评价权限：登录 + 真买过 + 还没评价过（后台审核通过后仍不可重复提交）
        var user = AccountController.currentUser(session);
        boolean purchased = false;
        boolean reviewed = false;
        if (user != null) {
            purchased = reviewService.findPurchase(user.getEmail(), p.getId()).isPresent();
            reviewed = reviewService.myReview(user.getEmail(), p.getId()).isPresent();
        }
        model.addAttribute("loggedIn", user != null);
        model.addAttribute("purchased", purchased);
        model.addAttribute("reviewed", reviewed);
        model.addAttribute("canReview", user != null && purchased && !reviewed);

        model.addAttribute("related", productService.findRelated(p, 4));
        return "product-detail";
    }

    @GetMapping("/order-success")
    public String orderSuccess(@RequestParam(value = "no", required = false) String orderNo, Model model) {
        model.addAttribute("orderNo", orderNo);
        if (orderNo != null && !orderNo.isBlank()) {
            model.addAttribute("order", orderService.findByOrderNo(orderNo));
        }
        return "order-success";
    }

    /**
     * 到货通知登记：缺货商品下方留邮箱。含蜜罐字段 company 防机器人；
     * 邮箱格式校验失败则静默忽略，避免被滥用。
     */
    @PostMapping("/product/{id}/restock")
    public String restock(@PathVariable Long id,
                          @RequestParam String email,
                          @RequestParam(required = false) String company,
                          HttpServletRequest request) {
        // 蜜罐：机器人通常会填隐藏字段，直接放行（当成功处理）但不落库
        if (company != null && !company.isBlank()) {
            return "redirect:/product/" + id + "?restock=1";
        }
        Product p = productService.findById(id);
        if (p != null && email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            RestockRequest r = new RestockRequest();
            r.setProductId(id);
            r.setEmail(email.trim().toLowerCase());
            restockRepository.save(r);
        }
        return "redirect:/product/" + id + "?restock=1";
    }
}
