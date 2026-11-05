package com.shine.xumis.service;

import com.shine.xumis.model.ProductImage;
import com.shine.xumis.model.ProductSku;
import com.shine.xumis.model.StoreCategory;
import com.shine.xumis.repository.ProductImageRepository;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.repository.StoreCategoryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** 商品多图 / SKU / 分类实体的统一读写（P1-2 + P2-1）。 */
@Service
public class CatalogExtraService {

    private final ProductImageRepository imageRepo;
    private final ProductSkuRepository skuRepo;
    private final StoreCategoryRepository catRepo;

    public CatalogExtraService(ProductImageRepository imageRepo, ProductSkuRepository skuRepo,
                               StoreCategoryRepository catRepo) {
        this.imageRepo = imageRepo;
        this.skuRepo = skuRepo;
        this.catRepo = catRepo;
    }

    /** 内置分类兼容：把旧的三个枚举值落成可维护的数据行 */
    @PostConstruct
    @Transactional
    public void initCategories() {
        try {
            seed("JEWELRY", "饰品", "Jewelry", 1);
            seed("HANDMADE", "手工品", "Handmade", 2);
            seed("OTHER", "其他", "Other", 3);
        } catch (Exception ignored) {
            // 首次建表时序问题上电时忽略，后续请求会补上
        }
    }

    private void seed(String code, String zh, String en, int sort) {
        if (catRepo.existsById(code)) return;
        StoreCategory c = new StoreCategory();
        c.setCode(code); c.setNameZh(zh); c.setNameEn(en);
        c.setSortOrder(sort); c.setEnabled(true); c.setBuiltin(true);
        catRepo.save(c);
    }

    public List<StoreCategory> categories() {
        ensureCategories();
        return catRepo.findByEnabledTrueOrderBySortOrderAsc();
    }

    public List<StoreCategory> allCategories() {
        ensureCategories();
        return catRepo.findAllByOrderBySortOrderAsc();
    }

    private void ensureCategories() {
        if (catRepo.count() == 0) initCategories();
    }

    public String categoryLabel(String code, boolean en) {
        if (code == null) return "";
        return catRepo.findById(code)
                .map(c -> en ? (c.getNameEn() == null ? c.getNameZh() : c.getNameEn()) : c.getNameZh())
                .orElse(code);
    }

    @Transactional
    public void saveCategory(String code, String zh, String en, int sort, boolean enabled) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,60}")) return;
        StoreCategory c = catRepo.findById(code).orElseGet(() -> {
            StoreCategory n = new StoreCategory(); n.setCode(code); return n;
        });
        c.setNameZh(zh); c.setNameEn(en); c.setSortOrder(sort);
        c.setEnabled(enabled); c.setBuiltin(catRepo.existsById(code) && c.isBuiltin());
        catRepo.save(c);
    }

    @Transactional
    public boolean deleteCategory(String code) {
        return catRepo.findById(code).map(c -> {
            if (c.isBuiltin()) return false;
            catRepo.delete(c);
            return true;
        }).orElse(false);
    }

    // ---------- 多图 ----------

    public List<ProductImage> images(Long productId) {
        if (productId == null) return List.of();
        return imageRepo.findByProductIdOrderBySortOrderAscIdAsc(productId);
    }

    public Map<Long, List<ProductImage>> imagesMap(Collection<Long> ids) {
        Map<Long, List<ProductImage>> out = new HashMap<>();
        if (ids == null) return out;
        for (Long id : ids) out.put(id, images(id));
        return out;
    }

    public String imagesText(Long productId) {
        StringBuilder sb = new StringBuilder();
        for (ProductImage i : images(productId)) {
            if (i.getUrl() != null && !i.getUrl().isBlank()) sb.append(i.getUrl().trim()).append("\n");
        }
        return sb.toString().trim();
    }

    /** 每行一个图片 URL，最多 8 张；保存时整组替换 */
    @Transactional
    public int saveImages(Long productId, String text) {
        imageRepo.deleteByProductId(productId);
        if (text == null || text.isBlank()) return 0;
        int n = 0;
        for (String line : text.split("\\r?\\n")) {
            String url = line.trim();
            if (url.isEmpty() || n >= 8) continue;
            if (!url.startsWith("/") && !url.startsWith("http")) continue;
            ProductImage img = new ProductImage(productId, url, n);
            imageRepo.save(img);
            n++;
        }
        return n;
    }

    // ---------- SKU ----------

    public List<ProductSku> skus(Long productId) {
        if (productId == null) return List.of();
        return skuRepo.findByProductIdOrderBySortOrderAscIdAsc(productId);
    }

    public List<ProductSku> enabledSkus(Long productId) {
        if (productId == null) return List.of();
        return skuRepo.findByProductIdAndEnabledTrueOrderBySortOrderAscIdAsc(productId);
    }

    public ProductSku findSku(Long skuId) {
        return skuId == null ? null : skuRepo.findById(skuId).orElse(null);
    }

    /** 后台文本框格式：规格名|规格值|英文值|加价|库存|编码（后 4 项可省略） */
    public String skusText(Long productId) {
        StringBuilder sb = new StringBuilder();
        for (ProductSku s : skus(productId)) {
            sb.append(nullToEmpty(s.getOptionName())).append('|')
              .append(nullToEmpty(s.getOptionValue())).append('|')
              .append(nullToEmpty(s.getOptionValueEn())).append('|')
              .append(s.getPriceDelta() == null ? "0" : s.getPriceDelta().toPlainString()).append('|')
              .append(s.getStock() == null ? "0" : s.getStock()).append('|')
              .append(nullToEmpty(s.getSkuCode())).append('|')
              .append(s.isEnabled() ? "1" : "0").append('\n');
        }
        return sb.toString().trim();
    }

    @Transactional
    public int saveSkus(Long productId, String text) {
        skuRepo.deleteByProductId(productId);
        if (text == null || text.isBlank()) return 0;
        int n = 0;
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank() || n >= 30) continue;
            String[] f = line.split("\\|", -1);
            ProductSku s = new ProductSku();
            s.setProductId(productId);
            s.setOptionName(f.length > 0 ? f[0].trim() : "规格");
            s.setOptionValue(f.length > 1 ? f[1].trim() : "");
            s.setOptionValueEn(f.length > 2 ? f[2].trim() : "");
            s.setPriceDelta(parseDecimal(f.length > 3 ? f[3] : "0"));
            s.setStock(parseInt(f.length > 4 ? f[4] : "0", 0));
            s.setSkuCode(f.length > 5 ? f[5].trim() : "");
            s.setEnabled(f.length <= 6 || !"0".equals(f[6].trim()));
            s.setSortOrder(n);
            if (s.getOptionValue().isEmpty()) continue;
            skuRepo.save(s);
            n++;
        }
        return n;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static BigDecimal parseDecimal(String s) {
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}
