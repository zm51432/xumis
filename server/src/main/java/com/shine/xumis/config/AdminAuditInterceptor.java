package com.shine.xumis.config;

import com.shine.xumis.model.AdminAuditLog;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.repository.AdminAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台写操作审计（阶段4）：只记录非 GET 的 /admin/** 请求。
 * 密码、CSRF 令牌等敏感字段一律不落库，只保留参数名与长度。
 */
@Component
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final java.util.Set<String> SECRET =
            java.util.Set.of("password", "newpassword", "oldpassword", "_csrf", "key", "token");

    private final AdminAuditLogRepository repo;

    public AdminAuditInterceptor(AdminAuditLogRepository repo) { this.repo = repo; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String m = request.getMethod();
        if ("GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m) || "OPTIONS".equalsIgnoreCase(m)) return true;
        try {
            Object o = request.getAttribute("currentAdmin");
            if (o == null) o = AdminAuthInterceptor.current(request.getSession(false));
            AdminUser u = (o instanceof AdminUser a) ? a : null;

            Map<String, String[]> params = request.getParameterMap();
            StringBuilder d = new StringBuilder();
            for (Map.Entry<String, String[]> e : new LinkedHashMap<>(params).entrySet()) {
                String k = e.getKey();
                if (SECRET.contains(k.toLowerCase())) { d.append(k).append("=***;"); continue; }
                String v = e.getValue().length > 0 ? String.valueOf(e.getValue()[0]) : "";
                if (v.length() > 80) v = v.substring(0, 80) + "…";
                d.append(k).append("=").append(v).append(";");
            }
            if (d.length() > 480) d.setLength(480);

            AdminAuditLog a = new AdminAuditLog();
            a.setUsername(u == null ? "anonymous" : u.getUsername());
            a.setRole(u == null || u.getRole() == null ? "" : u.getRole().name());
            a.setMethod(m);
            a.setPath(request.getRequestURI());
            a.setDetail(d.toString());
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
            a.setIp(ip == null ? "" : ip.substring(0, Math.min(64, ip.length())));
            a.setCreatedAt(LocalDateTime.now());
            repo.save(a);
        } catch (Exception ignored) {
            // 审计失败绝不影响业务操作
        }
        return true;
    }
}
