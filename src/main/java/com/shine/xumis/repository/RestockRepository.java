package com.shine.xumis.repository;

import com.shine.xumis.model.RestockRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestockRepository extends JpaRepository<RestockRequest, Long> {
}
