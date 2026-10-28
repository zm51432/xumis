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
                      @RequestParam String author,
                      @RequestParam int rating,
                      @RequestParam String comment,
                      HttpServletRequest request) {
        Product p = productService.findById(id);
        if (p == null) {
            return "redirect:/products";
        }
        // 防刷屏：同一 IP 10 分钟内最多 5 条评价
        String key = "review:" + clientIp(request);
        if (!rateLimitService.allow(key, 5, 10 * 60 * 1000L)) {
            return "redirect:/product/" + id + "?reviewRate=" + rateLimitService.retryAfterSeconds(key, 5, 10 * 60 * 1000L);
        }
        // 评分越界兜底（1~5）与长度收敛，避免恶意值污染平均分/撑爆页面
        int safeRating = Math.max(1, Math.min(5, rating));
        String safeAuthor = author == null || author.isBlank() ? "匿名" : author.trim();
        if (safeAuthor.length() > 60) {
            safeAuthor = safeAuthor.substring(0, 60);
        }
        String safeComment = (comment == null) ? "" : comment.trim();
        if (safeComment.length() > 1000) {
            safeComment = safeComment.substring(0, 1000);
        }
        Review r = new Review();
        r.setProduct(p);
        r.setAuthor(safeAuthor);
        r.setRating(safeRating);
        r.setComment(safeComment);
        r.setStatus("PENDING");
        reviewService.save(r);
        return "redirect:/product/" + id + "?review=1";
    }

    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }
}
