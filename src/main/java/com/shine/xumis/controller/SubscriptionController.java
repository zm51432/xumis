package com.shine.xumis.controller;

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

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/subscribe")
    public String subscribe(@RequestParam String email,
                            @RequestParam(required = false) String redirect,
                            HttpServletRequest request) {
        subscriptionService.subscribe(email);
        String back = RedirectUtils.safe(redirect, request);
        return "redirect:" + back + (back.contains("?") ? "&" : "?") + "subscribed=1";
    }
}
