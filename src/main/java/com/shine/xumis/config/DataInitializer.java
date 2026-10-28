package com.shine.xumis.config;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首次启动时写入演示商品数据（仅当商品表为空时）。
 * 图片使用占位图服务，可替换为自有图床。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ErpSyncService erpSyncService;

    public DataInitializer(ProductRepository productRepository, ErpSyncService erpSyncService) {
        this.productRepository = productRepository;
        this.erpSyncService = erpSyncService;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }
        String img = "https://placehold.co/600x600/FFE3EC/FF6F91?text=shine";
        List<Product> seeds = List.of(
            new Product("星心项链", "Star Heart Necklace", Category.JEWELRY, new BigDecimal("19.90"), img + "1"),
            new Product("贝珠耳环", "Shell Pearl Earrings", Category.JEWELRY, new BigDecimal("12.50"), img + "2"),
            new Product("编织手链", "Woven Bracelet", Category.JEWELRY, new BigDecimal("8.80"), img + "3"),
            new Product("银质戒指", "Sterling Silver Ring", Category.JEWELRY, new BigDecimal("15.00"), img + "4"),
            new Product("手工陶杯", "Handmade Ceramic Cup", Category.HANDMADE, new BigDecimal("22.00"), img + "5"),
            new Product("手绘丝巾", "Hand-painted Silk Scarf", Category.HANDMADE, new BigDecimal("28.00"), img + "6"),
            new Product("钩针玩偶", "Crochet Doll", Category.HANDMADE, new BigDecimal("18.50"), img + "7"),
            new Product("定制姓名牌", "Custom Nameplate", Category.HANDMADE, new BigDecimal("9.90"), img + "8")
        );
        seeds.get(0).setFeatured(true);
        seeds.get(1).setFeatured(true);
        seeds.get(4).setFeatured(true);
        seeds.get(6).setFeatured(true);
        seeds.get(0).setCustomizable(true);
        seeds.get(3).setCustomizable(true);
        seeds.get(7).setCustomizable(true);
        for (Product p : seeds) {
            p.setMaterial("合金 / 天然石");
            p.setStock(100);
            p.setDescription("shine星心精选" + p.getCategory().getLabel() + "，支持来图定制与小批量批发。");
            p.setDescriptionEn("Curated " + p.getCategory().name().toLowerCase() +
                    " from shine星心. Support custom design & small wholesale.");
        }
        productRepository.saveAll(seeds);
        erpSyncService.syncAllProducts(seeds);
    }
}
