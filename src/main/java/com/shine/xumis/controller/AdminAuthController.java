package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.service.AdminUserService;
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

    public AdminAuthController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
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
        AdminUser u = adminUserService.login(username, password).orElse(null);
        if (u == null) {
            // 不区分"用户名不存在/密码错误/已停用"，避免账号枚举
            model.addAttribute("error", true);
            model.addAttribute("lastUsername", username);
            return "admin-login";
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AdminAuthInterceptor.SESSION_KEY, u);
        return "redirect:/admin";
    }

    @GetMapping("/admin/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(AdminAuthInterceptor.SESSION_KEY);
        }
        return "redirect:/admin/login?loggedOut=1";
    }
}
