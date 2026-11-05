package com.shine.xumis.controller;

import com.shine.xumis.config.AppProperties;
import com.shine.xumis.config.EnLocaleFilter;
import com.shine.xumis.model.Category;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.CatalogExtraService;
import com.shine.xumis.service.ContentPageService;
import com.shine.xumis.service.CurrencyService;
import com.shine.xumis.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 全局模型增强：为所有页面提供店铺名、分类列表、币种与购物车数量（导航/页脚/购物车角标共用）。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    public static final String STORE_NAME = "shine星心";
    public static final String STORE_NAME_EN = "shine Star-Heart";

    /** 国家/地区下拉选项（所有需要填写国家的表单统一使用，保证与已存数据一致） */
    public static final List<String> COUNTRIES = List.of(
            "中国大陆", "中国香港", "中国澳门", "中国台湾",
            "United States", "Canada", "United Kingdom",
            "Germany", "France", "Italy", "Spain", "Netherlands",
            "Australia", "New Zealand",
            "Japan", "South Korea", "Singapore",
            "Malaysia", "Thailand", "Vietnam", "Indonesia", "Philippines",
            "India", "Brazil", "Mexico", "Russia",
            "United Arab Emirates", "Other"
    );

    private final CurrencyService currencyService;
    private final CartService cartService;
    private final SettingsService settings;
    private final ContentPageService contentPages;
    private final CatalogExtraService catalog;

    /** 后台入口基址：未配置 app.admin-path 时为 /admin，配置后为隐藏别名（见 AdminPathFilter） */
    @Value("${app.admin-path:}")
    private String adminPath;

    public GlobalModelAdvice(CurrencyService currencyService, CartService cartService,
                             SettingsService settings, ContentPageService contentPages,
                             CatalogExtraService catalog) {
        this.currencyService = currencyService;
        this.cartService = cartService;
        this.settings = settings;
        this.contentPages = contentPages;
        this.catalog = catalog;
    }

    @ModelAttribute
    public void populateGlobal(HttpSession session, HttpServletRequest request, Model model) {
        // 店铺名/文案/运费/SEO 全部来自后台可编辑的「站点设置」
        Map<String, String> cfg = settings.map();
        model.addAttribute("cfg", cfg);
        model.addAttribute("storeName", orDefault(cfg.get(SettingsService.SITE_NAME), STORE_NAME));
        model.addAttribute("storeNameEn", orDefault(cfg.get(SettingsService.SITE_NAME_EN), STORE_NAME_EN));
        model.addAttribute("siteTagline", cfg.get(SettingsService.SITE_TAGLINE));
        model.addAttribute("siteEmail", settings.getOr(SettingsService.SITE_EMAIL, "xumo92241@gmail.com"));
        model.addAttribute("siteContact", settings.getOr(SettingsService.SITE_CONTACT, "Ben"));
        model.addAttribute("sitePhone", cfg.get(SettingsService.SITE_PHONE));
        model.addAttribute("siteAddress", cfg.get(SettingsService.SITE_ADDRESS));
        model.addAttribute("currentYear", java.time.Year.now().getValue());
        model.addAttribute("freeShipThreshold", settings.getInt(SettingsService.SHOP_FREE_SHIP, 0));
        model.addAttribute("shippingFee", settings.getInt(SettingsService.SHOP_SHIP_FEE, 0));
        model.addAttribute("taxRate", settings.get(SettingsService.SHOP_TAX_RATE));
        model.addAttribute("showCategoryFilter", settings.getBool(SettingsService.SHOP_SHOW_CAT));
        model.addAttribute("categories", Arrays.asList(Category.values()));
        model.addAttribute("countries", COUNTRIES);
        model.addAttribute("adminBase", adminBase());

        // 站点绝对基址：供 OG / 结构化数据 / canonical 生成绝对地址
        String base = siteBaseUrl(request);
        model.addAttribute("siteBaseUrl", base);

        // 多语言 URL（P2-2）：/en/ 前缀由 EnLocaleFilter 转发，这里据此生成内链前缀与 hreflang
        String langPrefix = request.getAttribute(EnLocaleFilter.LANG_PREFIX_ATTR) == null ? "" : "/en";
        String rawPath = request.getRequestURI();
        if (rawPath == null || rawPath.isEmpty()) rawPath = "/";
        // 转发后 URI 是去掉 /en 的中文路径，拼回前缀即英文站的规范地址
        String query = request.getQueryString() == null || request.getQueryString().isBlank()
                ? "" : "?" + request.getQueryString();
        model.addAttribute("langPrefix", langPrefix);
        model.addAttribute("isEn", !langPrefix.isEmpty());
        model.addAttribute("canonicalUrl", base + langPrefix + rawPath + query);
        model.addAttribute("alternateZh", base + rawPath + query);
        model.addAttribute("alternateEn", base + "/en" + rawPath + query);
        model.addAttribute("storeCategories", catalog.categories());
        model.addAttribute("contentPages", contentPages.published());
        model.addAttribute("analyticsExternalId",
                settings.getOr(SettingsService.ANALYTICS_EXTERNAL_ID, ""));

        // 多币种：从 session 读取用户选择，缺省用默认币种
        String selected = (String) session.getAttribute("CURRENCY");
        AppProperties.Currency cur = currencyService.resolve(selected);
        model.addAttribute("currencyCode", cur.getCode());
        model.addAttribute("currencySymbol", cur.getSymbol());
        model.addAttribute("fxRate", cur.getRate());
        model.addAttribute("currencies", currencyService.enabled());

        // 购物车数量角标
        model.addAttribute("cartCount", cartService.count(session));

        // CSRF 令牌（页面通过 <meta name="_csrf"> 暴露给前端）
        model.addAttribute("csrf", com.shine.xumis.util.Csrf.token(session));

        // 当前登录的 C 端客户（导航显示「登录 / 我的账户」）
        Object u = session.getAttribute(com.shine.xumis.service.UserAccountService.SESSION_KEY);
        if (u instanceof com.shine.xumis.model.UserAccount user) {
            model.addAttribute("user", user);
        }
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    /** 后台入口基址（隐藏别名启用时为别名，否则 /admin） */
    private String adminBase() {
        String p = (adminPath == null) ? "" : adminPath.trim();
        if (p.isEmpty() || p.equals("admin") || p.equals("/admin")) {
            return "/admin";
        }
        return p.startsWith("/") ? p : "/" + p;
    }

    /** 根据请求拼出站点绝对基址（与 SeoController.baseUrl 保持一致） */
    private String siteBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String base = scheme + "://" + host;
        if (!((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
            base += ":" + port;
        }
        return base;
    }
}
