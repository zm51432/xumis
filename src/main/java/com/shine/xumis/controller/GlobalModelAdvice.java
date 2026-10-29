package com.shine.xumis.controller;

import com.shine.xumis.config.AppProperties;
import com.shine.xumis.model.Category;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.CurrencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.List;

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

    /** 后台入口基址：未配置 app.admin-path 时为 /admin，配置后为隐藏别名（见 AdminPathFilter） */
    @Value("${app.admin-path:}")
    private String adminPath;

    public GlobalModelAdvice(CurrencyService currencyService, CartService cartService) {
        this.currencyService = currencyService;
        this.cartService = cartService;
    }

    @ModelAttribute
    public void populateGlobal(HttpSession session, HttpServletRequest request, Model model) {
        model.addAttribute("storeName", STORE_NAME);
        model.addAttribute("storeNameEn", STORE_NAME_EN);
        model.addAttribute("categories", Arrays.asList(Category.values()));
        model.addAttribute("countries", COUNTRIES);
        model.addAttribute("adminBase", adminBase());

        // 站点绝对基址：供 OG / 结构化数据 / canonical 生成绝对地址，
        // 否则社交分享与搜索引擎拿到的会是相对路径，图片与链接失效。
        model.addAttribute("siteBaseUrl", siteBaseUrl(request));

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
