package com.shine.xumis.repository;

import com.shine.xumis.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);
    List<Order> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
