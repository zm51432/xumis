package com.shine.xumis.controller;

import com.shine.xumis.service.RateLimitService;
import com.shine.xumis.service.SubscriptionService;
import com.shine.xumis.util.RedirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Newsletter 邮件订阅：可来自前台页脚或后台，缺省返回来源页。
 */
@Controller
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RateLimitService rateLimitService;

    public SubscriptionController(SubscriptionService subscriptionService, RateLimitService rateLimitService) {
        this.subscriptionService = subscriptionService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/subscribe")
    public String subscribe(@RequestParam String email,
                            @RequestParam(required = false) String redirect,
                            HttpServletRequest request) {
        // 防刷：同一 IP 10 分钟内最多 5 次订阅提交；邮箱格式也做一次服务端校验
        String key = "subscribe:" + ReviewController.clientIp(request);
        boolean badEmail = email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
        boolean tooFast = !rateLimitService.allow(key, 5, 10 * 60 * 1000L);
        if (badEmail || tooFast) {
            String back = RedirectUtils.safe(redirect, request);
            String flag = badEmail ? "subBad=1" : "subRate=" + rateLimitService.retryAfterSeconds(key, 5, 10 * 60 * 1000L);
            return "redirect:" + back + (back.contains("?") ? "&" : "?") + flag;
        }
        subscriptionService.subscribe(email);
        String back = RedirectUtils.safe(redirect, request);
        return "redirect:" + back + (back.contains("?") ? "&" : "?") + "subscribed=1";
    }
}
