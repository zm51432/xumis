package com.shine.xumis.repository;

import com.shine.xumis.model.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreCategoryRepository extends JpaRepository<StoreCategory, String> {
    List<StoreCategory> findByEnabledTrueOrderBySortOrderAsc();
    List<StoreCategory> findAllByOrderBySortOrderAsc();
}
