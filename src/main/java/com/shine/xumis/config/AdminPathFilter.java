package com.shine.xumis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 后台隐藏入口。
 *
 * <p>默认后台地址是 {@code /admin}，任何猜到地址的人都能看到登录页。
 * 本过滤器允许通过配置项 {@code app.admin-path}（环境变量 ADMIN_PATH）把后台
 * 换成一个不易猜到的地址（例如 {@code /console-x7q9}）：</p>
 * <ul>
 *   <li>访问别名地址 → 内部转发到真实的 {@code /admin/**}，浏览器地址栏始终是别名；</li>
 *   <li>控制器里 {@code redirect:/admin/...} 的跳转地址会被反向改写成别名，不会泄漏真实路径；</li>
 *   <li>外部直接访问 {@code /admin/**} 返回 404，真实路径对外不可达；</li>
 *   <li>未配置 ADMIN_PATH 时本过滤器完全不生效（行为与原来一致）。</li>
 * </ul>
 *
 * <p>页面上的后台链接统一使用 {@code adminBase} 模型变量（见 GlobalModelAdvice），
 * 因此无论是否启用别名，后台内部的链接与表单都能正确指向当前入口。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminPathFilter extends OncePerRequestFilter {

    /** 真实后台路径（控制器映射） */
    private static final String REAL = "/admin";

    private final String alias;

    public AdminPathFilter(@Value("${app.admin-path:}") String adminPath) {
        String p = (adminPath == null) ? "" : adminPath.trim();
        if (p.isEmpty() || p.equals(REAL) || p.equals("admin")) {
            this.alias = "";              // 未启用：保持默认地址
        } else {
            this.alias = p.startsWith("/") ? p : "/" + p;
        }
    }

    /** 供模板 / 拦截器使用的后台基址 */
    public String aliasOrReal() {
        return alias.isEmpty() ? REAL : alias;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (alias.isEmpty() || request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            chain.doFilter(request, response);
            return;
        }

        String path = pathWithinApp(request);

        if (path.equals(alias) || path.startsWith(alias + "/")) {
            String target = REAL + path.substring(alias.length());
            request.getRequestDispatcher(target)
                    .forward(new RewrittenRequest(request, target),
                             new RedirectRewritingResponse(response, alias));
            return;
        }

        // 隐藏真实后台路径：外部直接访问 /admin/** 一律 404
        if (path.equals(REAL) || path.startsWith(REAL + "/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        chain.doFilter(request, response);
    }

    private static String pathWithinApp(HttpServletRequest request) {
        String ctx = request.getContextPath();
        if ("/".equals(ctx)) {
            ctx = "";
        }
        String uri = request.getRequestURI();
        return uri.length() > ctx.length() ? uri.substring(ctx.length()) : uri;
    }

    /** 让 Spring MVC 在转发后仍按 /admin/** 匹配到后台控制器 */
    private static class RewrittenRequest extends HttpServletRequestWrapper {
        private final String target;

        RewrittenRequest(HttpServletRequest request, String target) {
            super(request);
            this.target = target;
        }

        @Override
        public String getRequestURI() {
            return target;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest req = (HttpServletRequest) getRequest();
            String scheme = req.getScheme();
            String host = req.getServerName();
            int port = req.getServerPort();
            StringBuffer url = new StringBuffer(scheme).append("://").append(host);
            if (port > 0 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
                url.append(':').append(port);
            }
            return url.append(target);
        }

        @Override
        public String getServletPath() {
            return target;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }

    /** 把重定向地址里的真实 /admin 前缀改回别名，避免路径泄漏 */
    private static class RedirectRewritingResponse extends HttpServletResponseWrapper {
        private final String alias;

        RedirectRewritingResponse(HttpServletResponse response, String alias) {
            super(response);
            this.alias = alias;
        }

        private String rewrite(String location) {
            if (location == null) {
                return null;
            }
            if (location.equals(REAL) || location.startsWith(REAL + "/")
                    || location.startsWith(REAL + "?")) {
                return alias + location.substring(REAL.length());
            }
            return location;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(rewrite(location));
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, "Location".equalsIgnoreCase(name) ? rewrite(value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, "Location".equalsIgnoreCase(name) ? rewrite(value) : value);
        }
    }

}
