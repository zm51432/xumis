package com.shine.xumis.service;

import com.shine.xumis.dto.ProductSearchResult;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findByCategory(Category category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> findFeatured() {
        return productRepository.findByFeaturedTrue();
    }

    public List<Product> findCustomizable() {
        return productRepository.findByCustomizableTrue();
    }

    /** 分页查询（手机端 API 用） */
    public Page<Product> findPage(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Page<Product> findPageByCategory(Category category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable);
    }

    public Page<Product> findPageByFeatured(Pageable pageable) {
        return productRepository.findByFeaturedTrue(pageable);
    }

    /**
     * 商品搜索 + 分类 + 排序 + 分页。
     * 说明：查询下沉到数据库（分页/排序由 SQL 完成），不再把整表读进内存再过滤，
     * 商品规模增长时性能与内存占用都可控。
     * sort 取值：default(默认排序) / price_asc / price_desc / newest
     */
    public ProductSearchResult search(String query, Category category, String sort,
                                      int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 60);
        org.springframework.data.domain.Sort order = switch (sort == null ? "" : sort) {
            case "price_asc" -> org.springframework.data.domain.Sort.by("price").ascending();
            case "price_desc" -> org.springframework.data.domain.Sort.by("price").descending();
            case "newest" -> org.springframework.data.domain.Sort.by("id").descending();
            default -> org.springframework.data.domain.Sort.by("id").ascending();
        };
        String kw = (query == null || query.isBlank()) ? "" : query.trim();

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize, order);
        org.springframework.data.domain.Page<Product> p = productRepository.search(kw, category, pageable);

        int totalPages = Math.max(1, p.getTotalPages());
        int safePage = Math.min(p.getNumber(), totalPages - 1);
        return new ProductSearchResult(p.getContent(), safePage, totalPages, p.getTotalElements());
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    /** 后台删除商品 */
    public void delete(Long id) {
        if (id != null) {
            productRepository.deleteById(id);
        }
    }

    /** 计算商品在某币种下的展示价（基准 USD × 汇率，四舍五入 2 位） */
    public BigDecimal priceIn(Product p, java.math.BigDecimal fxRate) {
        if (p.getPrice() == null || fxRate == null) return BigDecimal.ZERO;
        return p.getPrice().multiply(fxRate)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
