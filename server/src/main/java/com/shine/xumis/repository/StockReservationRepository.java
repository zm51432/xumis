package com.shine.xumis.repository;

import com.shine.xumis.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    List<StockReservation> findByOrderNo(String orderNo);
    List<StockReservation> findByStatusAndExpiresAtBefore(String status, LocalDateTime time);
    List<StockReservation> findByStatus(String status);
    long countByStatus(String status);
}
