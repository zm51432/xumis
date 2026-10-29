package com.shine.xumis.config;

import com.shine.xumis.model.AdminRole;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.service.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台登录与权限拦截器：/admin/** 必须先登录（session 中存在已启用管理员）。
 *
 * 兼容策略：app.admin-allow-key=true 时，仍允许用 ?key=应急进入（视为站长），
 * 默认关闭——生产环境请保持关闭并使用账号登录。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "ADMIN_USER";

    private final AdminUserService adminUserService;
    private final String adminKey;
    private final boolean allowKey;

    public AdminAuthInterceptor(AdminUserService adminUserService,
                                @Value("${app.admin-key:admin}") String adminKey,
                                @Value("${app.admin-allow-key:false}") boolean allowKey) {
        this.adminUserService = adminUserService;
        this.adminKey = adminKey;
        this.allowKey = allowKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object o = session.getAttribute(SESSION_KEY);
            if (o instanceof AdminUser u && u.isEnabled()) {
                request.setAttribute("currentAdmin", u);
                return true;
            }
        }

        // 应急钥匙（默认关闭）
        String key = request.getParameter("key");
        // 常量时间比较，避免通过响应耗时逐字节猜测密钥
        if (allowKey && key != null && com.shine.xumis.util.Csrf.matches(adminKey, key)) {
            AdminUser owner = new AdminUser("key-admin", "", "应急钥匙", AdminRole.OWNER);
            owner.setId(-1L);
            HttpSession s = request.getSession(true);
            s.setAttribute(SESSION_KEY, owner);
            request.setAttribute("currentAdmin", owner);
            return true;
        }

        response.sendRedirect("/admin/login");
        return false;
    }

    /** 当前登录管理员（供控制器使用） */
    public static AdminUser current(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object o = session.getAttribute(SESSION_KEY);
        return (o instanceof AdminUser u) ? u : null;
    }
}
