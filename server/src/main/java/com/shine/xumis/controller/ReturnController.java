package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.ReturnRequest;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.ReturnRequestRepository;
import com.shine.xumis.service.ReturnService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** 网站侧退货：客户申请 / 我的申请 / 后台审批（阶段1）。 */
@Controller
public class ReturnController {

    public record Group(String requestNo, String orderNo, String email, String status,
                        java.time.LocalDateTime createdAt, List<ReturnRequest> lines,
                        int totalQty, BigDecimal refundTotal, String reason) {}

    private final ReturnService returns;
    private final ReturnRequestRepository repo;
    private final OrderRepository orders;

    public ReturnController(ReturnService returns, ReturnRequestRepository repo, OrderRepository orders) {
        this.returns = returns;
        this.repo = repo;
        this.orders = orders;
    }

    /** 客户：申请退货表单 */
    @GetMapping("/account/returns/new")
    public String form(@RequestParam String no, HttpSession session, Model model) {
        var user = AccountController.currentUser(session);
        if (user == null) return "redirect:/login?redirect=/account/returns/new%3Fno%3D" + no;
        Order o = orders.findByOrderNo(no).orElse(null);
        if (o == null || o.getUserId() == null || !o.getUserId().equals(user.getId()))
            return "redirect:/account?returnError=notFound";
        model.addAttribute("order", o);
        model.addAttribute("existing", repo.findByOrderNoAndStatusOrderByIdAsc(no, "REQUESTED"));
        return "return-request";
    }

    /** 客户：提交申请 */
    @PostMapping("/account/returns/submit")
    public String submit(@RequestParam String no,
                         @RequestParam(required = false) String reason,
                         @RequestParam Map<String, String> form,
                         HttpSession session) {
        var user = AccountController.currentUser(session);
        if (user == null) return "redirect:/login?redirect=/account";
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (!e.getKey().startsWith("qty_")) continue;
            try {
                long pid = Long.parseLong(e.getKey().substring(4));
                int n = Integer.parseInt(e.getValue());
                if (n > 0) qty.put(pid, n);
            } catch (Exception ignored) {}
        }
        ReturnService.Result r = returns.request(no, user.getId(), qty, reason);
        return "redirect:/account?returnMsg=" + enc(r.ok() ? "submitted" : r.message());
    }

    /** 后台：申请列表 */
    @GetMapping("/admin/returns")
    public String adminList(HttpSession session, Model model) {
        AdminUser me = AdminAuthInterceptor.current(session);
        if (me == null) return "redirect:/admin/login";
        model.addAttribute("me", me);
        model.addAttribute("groups", groups(repo.findAllByOrderByCreatedAtDesc()));
        return "admin-returns";
    }

    @PostMapping("/admin/returns/approve")
    public String approve(@RequestParam String requestNo,
                          @RequestParam(required = false) String note,
                          HttpSession session) {
        if (AdminAuthInterceptor.current(session) == null) return "redirect:/admin/login";
        ReturnService.Result r = returns.approve(requestNo, "admin", note);
        return "redirect:/admin/returns?msg=" + enc(r.message());
    }

    @PostMapping("/admin/returns/reject")
    public String reject(@RequestParam String requestNo,
                         @RequestParam(required = false) String note,
                         HttpSession session) {
        if (AdminAuthInterceptor.current(session) == null) return "redirect:/admin/login";
        ReturnService.Result r = returns.reject(requestNo, "admin", note);
        return "redirect:/admin/returns?msg=" + enc(r.message());
    }

    private List<Group> groups(List<ReturnRequest> all) {
        Map<String, List<ReturnRequest>> by = new LinkedHashMap<>();
        for (ReturnRequest r : all) by.computeIfAbsent(r.getRequestNo(), k -> new ArrayList<>()).add(r);
        List<Group> out = new ArrayList<>();
        for (Map.Entry<String, List<ReturnRequest>> e : by.entrySet()) {
            List<ReturnRequest> lines = e.getValue();
            ReturnRequest f = lines.get(0);
            int q = lines.stream().mapToInt(x -> x.getQuantity() == null ? 0 : x.getQuantity()).sum();
            BigDecimal amt = BigDecimal.ZERO;
            for (ReturnRequest x : lines) amt = amt.add(x.getRefundAmount() == null ? BigDecimal.ZERO : x.getRefundAmount());
            String email = orders.findByOrderNo(f.getOrderNo()).map(Order::getEmail).orElse("");
            out.add(new Group(e.getKey(), f.getOrderNo(), email, f.getStatus(), f.getCreatedAt(),
                              lines, q, amt, f.getReason()));
        }
        return out;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
