package com.shine.xumis.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头：用 Filter 而不是拦截器，这样静态资源（css/js/图片）也能覆盖到。
 * CSP 允许内联脚本/样式（模板里有 onclick 与内联 style）与 Google Fonts，
 * 其它来源一律限制为本站，降低 XSS 与点击劫持风险。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "img-src 'self' data: https:",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' data: https://fonts.gstatic.com",
            "script-src 'self' 'unsafe-inline'",
            "connect-src 'self'",
            "frame-ancestors 'self'",
            "object-src 'none'",
            "base-uri 'self'");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse res && request instanceof HttpServletRequest req) {
            String uri = req.getRequestURI();
            // 商品图 / 上传文件：允许浏览器与 CDN 缓存一天，减少重复下载、降低源站压力
            if (uri.startsWith("/img/products/") || uri.startsWith("/files/")) {
                res.setHeader("Cache-Control", "public, max-age=86400");
            }
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-Frame-Options", "SAMEORIGIN");
            res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            res.setHeader("Content-Security-Policy", CSP);
            res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        }
        chain.doFilter(request, response);
    }
}
