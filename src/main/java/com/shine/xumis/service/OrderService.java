package com.shine.xumis.service;

import com.shine.xumis.model.Order;
import com.shine.xumis.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order save(Order order) {
        return orderRepository.save(order);
    }

    public List<Order> findAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order findByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).orElse(null);
    }

    public long countAll() {
        return orderRepository.count();
    }

    public long countByStatus(String status) {
        return orderRepository.countByStatus(status);
    }
}
