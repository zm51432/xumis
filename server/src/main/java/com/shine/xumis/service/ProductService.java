package com.shine.xumis.service;

import com.shine.xumis.dto.ProductSearchResult;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.OrderItemRepository;
import com.shine.xumis.repository.OrderReturnRepository;
import com.shine.xumis.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository orderReturnRepository;

    public ProductService(ProductRepository productRepository, OrderItemRepository orderItemRepository,
                          OrderReturnRepository orderReturnRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderReturnRepository = orderReturnRepository;
    }

    /**
     * 商品 id → 净售出件数。
     * 口径：有效订单明细（已取消订单不计入） − 已生效退货数量，退货扣完不为负。
     * 首页热销榜、详情页「已售 N 件」、列表按销量排序、App 热销接口全部走这里。
     */
    public Map<Long, Integer> soldMap() {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : orderItemRepository.sumSoldByProduct()) {
            if (row[0] != null && row[1] != null) {
                map.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
            }
        }
        for (Object[] row : orderReturnRepository.sumReturnedByProduct()) {
            if (row[0] != null && row[1] != null) {
                long pid = ((Number) row[0]).longValue();
                int returned = ((Number) row[1]).intValue();
                map.merge(pid, -returned, Integer::sum);
            }
        }
        map.replaceAll((k, v) -> Math.max(0, v));
        return map;
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
        return search(query, category, sort, page, size, null, null);
    }

    /**
     * 商品搜索 + 分类 + 价格区间 + 排序 + 分页。
     * 价格区间用于前台「预算筛选」，避免买家在大量商品里逐页翻找。
     */
    public ProductSearchResult search(String query, Category category, String sort,
                                      int page, int size,
                                      java.math.BigDecimal priceMin, java.math.BigDecimal priceMax) {
        int safeSize = Math.min(Math.max(size, 1), 60);
        String kw = (query == null || query.isBlank()) ? "" : query.trim();

        // 按销量排序：售出量来自 order_item 聚合，无法直接写进 JPQL 的 ORDER BY，
        // 因此取符合条件的候选集（单店目录有限，上限 2000），按真实售出量排序后手动分页。
        if ("sold_desc".equalsIgnoreCase(sort)) {
            List<Product> all = new ArrayList<>(productRepository.search(kw, category, priceMin, priceMax,
                    org.springframework.data.domain.PageRequest.of(0, 2000,
                            org.springframework.data.domain.Sort.by("id").ascending())).getContent());
            Map<Long, Integer> sold = soldMap();
            all.sort(Comparator
                    .comparingInt((Product p) -> -sold.getOrDefault(p.getId(), 0))
                    .thenComparing(p -> p.getId() == null ? Long.MAX_VALUE : p.getId()));
            int total = all.size();
            int totalPages = Math.max(1, (int) Math.ceil(total / (double) safeSize));
            int safePage = Math.min(Math.max(page, 0), totalPages - 1);
            int from = Math.min(safePage * safeSize, total);
            int to = Math.min(from + safeSize, total);
            return new ProductSearchResult(new ArrayList<>(all.subList(from, to)), safePage, totalPages, total);
        }

        org.springframework.data.domain.Sort order = switch (sort == null ? "" : sort) {
            case "price_asc" -> org.springframework.data.domain.Sort.by("price").ascending();
            case "price_desc" -> org.springframework.data.domain.Sort.by("price").descending();
            case "newest" -> org.springframework.data.domain.Sort.by("id").descending();
            default -> org.springframework.data.domain.Sort.by("id").ascending();
        };

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize, order);
        org.springframework.data.domain.Page<Product> p = productRepository.search(kw, category, priceMin, priceMax, pageable);

        int totalPages = Math.max(1, p.getTotalPages());
        int safePage = Math.min(p.getNumber(), totalPages - 1);
        return new ProductSearchResult(p.getContent(), safePage, totalPages, p.getTotalElements());
    }

    /** 相关推荐：同分类优先，按真实售出量排序，其次精选补足（不再按 id 顺序堆） */
    public List<Product> findRelated(Product p, int limit) {
        Map<Long, Integer> sold = soldMap();
        Comparator<Product> bySales = Comparator
                .comparingInt((Product x) -> -sold.getOrDefault(x.getId(), 0))
                .thenComparing(x -> x.getId() == null ? Long.MAX_VALUE : x.getId());
        List<Product> same = productRepository.findByCategory(p.getCategory()).stream()
                .filter(x -> !x.getId().equals(p.getId()))
                .sorted(bySales)
                .collect(Collectors.toList());
        if (same.size() >= limit) return new ArrayList<>(same.subList(0, limit));
        List<Product> featured = productRepository.findByFeaturedTrue().stream()
                .filter(x -> !x.getId().equals(p.getId())
                        && same.stream().noneMatch(s -> s.getId().equals(x.getId())))
                .sorted(bySales)
                .limit(limit - same.size()).collect(Collectors.toList());
        List<Product> out = new ArrayList<>(same);
        out.addAll(featured);
        return out;
    }

    /**
     * 热销榜：按真实售出件数降序；销量相同或为 0 时，有库存的排前面。
     * 全站推荐逻辑统一走这里，避免首页/详情页各写一套。
     */
    public List<Product> findHot(int limit) {
        List<Product> all = new ArrayList<>(productRepository.findAll());
        Map<Long, Integer> sold = soldMap();
        all.sort(Comparator
                .comparingInt((Product x) -> -sold.getOrDefault(x.getId(), 0))
                .thenComparingInt(x -> (x.getStock() != null && x.getStock() > 0) ? 0 : 1)
                .thenComparing(x -> x.getId() == null ? Long.MAX_VALUE : x.getId()));
        return all.stream().limit(Math.max(limit, 1)).collect(Collectors.toList());
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
