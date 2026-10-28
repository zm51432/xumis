package com.shine.xumis.controller;

import com.shine.xumis.util.RedirectUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 币种切换：将用户选择写入 session，刷新后全站以新币种展示价格。
 */
@Controller
public class CurrencyController {

    @GetMapping("/set-currency")
    public String setCurrency(@RequestParam String c,
                              @RequestParam(required = false) String redirect,
                              HttpSession session,
                              HttpServletRequest request) {
        session.setAttribute("CURRENCY", c.toUpperCase());
        return "redirect:" + RedirectUtils.safe(redirect, request);
    }
}
