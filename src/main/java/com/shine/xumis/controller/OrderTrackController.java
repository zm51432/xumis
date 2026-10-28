package com.shine.xumis.controller;

import com.shine.xumis.model.Order;
import com.shine.xumis.service.OrderService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单进度查询（C 端闭环）：下单后用「订单号 + 下单邮箱」即可查看状态与明细，
 * 不需要注册也能查，符合外贸站 guest checkout 的实际使用习惯。
 */
@Controller
public class OrderTrackController {

    private final OrderService orderService;

    public OrderTrackController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/order/track")
    public String form(@RequestParam(value = "no", required = false) String no,
                       HttpSession session,
                       Model model) {
        model.addAttribute("defaultNo", no);
        // 已登录客户：邮箱自动带出，不用再手打
        var user = AccountController.currentUser(session);
        if (user != null) {
            model.addAttribute("defaultEmail", user.getEmail());
        }
        return "order-track";
    }

    @PostMapping("/order/track")
    public String query(@RequestParam(required = false) String orderNo,
                        @RequestParam(required = false) String email,
                        Model model) {
        String no = (orderNo == null) ? "" : orderNo.trim();
        String mail = (email == null) ? "" : email.trim();
        model.addAttribute("defaultNo", no);
        model.addAttribute("defaultEmail", mail);

        Order o = orderService.findByOrderNo(no);
        boolean matched = o != null && o.getEmail() != null && o.getEmail().equalsIgnoreCase(mail);
        if (!matched) {
            model.addAttribute("error", true);
            return "order-track";
        }
        model.addAttribute("order", o);
        model.addAttribute("stage", stageOf(o.getStatus()));
        return "order-track";
    }

    /** 进度阶段：1 已提交 / 2 已付款 / 3 已发货 / 4 已完成（取消单用 -1 单独展示） */
    private int stageOf(String status) {
        if (status == null) return 1;
        return switch (status) {
            case "PAID" -> 2;
            case "SHIPPED" -> 3;
            case "DONE" -> 4;
            case "CANCELLED" -> -1;
            default -> 1;
        };
    }
}
