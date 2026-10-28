package com.shine.xumis.service;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.Review;
import com.shine.xumis.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public void save(Review review) {
        reviewRepository.save(review);
    }

    public List<Review> approvedFor(Product product) {
        return reviewRepository.findByProductAndStatus(product, "APPROVED");
    }

    public List<Review> pending() {
        return reviewRepository.findByStatus("PENDING");
    }

    public long pendingCount() {
        return reviewRepository.countByStatus("PENDING");
    }

    public void approve(Long id) {
        reviewRepository.findById(id).ifPresent(r -> {
            r.setStatus("APPROVED");
            reviewRepository.save(r);
        });
    }

    /** 已审核评价的平均分（无评价返回 0） */
    public double avgRating(Product product) {
        List<Review> list = approvedFor(product);
        if (list.isEmpty()) return 0.0;
        return list.stream().mapToInt(Review::getRating).average().orElse(0.0);
    }
}
