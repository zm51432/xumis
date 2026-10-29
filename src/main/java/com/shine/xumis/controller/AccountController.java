package com.shine.xumis.controller;

import com.shine.xumis.model.Order;
import com.shine.xumis.model.UserAccount;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.LoginAttemptService;
import com.shine.xumis.service.UserAccountService;
import com.shine.xumis.util.RedirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * C 端账号：注册 / 登录 / 我的账户（资料 + 我的订单）。
 * 登录后下单会自动带出收货信息，订单归属账号，可随时回看。
 */
@Controller
public class AccountController {

    private final UserAccountService userService;
    private final OrderService orderService;
    private final LoginAttemptService attemptService;

    public AccountController(UserAccountService userService, OrderService orderService,
                             LoginAttemptService attemptService) {
        this.userService = userService;
        this.orderService = orderService;
        this.attemptService = attemptService;
    }

    static UserAccount currentUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object o = session.getAttribute(UserAccountService.SESSION_KEY);
        return (o instanceof UserAccount u) ? u : null;
    }

    // ===== 注册 =====

    @GetMapping("/register")
    public String registerPage(HttpSession session,
                               @RequestParam(value = "redirect", required = false) String redirect,
                               Model model) {
        if (currentUser(session) != null) {
            return "redirect:/account";
        }
        model.addAttribute("redirectTo", redirect);
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam(required = false) String email,
                           @RequestParam(required = false) String password,
                           @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                           @RequestParam(required = false) String name,
                           @RequestParam(value = "redirect", required = false) String redirect,
                           HttpServletRequest request,
                           Model model) {
        if (email == null || !email.contains("@")) {
            model.addAttribute("error", "badEmail");
            return "register";
        }
        if (password == null || password.length() < 8) {
            model.addAttribute("error", "weakPassword");
            model.addAttribute("email", email);
            model.addAttribute("name", name);
            return "register";
        }
        // 二次校验：前端校验可被绕过，服务端必须自己确认两次输入一致
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            model.addAttribute("error", "pwdMismatch");
            model.addAttribute("email", email);
            model.addAttribute("name", name);
            return "register";
        }
        var created = userService.register(email, password, name);
        if (created.isEmpty()) {
            model.addAttribute("error", "dupEmail");
            model.addAttribute("email", email);
            model.addAttribute("name", name);
            return "register";
        }
        // 注册成功即登录：更换 Session ID，避免会话固定
        request.changeSessionId();
        HttpSession s = request.getSession(true);
        s.setAttribute(UserAccountService.SESSION_KEY, created.get());
        s.removeAttribute(com.shine.xumis.util.Csrf.SESSION_ATTR);
        String back = (redirect != null && !redirect.isBlank()) ? RedirectUtils.safe(redirect, request) : "/account";
        return "redirect:" + back;
    }

    // ===== 登录 =====

    @GetMapping("/login")
    public String loginPage(HttpSession session,
                            @RequestParam(value = "redirect", required = false) String redirect,
                            Model model) {
        if (currentUser(session) != null) {
            return "redirect:/account";
        }
        model.addAttribute("redirectTo", redirect);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(required = false) String email,
                        @RequestParam(required = false) String password,
                        @RequestParam(value = "redirect", required = false) String redirect,
                        HttpServletRequest request,
                        Model model) {
        String key = "user:" + ((email == null) ? "" : email.trim().toLowerCase());
        if (attemptService.isLocked(key)) {
            model.addAttribute("error", true);
            model.addAttribute("lockedSeconds", attemptService.remainingSeconds(key));
            model.addAttribute("email", email);
            model.addAttribute("redirectTo", redirect);
            return "login";
        }

        var u = userService.login(email, password);
        if (u.isEmpty()) {
            attemptService.recordFailure(key);
            model.addAttribute("error", true);
            model.addAttribute("email", email);
            model.addAttribute("redirectTo", redirect);
            return "login";
        }
        attemptService.recordSuccess(key);

        // 防会话固定：登录后更换 Session ID
        request.changeSessionId();
        HttpSession session = request.getSession(true);
        session.setAttribute(UserAccountService.SESSION_KEY, u.get());
        session.removeAttribute(com.shine.xumis.util.Csrf.SESSION_ATTR);
        String back = (redirect != null && !redirect.isBlank()) ? RedirectUtils.safe(redirect, request) : "/account";
        return "redirect:" + back;
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/";
    }

    // ===== 我的账户 =====

    @GetMapping("/account")
    public String account(HttpSession session, Model model) {
        UserAccount me = currentUser(session);
        if (me == null) {
            return "redirect:/login?redirect=/account";
        }
        List<Order> orders = orderService.findMyOrders(me.getId(), me.getEmail());
        model.addAttribute("me", me);
        model.addAttribute("orders", orders);
        return "account";
    }

    @PostMapping("/account/profile")
    public String updateProfile(HttpSession session,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) String country,
                                @RequestParam(required = false) String address) {
        UserAccount me = currentUser(session);
        if (me == null) {
            return "redirect:/login?redirect=/account";
        }
        userService.updateProfile(me.getId(), name, phone, country, address);
        // 同步 session 中的最新资料
        userService.findByEmail(me.getEmail())
                .ifPresent(u -> session.setAttribute(UserAccountService.SESSION_KEY, u));
        return "redirect:/account?saved=profile";
    }

    @PostMapping("/account/password")
    public String changePassword(HttpSession session,
                                 @RequestParam(required = false) String oldPassword,
                                 @RequestParam(required = false) String newPassword) {
        UserAccount me = currentUser(session);
        if (me == null) {
            return "redirect:/login?redirect=/account";
        }
        boolean ok = userService.changePassword(me.getId(), oldPassword, newPassword);
        return "redirect:/account?saved=" + (ok ? "pwd" : "pwdError");
    }
}
