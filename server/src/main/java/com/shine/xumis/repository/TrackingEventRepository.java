package com.shine.xumis.repository;

import com.shine.xumis.model.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {
    List<TrackingEvent> findByOrderNoOrderByEventTimeDescIdDesc(String orderNo);
    void deleteByOrderNo(String orderNo);
}
