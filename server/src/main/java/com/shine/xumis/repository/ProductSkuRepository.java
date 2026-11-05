package com.shine.xumis.repository;

import com.shine.xumis.model.ProductSku;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductSkuRepository extends JpaRepository<ProductSku, Long> {
    List<ProductSku> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
    List<ProductSku> findByProductIdAndEnabledTrueOrderBySortOrderAscIdAsc(Long productId);
    void deleteByProductId(Long productId);
}
