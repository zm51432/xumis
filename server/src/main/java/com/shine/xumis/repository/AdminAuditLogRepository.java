package com.shine.xumis.repository;

import com.shine.xumis.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findTop300ByOrderByIdDesc();
}
