package com.shine.xumis.config;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Product;
import com.shine.xumis.repository.ProductRepository;
import com.shine.xumis.service.AdminUserService;
import org.springframework.beans.factory.annotation.Value;
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
    private final AdminUserService adminUserService;

    @Value("${app.admin-username:admin}")
    private String adminUsername;

    @Value("${app.admin-password:Admin@12345}")
    private String adminPassword;

    public DataInitializer(ProductRepository productRepository, ErpSyncService erpSyncService,
                           AdminUserService adminUserService) {
        this.productRepository = productRepository;
        this.erpSyncService = erpSyncService;
        this.adminUserService = adminUserService;
    }

    @Override
    public void run(String... args) {
        // 没有任何管理员时，创建默认站长账号（口令取自配置，登录后请立即修改）
        if (adminUserService.count() == 0) {
            adminUserService.ensureDefaultAdmin(adminUsername, adminPassword, "站长 Owner");
            System.out.println("============================================================");
            System.out.println(" 已创建默认站长账号： " + adminUsername + " / " + adminPassword);
            System.out.println(" 请登录 http://localhost:8080/admin 后立即修改密码！");
            System.out.println("============================================================");
        }

        if (productRepository.count() > 0) {
            return;
        }
        // 本地真实商品图（部署即展示，不依赖外网占位图）
        String base = "/img/products/";
        List<Product> seeds = List.of(
            mk(base + "p01_necklace_star_heart.png", "星心项链", "Star Heart Necklace", Category.JEWELRY, "19.90", "合金 / 镀玫瑰金", "经典星心吊坠，温柔点亮日常穿搭。", true, true),
            mk(base + "p02_earrings_shell_pearl.png", "贝珠耳环", "Shell Pearl Earrings", Category.JEWELRY, "12.50", "天然贝珠 / 合金", "天然贝珠耳坠，轻盈显气质。", true, false),
            mk(base + "p03_bracelet_woven.png", "编织友谊手链", "Woven Friendship Bracelet", Category.JEWELRY, "8.80", "棉线 / 丝线", "粉绿撞色编织，送给最好的朋友。", false, false),
            mk(base + "p04_ring_silver_heart.png", "银质心戒", "Sterling Silver Heart Ring", Category.JEWELRY, "15.00", "925 银", "细圈银戒点缀小爱心，日常百搭。", false, true),
            mk(base + "p05_necklace_moonstone.png", "月光石项链", "Moonstone Pendant", Category.JEWELRY, "21.00", "天然月光石 / 银链", "月光石随光晕染，神秘又温柔。", true, false),
            mk(base + "p06_mug_ceramic.png", "手作陶杯", "Handmade Ceramic Mug", Category.HANDMADE, "22.00", "釉下彩陶瓷", "鼠尾草绿手作陶杯，温润好握感。", false, false),
            mk(base + "p07_scarf_silk.png", "手绘真丝丝巾", "Hand-painted Silk Scarf", Category.HANDMADE, "28.00", "100% 桑蚕丝", "水彩花卉手绘，柔光流动。", false, false),
            mk(base + "p08_doll_crochet.png", "钩针兔玩偶", "Crochet Bunny Doll", Category.HANDMADE, "18.50", "棉纱线", "奶油色钩针兔，治愈系陪伴。", true, false),
            mk(base + "p09_candle_soy.png", "大豆蜡香薰", "Soy Wax Candle", Category.HANDMADE, "16.00", "大豆蜡 / 陶瓷杯", "淡香大豆蜡，氛围感拉满。", false, false),
            mk(base + "p10_frame_driedflower.png", "干花相框", "Dried Flower Frame", Category.HANDMADE, "24.00", "永生花 / 木框", "微缩花境定格，装饰小角落。", false, true),
            mk(base + "p11_clip_pearl.png", "珍珠发夹", "Pearl Hair Clip", Category.JEWELRY, "9.90", "仿珍珠 / 金色调", "小珍珠发夹，点睛甜美发型。", false, false),
            mk(base + "p12_bracelet_rosequartz.png", "玫瑰石英手串", "Rose Quartz Bracelet", Category.JEWELRY, "13.50", "天然粉晶 / 弹力线", "粉晶手串，温柔好寓意。", false, true)
        );
        for (Product p : seeds) {
            p.setStock(100);
        }
        productRepository.saveAll(seeds);
        erpSyncService.syncAllProducts(seeds);
    }

    private Product mk(String img, String name, String nameEn, Category cat, String price,
                       String material, String desc, boolean featured, boolean customizable) {
        Product p = new Product(name, nameEn, cat, new BigDecimal(price), img);
        p.setMaterial(material);
        p.setDescription(desc);
        p.setDescriptionEn(nameEn + " — a curated piece from shine星心.");
        p.setFeatured(featured);
        p.setCustomizable(customizable);
        return p;
    }
}
