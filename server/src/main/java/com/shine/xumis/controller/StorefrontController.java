package com.shine.xumis.controller;

import com.shine.xumis.dto.ProductSearchResult;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.RestockRequest;
import com.shine.xumis.repository.RestockRepository;
import com.shine.xumis.service.CatalogExtraService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.*;

/** 店铺前台页面（分类实体化 + 多图/SKU 增强版）。 */
@Controller
public class StorefrontController {

    private final ProductService productService;
    private final RestockRepository restockRepository;
    private final OrderService orderService;
    private final CatalogExtraService catalog;

    public StorefrontController(ProductService productService,
                                RestockRepository restockRepository, OrderService orderService,
                                CatalogExtraService catalog) {
        this.productService = productService;
        this.restockRepository = restockRepository;
        this.orderService = orderService;
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featured", productService.findFeatured());
        model.addAttribute("customizable", productService.findCustomizable());
        model.addAttribute("hot", productService.findHot(8));
        model.addAttribute("sold", soldMap(productService.findAll()));
        return "home";
    }

    /**
     * 商品列表：cat 既可以是旧枚举（JEWELRY/HANDMADE/OTHER），
     * 也可以是后台新建的分类 code（P2-1）。新分类走内存分页，规模小且不影响旧查询性能。
     */
    @GetMapping("/products")
    public String products(@RequestParam(value = "cat", required = false) String catParam,
                           @RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "sort", defaultValue = "default") String sort,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           @RequestParam(value = "priceMin", required = false) String priceMinStr,
                           @RequestParam(value = "priceMax", required = false) String priceMaxStr,
                           Model model) {
        int size = 12;
        BigDecimal priceMin = parsePrice(priceMinStr);
        BigDecimal priceMax = parsePrice(priceMaxStr);
        Category cat = null;
        String catCode = null;
        if (catParam != null && !catParam.isBlank()) {
            try {
                cat = Category.valueOf(catParam.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                catCode = catParam.trim();
            }
        }
        ProductSearchResult result = (catCode == null)
                ? productService.search(q, cat, sort, page, size, priceMin, priceMax)
                : searchByCode(q, catCode, sort, page, size, priceMin, priceMax);
        model.addAttribute("products", result.getItems());
        model.addAttribute("sold", soldMap(result.getItems()));
        model.addAttribute("activeCat", cat);
        model.addAttribute("activeCatCode", catCode);
        model.addAttribute("categoryName", catCode == null ? "" : catalog.categoryLabel(catCode, false));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort);
        model.addAttribute("pmin", priceMinStr == null ? "" : priceMinStr);
        model.addAttribute("pmax", priceMaxStr == null ? "" : priceMaxStr);
        model.addAttribute("page", result.getPage());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalItems", result.getTotalItems());
        return "products";
    }

    private ProductSearchResult searchByCode(String q, String code, String sort, int page, int size,
                                             BigDecimal min, BigDecimal max) {
        String kw = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Product> all = new ArrayList<>();
        for (Product p : productService.findAll()) {
            if (p.getCategoryCode() == null || !code.equalsIgnoreCase(p.getCategoryCode())) continue;
            if (!kw.isEmpty()) {
                String hay = ((p.getName() == null ? "" : p.getName()) + " "
                        + (p.getNameEn() == null ? "" : p.getNameEn()) + " "
                        + (p.getDescription() == null ? "" : p.getDescription())).toLowerCase(Locale.ROOT);
                if (!hay.contains(kw)) continue;
            }
            BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            if (min != null && price.compareTo(min) < 0) continue;
            if (max != null && price.compareTo(max) > 0) continue;
            all.add(p);
        }
        Map<Long, Integer> sold = productService.soldMap();
        Comparator<Product> cmp = switch (sort == null ? "" : sort) {
            case "price_asc" -> Comparator.<Product, BigDecimal>comparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice());
            case "price_desc" -> Comparator.<Product, BigDecimal>comparing(p -> p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()).reversed();
            case "newest" -> Comparator.<Product, Long>comparing(p -> p.getId() == null ? 0L : p.getId()).reversed();
            case "sold_desc" -> Comparator.comparingInt((Product p) -> -sold.getOrDefault(p.getId(), 0));
            default -> Comparator.<Product, Long>comparing(p -> p.getId() == null ? 0L : p.getId());
        };
        all.sort(cmp);
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
        int safePage = Math.min(Math.max(page, 0), totalPages - 1);
        int from = Math.min(safePage * size, total);
        int to = Math.min(from + size, total);
        return new ProductSearchResult(new ArrayList<>(all.subList(from, to)), safePage, totalPages, total);
    }

    private Map<Long, Integer> soldMap(List<Product> products) {
        Map<Long, Integer> map = new HashMap<>(productService.soldMap());
        if (products != null) {
            for (Product p : products) {
                if (p != null && p.getId() != null) map.putIfAbsent(p.getId(), 0);
            }
        }
        return map;
    }

    private BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(s.trim());
            return v.compareTo(BigDecimal.ZERO) < 0 ? null : v;
        } catch (NumberFormatException e) { return null; }
    }

    @GetMapping("/product/{id}")
    public String detail(@PathVariable Long id, Model model,
                         jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Product p = productService.findById(id);
        if (p == null) {
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        model.addAttribute("product", p);
        model.addAttribute("related", productService.findRelated(p, 4));
        model.addAttribute("hot", productService.findHot(4));
        model.addAttribute("productSold", productService.soldMap().getOrDefault(p.getId(), 0));
        model.addAttribute("images", catalog.images(id));
        model.addAttribute("skus", catalog.enabledSkus(id));
        model.addAttribute("categoryName", p.getCategoryCode() == null || p.getCategoryCode().isBlank()
                ? "" : catalog.categoryLabel(p.getCategoryCode(), false));
        return "product-detail";
    }

    @GetMapping("/checkout")
    public String checkoutAlias() { return "redirect:/cart/checkout"; }

    @GetMapping("/order-success")
    public String orderSuccess(@RequestParam(value = "no", required = false) String orderNo, Model model) {
        model.addAttribute("orderNo", orderNo);
        if (orderNo != null && !orderNo.isBlank()) {
            model.addAttribute("order", orderService.findByOrderNo(orderNo));
        }
        return "order-success";
    }

    @PostMapping("/product/{id}/restock")
    public String restock(@PathVariable Long id,
                          @RequestParam String email,
                          @RequestParam(required = false) String company,
                          HttpServletRequest request) {
        if (company != null && !company.isBlank()) return "redirect:/product/" + id + "?restock=1";
        Product p = productService.findById(id);
        if (p != null && email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            RestockRequest r = new RestockRequest();
            r.setProductId(id);
            r.setEmail(email.trim().toLowerCase());
            restockRepository.save(r);
        }
        return "redirect:/product/" + id + "?restock=1";
    }
}
