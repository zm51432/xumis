package com.shine.xumis.service;

import com.shine.xumis.model.Order;
import com.shine.xumis.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** 我的订单：登录用户按其 ID + 邮箱取并集，避免游客期订单丢失 */
    public List<Order> findMyOrders(Long userId, String email) {
        List<Order> list = new ArrayList<>();
        if (userId != null) {
            for (Order o : orderRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
                if (!containsId(list, o.getId())) list.add(o);
            }
        }
        if (email != null && !email.isBlank()) {
            for (Order o : orderRepository.findByEmailOrderByCreatedAtDesc(email.trim())) {
                if (!containsId(list, o.getId())) list.add(o);
            }
        }
        list.sort((a, b) -> (b.getCreatedAt() == null ? java.time.LocalDateTime.MIN : b.getCreatedAt())
                .compareTo(a.getCreatedAt() == null ? java.time.LocalDateTime.MIN : a.getCreatedAt()));
        return list;
    }

    private boolean containsId(List<Order> list, Long id) {
        for (Order o : list) {
            if (o.getId() != null && o.getId().equals(id)) return true;
        }
        return false;
    }

    /** 后台推进订单状态：PENDING → PAID → SHIPPED → DONE（或 CANCELLED） */
    @Transactional
    public boolean updateStatus(Long id, String status) {
        Optional<Order> opt = orderRepository.findById(id);
        if (opt.isEmpty() || status == null || status.isBlank()) {
            return false;
        }
        Order order = opt.get();
        order.setStatus(status.trim().toUpperCase());
        orderRepository.save(order);
        return true;
    }
}
