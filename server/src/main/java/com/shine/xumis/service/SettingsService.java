package com.shine.xumis.service;

import com.shine.xumis.model.Category;
import com.shine.xumis.model.SiteSetting;
import com.shine.xumis.repository.SiteSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点设置服务：数据库 + 内存缓存。
 *
 * <p>所有可配置项集中在这里声明（key / 分组 / 名称 / 默认值），后台设置页
 * 直接按声明渲染表单，新增一个配置项只需要往 {@link #DEFS} 里加一行。</p>
 */
@Service
public class SettingsService {

    /** 一个配置项的定义（额外提供 JavaBean getter，模板里可直接 d.label / d.key 访问） */
    public record Def(String key, String group, String label, String hint, String def, boolean multiline) {
        public static Def text(String key, String group, String label, String hint, String def) {
            return new Def(key, group, label, hint, def, false);
        }

        public static Def area(String key, String group, String label, String hint, String def) {
            return new Def(key, group, label, hint, def, true);
        }

        public String getKey() { return key; }
        public String getGroup() { return group; }
        public String getLabel() { return label; }
        public String getHint() { return hint; }
        public String getDef() { return def; }
        public boolean isMultiline() { return multiline; }
    }

    public static final String SITE_NAME = "site.name";
    public static final String SITE_NAME_EN = "site.nameEn";
    public static final String SITE_TAGLINE = "site.tagline";
    public static final String SITE_DESC = "site.description";
    public static final String SITE_EMAIL = "site.email";
    public static final String SITE_CONTACT = "site.contact";
    public static final String SITE_PHONE = "site.phone";
    public static final String SITE_ADDRESS = "site.address";

    public static final String HOME_HERO = "home.heroTitle";
    public static final String HOME_SUB = "home.heroSubtitle";
    public static final String HOME_NOTICE = "home.notice";
    public static final String HOME_NOTICE_EN = "home.noticeEn";
    public static final String HOME_FEATURED_TITLE = "home.featuredTitle";
    public static final String HOME_HOT_TITLE = "home.hotTitle";
    public static final String HOME_HOT_ENABLED = "home.hotEnabled";

    public static final String SHOP_FREE_SHIP = "shop.freeShipThreshold";
    public static final String SHOP_SHIP_FEE = "shop.shippingFee";
    public static final String SHOP_TAX_RATE = "shop.taxRate";
    public static final String SHOP_SHOW_CAT = "shop.showCategoryFilter";
    public static final String SHOP_LOW_STOCK = "shop.lowStockThreshold";
    public static final String SHOP_RESERVE_MINUTES = "shop.reserveMinutes";
    public static final String MAIL_ORDER_NOTIFY = "mail.orderNotify";
    public static final String ANALYTICS_ENABLED = "analytics.enabled";
    public static final String ANALYTICS_EXTERNAL_ID = "analytics.externalId";

    public static final String SEO_TITLE = "seo.title";
    public static final String SEO_DESC = "seo.description";
    public static final String SEO_KEYWORDS = "seo.keywords";

    private static final List<Def> DEFS = List.of(
            Def.text(SITE_NAME, "基本信息", "店铺名（中文）", "导航、页脚、标题都会用到", "shine星心"),
            Def.text(SITE_NAME_EN, "基本信息", "店铺名（英文）", "英文站与副标题使用", "shine Star-Heart"),
            Def.text(SITE_TAGLINE, "基本信息", "一句话介绍", "首页副标题 / 页脚", "饰品与手工品的温柔制造者"),
            Def.area(SITE_DESC, "基本信息", "站点描述", "SEO description 与分享摘要", "shine星心 · 手工饰品与来图定制，跨境直发，断网也能浏览商品册。"),
            Def.text(SITE_EMAIL, "基本信息", "联系邮箱", "页脚与交易邮件里展示", "xumo92241@gmail.com"),
            Def.text(SITE_CONTACT, "基本信息", "联系人", "页脚「联系我们」展示", "Ben"),
            Def.text(SITE_PHONE, "基本信息", "客服电话", "页脚展示，可留空", ""),
            Def.text(SITE_ADDRESS, "基本信息", "工作室地址", "页脚展示，可留空", ""),

            Def.text(HOME_HERO, "首页内容", "首屏大标题", "留空则使用语言包默认文案", "让每一份心意，都被亲手点亮"),
            Def.text(HOME_SUB, "首页内容", "首屏副标题", "", "shine星心 · 手作饰品，一件一件做"),
            Def.text(HOME_NOTICE, "首页内容", "滚动字幕文案（中文）", "顶部公告条跑马灯", "只有销量，没有评价。"),
            Def.text(HOME_NOTICE_EN, "首页内容", "滚动字幕文案（英文）", "英文站 /en/ 的公告条", "Only sales, no reviews."),
            Def.text(HOME_FEATURED_TITLE, "首页内容", "精选区标题", "", "精选好物"),
            Def.text(HOME_HOT_TITLE, "首页内容", "热销区标题", "按真实售出量排序", "热销榜 · 大家都在买"),
            Def.text(HOME_HOT_ENABLED, "首页内容", "显示热销区", "true / false", "true"),

            Def.text(SHOP_FREE_SHIP, "交易与运费", "包邮门槛（USD）", "留空表示不做包邮提示", "99"),
            Def.text(SHOP_SHIP_FEE, "交易与运费", "基础运费（USD）", "未达包邮门槛时计入订单", "12"),
            Def.text(SHOP_TAX_RATE, "交易与运费", "税率（%）", "按折后商品金额计算，0 表示不收税；计入订单", "0"),
            Def.text(SHOP_SHOW_CAT, "交易与运费", "显示分类筛选", "单类目店铺建议 false", "false"),
            Def.text(SHOP_LOW_STOCK, "交易与运费", "低库存提示阈值", "库存低于该值显示「仅剩 N 件」", "3"),
            Def.text(SHOP_RESERVE_MINUTES, "交易与运费", "库存预占分钟数", "未付款订单超时自动取消并释放库存", "30"),

            Def.text(MAIL_ORDER_NOTIFY, "通知与自动化", "订单状态邮件", "下单/付款/发货/完成/取消自动发信：true / false", "true"),
            Def.text(ANALYTICS_ENABLED, "通知与自动化", "站内访问统计", "记录浏览/加购/下单漏斗：true / false", "true"),
            Def.text(ANALYTICS_EXTERNAL_ID, "通知与自动化", "外部分析站点 ID", "可选：填 GA4 或 Plausible 的站点 ID，留空只用站内统计", ""),

            Def.text(SEO_TITLE, "SEO", "页面标题", "留空自动用「店铺名 · 页面名」", "shine星心 · 手工饰品与来图定制"),
            Def.area(SEO_DESC, "SEO", "页面描述", "建议 80–160 字", "shine星心手工饰品独立站：手作饰品、来图定制，按真实售出量推荐，跨境直发。"),
            Def.text(SEO_KEYWORDS, "SEO", "关键词", "英文逗号分隔", "饰品,手工饰品,来图定制,jewelry,handmade,custom"),

            Def.text("cat.JEWELRY.zh", "分类名称", "饰品 · 中文", "", "饰品"),
            Def.text("cat.JEWELRY.en", "分类名称", "饰品 · 英文", "", "Jewelry"),
            Def.text("cat.HANDMADE.zh", "分类名称", "手工品 · 中文", "", "手工品"),
            Def.text("cat.HANDMADE.en", "分类名称", "手工品 · 英文", "", "Handmade"),
            Def.text("cat.OTHER.zh", "分类名称", "其他 · 中文", "", "其他"),
            Def.text("cat.OTHER.en", "分类名称", "其他 · 英文", "", "Other")
    );

    private final SiteSettingRepository repo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SiteSettingRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从数据库重新加载（保存后调用），同时刷新分类显示名 */
    public void reload() {
        try {
            cache.clear();
            for (SiteSetting s : repo.findAll()) {
                if (s.getKey() != null && s.getValue() != null) {
                    cache.put(s.getKey(), s.getValue());
                }
            }
        } catch (Exception ignored) {
            // 首次建库时表可能还没建好，用默认值兜底
        }
        applyCategoryLabels();
    }

    /** 分类名支持后台改：把设置写回枚举，模板里 c.label 不用改 */
    private void applyCategoryLabels() {
        for (Category c : Category.values()) {
            String zh = cache.get("cat." + c.name() + ".zh");
            String en = cache.get("cat." + c.name() + ".en");
            c.setLabels(blankToNull(zh), blankToNull(en));
        }
    }

    public String get(String key) {
        String v = cache.get(key);
        if (v != null && !v.isBlank()) return v;
        Def d = def(key);
        return d == null ? "" : d.def();
    }

    public String getOr(String key, String fallback) {
        String v = get(key);
        return v.isBlank() ? fallback : v;
    }

    public int getInt(String key, int fallback) {
        try {
            String v = get(key).trim();
            return v.isEmpty() ? fallback : Integer.parseInt(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean getBool(String key) {
        String v = get(key).trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on");
    }

    /** 当前全部配置（key → 生效值），供模板使用 ${cfg['site.name']} */
    public Map<String, String> map() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Def d : DEFS) m.put(d.key(), get(d.key()));
        return m;
    }

    public List<Def> defs() {
        return DEFS;
    }

    /** 按分组返回：分组名 → 配置项（后台设置页直接渲染） */
    public Map<String, List<Def>> grouped() {
        Map<String, List<Def>> g = new LinkedHashMap<>();
        for (Def d : DEFS) g.computeIfAbsent(d.group(), k -> new ArrayList<>()).add(d);
        return g;
    }

    @Transactional
    public void save(Map<String, String> form) {
        for (Def d : DEFS) {
            if (!form.containsKey(d.key())) continue;
            String v = form.get(d.key());
            if (v == null) v = "";
            v = v.trim();
            SiteSetting s = repo.findById(d.key()).orElseGet(() -> new SiteSetting(d.key(), ""));
            s.setValue(v.length() > 2000 ? v.substring(0, 2000) : v);
            repo.save(s);
        }
        reload();
    }

    /** 恢复某一分组为默认值 */
    @Transactional
    public void resetGroup(String group) {
        for (Def d : DEFS) {
            if (!d.group().equals(group)) continue;
            repo.deleteById(d.key());
        }
        reload();
    }

    private Def def(String key) {
        for (Def d : DEFS) if (d.key().equals(key)) return d;
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
