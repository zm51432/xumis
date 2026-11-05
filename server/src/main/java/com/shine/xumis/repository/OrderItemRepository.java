package com.shine.xumis.repository;

import com.shine.xumis.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 每个商品的有效购买件数：已取消（CANCELLED）的订单不计入。
     * 返回 [productId, 件数] 二元数组。
     */
    @Query("select oi.productId, sum(oi.quantity) from OrderItem oi join oi.order o " +
           "where oi.productId is not null and upper(o.status) <> 'CANCELLED' " +
           "group by oi.productId")
    List<Object[]> sumSoldByProduct();
}
