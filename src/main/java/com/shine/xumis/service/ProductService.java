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

    /**
     * 商品搜索 + 分类 + 排序 + 分页（内存过滤，适合中小规模目录）。
     * sort 取值：default(默认排序) / price_asc / price_desc / newest
     */
    public ProductSearchResult search(String query, Category category, String sort,
                                      int page, int size) {
        List<Product> all = (category != null)
                ? new java.util.ArrayList<>(findByCategory(category))
                : new java.util.ArrayList<>(findAll());

        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            all = all.stream().filter(p -> {
                String hay = (p.getName() + " " + p.getNameEn() + " " +
                        p.getDescription() + " " + p.getDescriptionEn()).toLowerCase();
                return hay.contains(q);
            }).collect(Collectors.toList());
        }

        Comparator<Product> cmp = switch (sort == null ? "" : sort) {
            case "price_asc" -> Comparator.<Product, BigDecimal>comparing(Product::getPrice);
            case "price_desc" -> Comparator.<Product, BigDecimal>comparing(Product::getPrice).reversed();
            case "newest" -> Comparator.<Product, Long>comparing(Product::getId).reversed();
            default -> Comparator.<Product, Long>comparing(Product::getId);
        };
        all.sort(cmp);

        long total = all.size();
        int totalPages = (int) Math.max(1, Math.ceil((double) total / size));
        int safePage = Math.min(Math.max(page, 0), totalPages - 1);
        int from = safePage * size;
        int to = Math.min(from + size, all.size());
        List<Product> pageItems = from <= to ? all.subList(from, to) : List.of();

        return new ProductSearchResult(pageItems, safePage, totalPages, total);
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
