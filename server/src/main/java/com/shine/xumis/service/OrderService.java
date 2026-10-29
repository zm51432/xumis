package com.shine.xumis.service;

import com.shine.xumis.dto.ProductSales;
import com.shine.xumis.dto.SalesSummary;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // ==================== 销售统计 ====================

    /** 是否计入销售统计：已取消的订单不计入 */
    private static boolean counted(Order o) {
        String st = (o == null || o.getStatus() == null) ? "" : o.getStatus().toUpperCase();
        return !"CANCELLED".equals(st);
    }

    private static boolean confirmed(Order o) {
        String st = (o == null || o.getStatus() == null) ? "" : o.getStatus().toUpperCase();
        return st.equals("PAID") || st.equals("SHIPPED") || st.equals("DONE");
    }

    /**
     * 累计销售汇总：销售额（不含取消）、已确认收入、待确认金额、订单数、售出件数、客单价。
     * 金额以订单存储的基准币种（USD）为准。
     */
    public SalesSummary salesSummary() {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        long orders = 0;
        long items = 0;
        for (Order o : findAll()) {
            if (!counted(o) || o.getTotalAmount() == null) {
                continue;
            }
            total = total.add(o.getTotalAmount());
            orders++;
            if (confirmed(o)) {
                paid = paid.add(o.getTotalAmount());
            }
            if (o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    if (it != null && it.getQuantity() != null) {
                        items += it.getQuantity();
                    }
                }
            }
        }
        BigDecimal avg = (orders > 0)
                ? total.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new SalesSummary(total, paid, total.subtract(paid), orders, items, avg);
    }

    /** 按商品汇总售出件数与金额（不含取消订单），供商品管理页展示 */
    public Map<Long, ProductSales> soldByProduct() {
        Map<Long, long[]> acc = new HashMap<>();   // [件数, 金额(分)]
        for (Order o : findAll()) {
            if (!counted(o) || o.getItems() == null) {
                continue;
            }
            for (OrderItem it : o.getItems()) {
                if (it == null || it.getProductId() == null) {
                    continue;
                }
                int qty = (it.getQuantity() == null) ? 0 : it.getQuantity();
                BigDecimal price = (it.getPrice() == null) ? BigDecimal.ZERO : it.getPrice();
                long cents = price.multiply(BigDecimal.valueOf(qty))
                        .movePointRight(2).longValue();
                long[] cur = acc.computeIfAbsent(it.getProductId(), k -> new long[2]);
                cur[0] += qty;
                cur[1] += cents;
            }
        }
        Map<Long, ProductSales> out = new HashMap<>();
        acc.forEach((pid, v) -> out.put(pid, new ProductSales(v[0],
                BigDecimal.valueOf(v[1]).movePointLeft(2))));
        return out;
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
