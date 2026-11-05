package com.shine.xumis.config;

import com.shine.xumis.service.AnalyticsService;
import com.shine.xumis.util.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 站内埋点（P2-3）：只记录前台页面浏览，排除后台、接口、静态资源与扫描器路径。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AnalyticsFilter extends OncePerRequestFilter {

    private final AnalyticsService analytics;

    public AnalyticsFilter(AnalyticsService analytics) { this.analytics = analytics; }

    private static boolean skip(String uri) {
        if (uri == null) return true;
        if (!uri.startsWith("/")) return true;
        String[] prefixes = {"/css/", "/js/", "/img/", "/files/", "/api/", "/admin/",
                "/console-", "/erp/", "/HF/", "/apk/", "/download/", "/h2-console",
                "/actuator", "/favicon", "/manifest", "/icon.svg", "/sitemap.xml", "/robots.txt"};
        for (String p : prefixes) if (uri.startsWith(p)) return true;
        String[] suffixes = {".css", ".js", ".png", ".jpg", ".jpeg", ".webp", ".svg", ".ico", ".woff", ".woff2"};
        for (String s : suffixes) if (uri.endsWith(s)) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean track = "GET".equalsIgnoreCase(request.getMethod()) && !skip(uri);
        try {
            chain.doFilter(request, response);
        } finally {
            if (track && response.getStatus() == 200) {
                String sid = request.getRequestedSessionId();
                if (sid == null) {
                    sid = IpUtils.clientIp(request) + "|" + String.valueOf(request.getHeader("User-Agent"));
                }
                Long productId = null;
                if (uri.matches("/(en/)?product/\\d+")) {
                    try { productId = Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1)); }
                    catch (NumberFormatException ignored) {}
                }
                String path = uri.startsWith("/en/") ? uri.substring(3) : uri;
                analytics.record("VIEW", path, productId, sid);
            }
        }
    }
}
