package com.shine.xumis.controller;

import com.shine.xumis.config.AppProperties;
import com.shine.xumis.model.Category;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.CurrencyService;
import jakarta.servlet.http.HttpSession;
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

    private final CurrencyService currencyService;
    private final CartService cartService;

    public GlobalModelAdvice(CurrencyService currencyService, CartService cartService) {
        this.currencyService = currencyService;
        this.cartService = cartService;
    }

    @ModelAttribute
    public void populateGlobal(HttpSession session, Model model) {
        model.addAttribute("storeName", STORE_NAME);
        model.addAttribute("storeNameEn", STORE_NAME_EN);
        model.addAttribute("categories", Arrays.asList(Category.values()));

        // 多币种：从 session 读取用户选择，缺省用默认币种
        String selected = (String) session.getAttribute("CURRENCY");
        AppProperties.Currency cur = currencyService.resolve(selected);
        model.addAttribute("currencyCode", cur.getCode());
        model.addAttribute("currencySymbol", cur.getSymbol());
        model.addAttribute("fxRate", cur.getRate());
        model.addAttribute("currencies", currencyService.enabled());

        // 购物车数量角标
        model.addAttribute("cartCount", cartService.count(session));

        // 当前登录的 C 端客户（导航显示「登录 / 我的账户」）
        Object u = session.getAttribute(com.shine.xumis.service.UserAccountService.SESSION_KEY);
        if (u instanceof com.shine.xumis.model.UserAccount user) {
            model.addAttribute("user", user);
        }
    }
}
