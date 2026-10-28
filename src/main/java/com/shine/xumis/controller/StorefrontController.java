package com.shine.xumis.controller;

import com.shine.xumis.dto.ProductSearchResult;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 店铺前台页面（Thymeleaf 视图）
 */
@Controller
public class StorefrontController {

    private final ProductService productService;
    private final ReviewService reviewService;

    public StorefrontController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
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
                           Model model) {
        int size = 12;
        ProductSearchResult result = productService.search(q, cat, sort, page, size);
        model.addAttribute("products", result.getItems());
        model.addAttribute("activeCat", cat);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort);
        model.addAttribute("page", result.getPage());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalItems", result.getTotalItems());
        return "products";
    }

    @GetMapping("/product/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product p = productService.findById(id);
        if (p == null) {
            return "redirect:/products";
        }
        model.addAttribute("product", p);
        model.addAttribute("reviews", reviewService.approvedFor(p));
        model.addAttribute("avgRating", reviewService.avgRating(p));
        model.addAttribute("reviewCount", reviewService.approvedFor(p).size());
        return "product-detail";
    }

    @GetMapping("/order-success")
    public String orderSuccess(@RequestParam(value = "no", required = false) String orderNo, Model model) {
        model.addAttribute("orderNo", orderNo);
        return "order-success";
    }
}
