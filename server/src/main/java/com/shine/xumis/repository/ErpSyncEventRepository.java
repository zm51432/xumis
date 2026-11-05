package com.shine.xumis.repository;

import com.shine.xumis.model.ErpSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ErpSyncEventRepository extends JpaRepository<ErpSyncEvent, Long> {
    List<ErpSyncEvent> findTop60ByOrderByIdDesc();
    long countByStatus(String status);
    List<ErpSyncEvent> findByStatusAndNextRetryAtBefore(String status, LocalDateTime time);
    long deleteByCreatedAtBefore(LocalDateTime time);
}
