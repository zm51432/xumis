package com.shine.xumis.controller;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.Review;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.RateLimitService;
import com.shine.xumis.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品评价提交：提交后进入 PENDING，待后台审核后展示。
 */
@Controller
public class ReviewController {

    private final ProductService productService;
    private final ReviewService reviewService;
    private final RateLimitService rateLimitService;

    public ReviewController(ProductService productService, ReviewService reviewService,
                            RateLimitService rateLimitService) {
        this.productService = productService;
        this.reviewService = reviewService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/product/{id}/review")
    public String add(@PathVariable Long id,
                      @RequestParam(required = false) String author,
                      @RequestParam(defaultValue = "5") int rating,
                      @RequestParam(required = false) String comment,
                      jakarta.servlet.http.HttpSession session,
                      HttpServletRequest request) {
        Product p = productService.findById(id);
        if (p == null) {
            return "redirect:/products";
        }
        // 只有登录用户能评价：需要邮箱去匹配订单，判断是不是真的买过
        var user = AccountController.currentUser(session);
        if (user == null) {
            return "redirect:/product/" + id + "?reviewNeedLogin=1";
        }
        // 防刷屏：同一 IP 10 分钟内最多 5 条评价
        String key = "review:" + clientIp(request);
        if (!rateLimitService.allow(key, 5, 10 * 60 * 1000L)) {
            return "redirect:/product/" + id + "?reviewRate=" + rateLimitService.retryAfterSeconds(key, 5, 10 * 60 * 1000L);
        }
        String name = (author == null || author.isBlank())
                ? ((user.getName() != null && !user.getName().isBlank()) ? user.getName() : null)
                : author;

        ReviewService.SubmitResult res = reviewService.submit(p, user.getEmail(), name, rating, comment);
        return switch (res) {
            case OK -> "redirect:/product/" + id + "?review=1";
            case NOT_PURCHASED -> "redirect:/product/" + id + "?reviewNeedBuy=1";
            case DUPLICATE -> "redirect:/product/" + id + "?reviewDup=1";
            case NO_PRODUCT -> "redirect:/products";
        };
    }

    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }
}
