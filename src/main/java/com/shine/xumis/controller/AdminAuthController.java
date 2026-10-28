package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.service.AdminUserService;
import com.shine.xumis.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 后台账号登录 / 登出。登录后管理员信息写入 session，由 AdminAuthInterceptor 统一校验。
 */
@Controller
public class AdminAuthController {

    private final AdminUserService adminUserService;
    private final LoginAttemptService attemptService;

    public AdminAuthController(AdminUserService adminUserService, LoginAttemptService attemptService) {
        this.adminUserService = adminUserService;
        this.attemptService = attemptService;
    }

    @GetMapping("/admin/login")
    public String loginPage(HttpSession session, Model model) {
        AdminUser me = AdminAuthInterceptor.current(session);
        if (me != null && me.isEnabled()) {
            return "redirect:/admin";
        }
        model.addAttribute("denied", false);
        return "admin-login";
    }

    @PostMapping("/admin/login")
    public String login(@RequestParam(required = false) String username,
                        @RequestParam(required = false) String password,
                        HttpServletRequest request,
                        Model model) {
        String key = "admin:" + ((username == null) ? "" : username.trim().toLowerCase());
        // 口令爆破防护：连续失败 5 次锁定 10 分钟
        if (attemptService.isLocked(key)) {
            model.addAttribute("error", true);
            model.addAttribute("lockedSeconds", attemptService.remainingSeconds(key));
            model.addAttribute("lastUsername", username);
            return "admin-login";
        }

        AdminUser u = adminUserService.login(username, password).orElse(null);
        if (u == null) {
            attemptService.recordFailure(key);
            // 不区分"用户名不存在/密码错误/已停用"，避免账号枚举
            model.addAttribute("error", true);
            model.addAttribute("lastUsername", username);
            return "admin-login";
        }
        attemptService.recordSuccess(key);

        // 防会话固定：登录后更换 Session ID
        HttpSession old = request.getSession(false);
        if (old != null) {
            String locale = (String) old.getAttribute("SHINE_LOCALE_KEEP");
            if (locale != null) {
                request.setAttribute("SHINE_LOCALE_KEEP", locale);
            }
        }
        request.changeSessionId();
        HttpSession session = request.getSession(true);
        session.setAttribute(AdminAuthInterceptor.SESSION_KEY, u);
        // 新会话重新生成 CSRF 令牌
        session.removeAttribute(com.shine.xumis.util.Csrf.SESSION_ATTR);
        return "redirect:/admin";
    }

    @PostMapping("/admin/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/admin/login?loggedOut=1";
    }
}
