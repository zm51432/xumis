package com.shine.xumis.repository;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.Review;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductAndStatus(Product product, String status);

    /** 后台待审列表需要展示商品名，这里用 EntityGraph 预加载 product，避免懒加载异常 */
    @EntityGraph(attributePaths = "product")
    List<Review> findByStatus(String status);

    long countByStatus(String status);

    /** 后台评价管理：按状态倒序（status 为空表示全部） */
    @EntityGraph(attributePaths = "product")
    List<Review> findByStatusOrderByCreatedAtDesc(String status);

    @EntityGraph(attributePaths = "product")
    List<Review> findAllByOrderByCreatedAtDesc();

    /** 是否已评价过（同一邮箱对同一商品只允许一条） */
    List<Review> findByUserEmailAndProductId(String userEmail, Long productId);

    @EntityGraph(attributePaths = "product")
    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);
}
