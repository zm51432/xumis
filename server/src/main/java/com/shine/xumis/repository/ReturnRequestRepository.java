package com.shine.xumis.repository;

import com.shine.xumis.model.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByRequestNoOrderByIdAsc(String requestNo);

    List<ReturnRequest> findByRequestNoAndStatusOrderByIdAsc(String requestNo, String status);

    List<ReturnRequest> findByOrderNoAndStatusOrderByIdAsc(String orderNo, String status);

    List<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ReturnRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<ReturnRequest> findAllByOrderByCreatedAtDesc();

    /** 同一订单同一商品待审数量，避免重复申请导致超退 */
    @Query("select coalesce(sum(r.quantity), 0) from ReturnRequest r " +
           "where r.orderNo = :orderNo and r.productId = :productId and r.status = 'REQUESTED'")
    int sumPending(@Param("orderNo") String orderNo, @Param("productId") Long productId);
}
