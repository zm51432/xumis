package com.shine.xumis.repository;

import com.shine.xumis.model.VisitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {
    long countByDayAndType(String day, String type);

    @Query("select count(distinct v.visitor) from VisitEvent v where v.day = :day and v.type = :type")
    long countDistinctVisitor(String day, String type);

    @Query("select v.day, v.type, count(v), count(distinct v.visitor) from VisitEvent v " +
           "where v.day >= :from group by v.day, v.type order by v.day desc")
    List<Object[]> dailyStats(String from);

    @Query("select v.path, count(v) from VisitEvent v where v.type = 'VIEW' and v.day >= :from " +
           "group by v.path order by count(v) desc")
    List<Object[]> topPaths(String from);

    @Query("select v.productId, count(v) from VisitEvent v where v.type = 'VIEW' and v.productId is not null " +
           "and v.day >= :from group by v.productId order by count(v) desc")
    List<Object[]> topProducts(String from);
}
