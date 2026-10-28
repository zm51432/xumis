package com.shine.xumis.controller;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.Review;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.ReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    public ReviewController(ProductService productService, ReviewService reviewService) {
        this.productService = productService;
        this.reviewService = reviewService;
    }

    @PostMapping("/product/{id}/review")
    public String add(@PathVariable Long id,
                      @RequestParam String author,
                      @RequestParam @Min(1) @Max(5) int rating,
                      @RequestParam String comment) {
        Product p = productService.findById(id);
        if (p == null) {
            return "redirect:/products";
        }
        Review r = new Review();
        r.setProduct(p);
        r.setAuthor(author == null || author.isBlank() ? "匿名" : author);
        r.setRating(rating);
        r.setComment(comment);
        r.setStatus("PENDING");
        reviewService.save(r);
        return "redirect:/product/" + id + "?review=1";
    }
}
