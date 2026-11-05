package com.shine.xumis.repository;

import com.shine.xumis.model.ContentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContentPageRepository extends JpaRepository<ContentPage, String> {
    List<ContentPage> findByPublishedTrueOrderBySortOrderAsc();
    List<ContentPage> findAllByOrderBySortOrderAsc();
}
