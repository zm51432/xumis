package com.shine.xumis.repository;

import com.shine.xumis.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    /** 预加载明细，避免 open-in-view=false 下渲染订单详情报懒加载异常 */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findAllByOrderByCreatedAtDesc();

    /** 我的订单：按账号 ID 或下单邮箱（兼容游客下单） */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByEmailOrderByCreatedAtDesc(String email);

    long countByStatus(String status);
}
