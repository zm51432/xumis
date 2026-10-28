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
}
