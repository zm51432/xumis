package com.shine.xumis.repository;

import com.shine.xumis.model.SiteCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SiteCheckRepository extends JpaRepository<SiteCheck, Long> {

    List<SiteCheck> findTop100ByOrderByIdDesc();

    List<SiteCheck> findTop200ByCheckedAtAfterOrderByIdDesc(LocalDateTime after);

    void deleteByCheckedAtBefore(LocalDateTime before);
}
