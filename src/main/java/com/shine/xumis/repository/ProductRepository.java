package com.shine.xumis.repository;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(Category category);
    List<Product> findByFeaturedTrue();
    List<Product> findByCustomizableTrue();
}
