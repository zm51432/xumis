package com.shine.xumis.service;

import com.shine.xumis.model.ContentPage;
import com.shine.xumis.repository.ContentPageRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;

/** 内容页（P2-5）：首次启动写入 5 篇默认内容，后台可直接编辑。 */
@Service
public class ContentPageService {

    private final ContentPageRepository repo;

    public ContentPageService(ContentPageRepository repo) { this.repo = repo; }

    private static final String[][] SEED = {
        {"about", "关于我们", "About Us", "1",
         "<p>shine星心 是一间专注手工饰品的小工作室。我们相信，一件被认真做出来的小物件，可以承载很具体的心意。</p><p>从选材、打磨到包装，每一件都由手工完成，不做批量复制，也不做虚饰的评价——我们只用真实售出量说话。</p>",
         "<p>shine Star-Heart is a small studio making handcrafted jewelry. We believe a carefully made piece can carry a very specific feeling.</p><p>Every item is finished by hand, from material selection to packaging. No mass production, no fabricated reviews — only real sales count.</p>"},
        {"story", "品牌故事", "Our Story", "2",
         "<p>名字里的「星心」，是星光与心意。我们想做的不是橱窗里冷冰冰的商品，而是能被戴很久、会留下痕迹的小东西。</p><p>因为规模小，我们把力气都花在材料和细节上：每一种材质都标注清楚，每一处尺寸都如实描述。</p>",
         "<p>Star-Heart stands for starlight and sincerity. We make pieces to be worn for a long time, not things that sit cold behind glass.</p><p>Being small lets us focus on materials and details: every material is labelled honestly and every size described as it is.</p>"},
        {"care", "材质与保养", "Materials & Care", "3",
         "<p><strong>银饰</strong>：避免接触香水、汗液与化学品；不佩戴时用软布擦净，放入干燥密封袋，氧化可用擦银布轻拭。</p><p><strong>镀金 / 合金</strong>：避免长时间沾水与摩擦，洗澡、运动、游泳前请取下。</p><p><strong>天然石 / 珍珠</strong>：避免暴晒与硬物碰撞，单独存放以防刮花。</p><p>手工制品存在细微差异，属正常现象，非质量问题。</p>",
         "<p><strong>Silver</strong>: keep away from perfume, sweat and chemicals. Wipe with a soft cloth and store dry; use a silver polishing cloth for oxidation.</p><p><strong>Plated / alloy</strong>: avoid prolonged water and friction; remove before showering or swimming.</p><p><strong>Natural stone / pearl</strong>: avoid direct sun and hard knocks, store separately.</p>"},
        {"size", "尺寸指南", "Size Guide", "4",
         "<p><strong>项链</strong>：40cm 贴颈，45cm 日常最百搭，50cm 适合叠戴；吊坠位置随链长下移。</p><p><strong>戒指</strong>：用细线绕指一圈量长度，对照周长表选号；手指在傍晚略粗，建议此时测量。</p><p><strong>手链</strong>：手腕净周长 + 1.5~2cm 为舒适长度。</p><p>不确定的话，欢迎通过询盘页告诉我们尺寸，我们会在制作前确认。</p>",
         "<p><strong>Necklace</strong>: 40cm sits close to the neck, 45cm is the everyday length, 50cm works for layering.</p><p><strong>Ring</strong>: measure finger circumference with a soft string, preferably in the evening.</p><p><strong>Bracelet</strong>: wrist circumference + 1.5–2cm for a comfortable fit.</p>"},
        {"shipping", "配送与退换", "Shipping & Returns", "5",
         "<p><strong>配送</strong>：现货 1–3 个工作日内发出，定制品按沟通工期制作。跨境运输通常 7–20 个工作日，节假日可能顺延；发货后可在「订单查询」查看物流单号与轨迹。</p><p><strong>退换</strong>：收到后 7 天内，未佩戴、包装完好的非定制商品可申请退换；定制商品非质量问题不支持退换。</p><p><strong>退货与销量</strong>：退货生效后，该商品的售出量会同步扣减，保证推荐榜单只反映真实成交。</p>",
         "<p><strong>Shipping</strong>: in-stock items ship within 1–3 business days. Cross-border delivery usually takes 7–20 business days; tracking is available on the order page.</p><p><strong>Returns</strong>: unworn, non-custom items in original packaging may be returned within 7 days of delivery.</p>"}
    };

    @PostConstruct
    public void init() {
        try {
            for (String[] s : SEED) {
                if (repo.existsById(s[0])) continue;
                ContentPage p = new ContentPage();
                p.setSlug(s[0]); p.setTitleZh(s[1]); p.setTitleEn(s[2]);
                p.setSortOrder(Integer.parseInt(s[3]));
                p.setBodyZh(s[4]); p.setBodyEn(s[5]); p.setPublished(true);
                repo.save(p);
            }
        } catch (Exception ignored) {
            // 首次建表时序问题，后续访问会补
        }
    }

    public List<ContentPage> published() {
        if (repo.count() == 0) init();
        return repo.findByPublishedTrueOrderBySortOrderAsc();
    }

    public List<ContentPage> all() { return repo.findAllByOrderBySortOrderAsc(); }
    public ContentPage find(String slug) { return slug == null ? null : repo.findById(slug).orElse(null); }
    public void save(ContentPage p) { repo.save(p); }
}
