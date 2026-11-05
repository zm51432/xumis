package com.shine.xumis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.Locale;

/**
 * 多语言 URL（P2-2）：/en/... 作为英文站独立路径，内部转发到同一控制器，
 * 同时把语言 Cookie 固定为英文，并打上 langPrefix 供模板生成 /en/ 内链与 hreflang。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EnLocaleFilter extends OncePerRequestFilter {

    public static final String LANG_PREFIX_ATTR = "SHINE_LANG_PREFIX";

    private final LocaleResolver localeResolver;

    public EnLocaleFilter(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.equals("/en") && !uri.startsWith("/en/")) {
            chain.doFilter(request, response);
            return;
        }
        String target = uri.equals("/en") ? "/" : uri.substring(3);
        if (target.isEmpty() || target.charAt(0) != '/') target = "/" + target;
        // 静态资源不走英文前缀重写（正常情况模板不会生成这种链接，仅为兜底）
        if (target.startsWith("/css/") || target.startsWith("/js/") || target.startsWith("/files/")
                || target.startsWith("/img/")) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(LANG_PREFIX_ATTR, "/en");
        try {
            localeResolver.setLocale(request, response, Locale.ENGLISH);
        } catch (Exception ignored) {
            // 语言 Cookie 写入失败不影响页面
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher(target);
        dispatcher.forward(request, response);
    }
}
