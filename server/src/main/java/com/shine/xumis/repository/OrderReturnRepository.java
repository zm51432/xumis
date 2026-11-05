package com.shine.xumis.repository;

import com.shine.xumis.model.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {

    /** 每个商品累计已退货件数（用于从售出量里扣减） */
    @Query("select r.productId, sum(r.quantity) from OrderReturn r " +
           "where r.status = 'APPLIED' group by r.productId")
    List<Object[]> sumReturnedByProduct();

    /** 每个商品累计退款金额，供销售汇总扣减 */
    @Query("select coalesce(sum(r.refundAmount), 0) from OrderReturn r where r.status = 'APPLIED'")
    java.math.BigDecimal totalRefunded();

    /** 某订单里某商品已退数量（防止超退） */
    @Query("select coalesce(sum(r.quantity), 0) from OrderReturn r " +
           "where r.orderNo = :orderNo and r.productId = :productId and r.status = 'APPLIED'")
    int sumReturned(@Param("orderNo") String orderNo, @Param("productId") Long productId);

    /** 幂等：同一退货单的同一商品只生效一次 */
    Optional<OrderReturn> findByReturnNoAndProductId(String returnNo, Long productId);

    List<OrderReturn> findByOrderNoOrderByIdAsc(String orderNo);
}
