package com.shine.xumis.repository;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(Category category);
    List<Product> findByFeaturedTrue();
    List<Product> findByCustomizableTrue();
    Page<Product> findByCategory(Category category, Pageable pageable);

    /**
     * 关键字 + 分类的**数据库侧**分页查询。
     * 之前是 findAll() 全表读进内存再过滤排序，商品规模一大就会明显变慢且吃内存。
     */
    @Query("SELECT p FROM Product p WHERE " +
            "(:kw IS NULL OR :kw = '' OR " +
            " LOWER(COALESCE(p.name, '')) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
            " LOWER(COALESCE(p.nameEn, '')) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
            " LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :kw, '%')) OR " +
            " LOWER(COALESCE(p.descriptionEn, '')) LIKE LOWER(CONCAT('%', :kw, '%'))) " +
            "AND (:cat IS NULL OR p.category = :cat)")
    Page<Product> search(@Param("kw") String kw, @Param("cat") Category cat, Pageable pageable);
}
