package com.shine.xumis.service;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.Review;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    /** 提交结果 */
    public enum SubmitResult { OK, NOT_PURCHASED, DUPLICATE, NO_PRODUCT }

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    public ReviewService(ReviewRepository reviewRepository, OrderRepository orderRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
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

    // ==================== 购买后评价 ====================

    /**
     * 判断某邮箱是否真的买过该商品：订单里含该商品且订单未取消。
     * 返回命中的订单号（用于写入评价作为购买凭证），未购买返回空。
     */
    @Transactional(readOnly = true)
    public Optional<String> findPurchase(String email, Long productId) {
        if (email == null || email.isBlank() || productId == null) {
            return Optional.empty();
        }
        String mail = email.trim().toLowerCase();
        for (Order o : orderRepository.findByEmailOrderByCreatedAtDesc(mail)) {
            if (o == null || o.getItems() == null) {
                continue;
            }
            if ("CANCELLED".equalsIgnoreCase(o.getStatus())) {
                continue;
            }
            for (OrderItem it : o.getItems()) {
                if (it != null && productId.equals(it.getProductId())) {
                    return Optional.ofNullable(o.getOrderNo());
                }
            }
        }
        return Optional.empty();
    }

    /** 该邮箱对某商品是否已评价过（一人一商品一条） */
    public Optional<Review> myReview(String email, Long productId) {
        if (email == null || email.isBlank() || productId == null) {
            return Optional.empty();
        }
        List<Review> list = reviewRepository.findByUserEmailAndProductId(email.trim().toLowerCase(), productId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 提交评价：必须是真实购买用户（按订单校验），且同一商品只能评价一次。
     * 提交后进入 PENDING，需后台审核通过才展示。
     */
    @Transactional
    public SubmitResult submit(Product product, String email, String author, int rating, String comment) {
        if (product == null) {
            return SubmitResult.NO_PRODUCT;
        }
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        Optional<String> orderNo = findPurchase(mail, product.getId());
        if (orderNo.isEmpty()) {
            return SubmitResult.NOT_PURCHASED;
        }
        if (myReview(mail, product.getId()).isPresent()) {
            return SubmitResult.DUPLICATE;
        }

        int safeRating = Math.max(1, Math.min(5, rating));
        String safeAuthor = (author == null || author.isBlank()) ? "匿名" : author.trim();
        if (safeAuthor.length() > 60) {
            safeAuthor = safeAuthor.substring(0, 60);
        }
        String safeComment = (comment == null) ? "" : comment.trim();
        if (safeComment.length() > 1000) {
            safeComment = safeComment.substring(0, 1000);
        }

        Review r = new Review();
        r.setProduct(product);
        r.setAuthor(safeAuthor);
        r.setUserEmail(mail);
        r.setOrderNo(orderNo.get());
        r.setVerifiedPurchase(true);
        r.setRating(safeRating);
        r.setComment(safeComment);
        r.setStatus("PENDING");
        reviewRepository.save(r);
        return SubmitResult.OK;
    }

    // ==================== 后台管理 ====================

    /** 后台评价列表：status 为空/ALL 时返回全部，按时间倒序 */
    public List<Review> listForAdmin(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return reviewRepository.findAllByOrderByCreatedAtDesc();
        }
        return reviewRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
    }

    /** 某商品的评价（后台按商品筛选） */
    public List<Review> forProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public long countAll() {
        return reviewRepository.count();
    }

    /** 取消通过：退回待审（前台立即不展示） */
    @Transactional
    public boolean unapprove(Long id) {
        return reviewRepository.findById(id).map(r -> {
            r.setStatus("PENDING");
            reviewRepository.save(r);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean delete(Long id) {
        if (id == null || !reviewRepository.existsById(id)) {
            return false;
        }
        reviewRepository.deleteById(id);
        return true;
    }
}
