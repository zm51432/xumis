package com.shine.xumis.controller;

import com.shine.xumis.util.RedirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.stereotype.Controller;

import java.util.Locale;

/**
 * 语言切换。
 *
 * 为什么需要这个端点：LocaleChangeInterceptor 只在 URL 上「存在」?lang= 参数时才生效，
 * 因此靠"删除 lang 参数"来切回中文是无效的（Cookie 里仍然是英文）。
 * 这里改为显式写入 Locale（底层由 CookieLocaleResolver 持久化），再安全跳回原页面。
 */
@Controller
public class LocaleController {

    private final LocaleResolver localeResolver;

    public LocaleController(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    /**
     * @param l        目标语言：zh / en（其它值回落到 zh）
     * @param redirect 跳回的站内相对路径（会被 RedirectUtils 校验，防止开放重定向）
     */
    @GetMapping("/set-lang")
    public String setLang(@RequestParam(value = "l", required = false) String l,
                          @RequestParam(value = "redirect", required = false) String redirect,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        Locale target = resolve(l);
        localeResolver.setLocale(request, response, target);
        return "redirect:" + RedirectUtils.safe(redirect, request);
    }

    private Locale resolve(String l) {
        if (l == null) {
            return Locale.CHINESE;
        }
        String v = l.trim().toLowerCase();
        if (v.startsWith("en")) {
            return Locale.ENGLISH;
        }
        if (v.startsWith("zh") || v.startsWith("cn")) {
            return Locale.CHINESE;
        }
        return Locale.CHINESE;
    }
}
