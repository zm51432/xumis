package com.shine.xumis.controller;

import com.shine.xumis.model.ContentPage;
import com.shine.xumis.model.Subscription;
import com.shine.xumis.service.ContentPageService;
import com.shine.xumis.service.SubscriptionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

/** 内容页（P2-5）与邮件订阅退订（P1-5）。 */
@Controller
public class ContentController {

    private final ContentPageService pages;
    private final SubscriptionService subscriptions;

    public ContentController(ContentPageService pages, SubscriptionService subscriptions) {
        this.pages = pages;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/p/{slug}")
    public String page(@PathVariable String slug, Model model, HttpServletResponse response) throws IOException {
        ContentPage p = pages.find(slug);
        if (p == null || !p.isPublished()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        model.addAttribute("page", p);
        model.addAttribute("pageTitle", p.getTitleZh());
        model.addAttribute("pageTitleEn", p.getTitleEn());
        return "content";
    }

    /** 邮件里的退订链接：先展示确认页，避免邮件客户端预取链接导致误退订 */
    @GetMapping("/unsubscribe")
    public String unsubscribeForm(@RequestParam(required = false) String token, Model model) {
        Subscription s = subscriptions.findByToken(token);
        model.addAttribute("sub", s);
        model.addAttribute("token", token);
        return "unsubscribe";
    }

    @PostMapping("/unsubscribe")
    public String unsubscribe(@RequestParam(required = false) String token, Model model) {
        boolean ok = subscriptions.unsubscribe(token);
        model.addAttribute("done", ok);
        model.addAttribute("token", token);
        return "unsubscribe";
    }
}
