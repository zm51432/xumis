package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.SiteCheck;
import com.shine.xumis.model.Subscription;
import com.shine.xumis.repository.CustomIdeaRepository;
import com.shine.xumis.repository.InquiryRepository;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.RestockRepository;
import com.shine.xumis.repository.SubscriptionRepository;
import com.shine.xumis.service.HealthCheckService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.SettingsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台运营模块：订单、询盘、定制需求、到货通知、订阅、站点设置、站点自检。
 *
 * <p>设计目标是把「网站所有模块」都收进后台可查可改：
 * 商品/客户/账号在 AdminController，本控制器补齐剩余的数据模块与管理面板。</p>
 *
 * <p>权限：读取需要登录（AdminAuthInterceptor 已统一拦截 /admin/**）；
 * 写操作额外要求 {@code canWrite()}（站长/运营），只读角色只能看。</p>
 */
@Controller
public class AdminOpsController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final InquiryRepository inquiryRepository;
    private final CustomIdeaRepository ideaRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RestockRepository restockRepository;
    private final SettingsService settings;
    private final HealthCheckService health;
    private final com.shine.xumis.integration.erp.ErpSyncService erpSyncService;
    private final com.shine.xumis.service.StockReservationService reservations;

    public AdminOpsController(OrderService orderService, OrderRepository orderRepository,
                              InquiryRepository inquiryRepository, CustomIdeaRepository ideaRepository,
                              SubscriptionRepository subscriptionRepository, RestockRepository restockRepository,
                              SettingsService settings, HealthCheckService health,
                              com.shine.xumis.integration.erp.ErpSyncService erpSyncService,
                              com.shine.xumis.service.StockReservationService reservations) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.inquiryRepository = inquiryRepository;
        this.ideaRepository = ideaRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.restockRepository = restockRepository;
        this.settings = settings;
        this.health = health;
        this.erpSyncService = erpSyncService;
        this.reservations = reservations;
    }

    // ==================== 通用 ====================

    private AdminUser me(HttpSession session) {
        return AdminAuthInterceptor.current(session);
    }

    /** 读取页守卫：未登录跳登录页；已登录把 me 放进模型 */
    private boolean needLogin(HttpSession session, Model model) {
        AdminUser u = me(session);
        if (u == null) return true;
        model.addAttribute("me", u);
        return false;
    }

    /** 写操作守卫：返回 null 表示放行，否则返回应该跳转的地址 */
    private String writeGuard(HttpSession session) {
        AdminUser u = me(session);
        if (u == null) return "redirect:/admin/login";
        if (!u.canWrite()) return "redirect:/admin?denied=1";
        return null;
    }

    // ==================== 订单 ====================

    @GetMapping("/admin/orders")
    public String orders(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String q,
                         HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        List<Order> all = orderService.findAll();
        String st = (status == null) ? "" : status.trim().toUpperCase();
        String kw = (q == null) ? "" : q.trim().toLowerCase();
        List<Order> list = new ArrayList<>();
        for (Order o : all) {
            if (!st.isEmpty() && !st.equalsIgnoreCase(o.getStatus())) continue;
            if (!kw.isEmpty()) {
                String hay = ((o.getOrderNo() == null ? "" : o.getOrderNo()) + " "
                        + (o.getEmail() == null ? "" : o.getEmail()) + " "
                        + (o.getCustomerName() == null ? "" : o.getCustomerName())).toLowerCase();
                if (!hay.contains(kw)) continue;
            }
            list.add(o);
        }
        model.addAttribute("orders", list);
        model.addAttribute("status", st);
        model.addAttribute("q", kw);
        model.addAttribute("orderStatuses", List.of("PENDING", "PAID", "SHIPPED", "DONE", "CANCELLED",
                "PARTIAL_RETURN", "REFUNDED"));
        model.addAttribute("total", list.size());
        return "admin-orders";
    }

    @PostMapping("/admin/orders/delete")
    public String deleteOrder(@RequestParam Long id, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        String orderNo = orderRepository.findById(id).map(com.shine.xumis.model.Order::getOrderNo).orElse(null);
        orderRepository.deleteById(id);
        reservations.release(orderNo);          // 清理未释放的库存预占，避免幽灵占库存
        erpSyncService.deleteOrder(orderNo);   // 同步删除 ERP 销售单，避免幽灵单
        return "redirect:/admin/orders?saved=deleted";
    }

    @GetMapping("/admin/orders/export.csv")
    public void exportOrders(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"orders.csv\"");
        PrintWriter w = response.getWriter();
        w.write('\uFEFF');
        w.println("订单号,状态,客户,邮箱,电话,国家,地址,商品小计,优惠,应付金额,币种,优惠码,支付方式,物流单号,承运商,商品,下单时间");
        for (Order o : orderService.findAll()) {
            StringBuilder items = new StringBuilder();
            if (o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    if (items.length() > 0) items.append(" | ");
                    items.append(it.getProductName()).append(" x").append(it.getQuantity());
                }
            }
            w.println(String.join(",",
                    csv(o.getOrderNo()), csv(o.getStatus()), csv(o.getCustomerName()), csv(o.getEmail()),
                    csv(o.getPhone()), csv(o.getCountry()), csv(o.getAddress()),
                    o.getSubtotalAmount() == null ? (o.getTotalAmount() == null ? "" : o.getTotalAmount().toPlainString()) : o.getSubtotalAmount().toPlainString(),
                    o.getDiscountAmount() == null ? "" : o.getDiscountAmount().toPlainString(),
                    o.getTotalAmount() == null ? "" : o.getTotalAmount().toPlainString(),
                    csv(o.getCurrency()), csv(o.getCouponCode()), csv(o.getPaymentMethod()),
                    csv(o.getTrackingNo()), csv(o.getCarrier()), csv(items.toString()),
                    o.getCreatedAt() == null ? "" : o.getCreatedAt().toString()));
        }
        w.flush();
    }

    // ==================== 询盘 ====================

    @GetMapping("/admin/inquiries")
    public String inquiries(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        model.addAttribute("inquiries", inquiryRepository.findAll());
        return "admin-inquiries";
    }

    @PostMapping("/admin/inquiries/status")
    public String inquiryStatus(@RequestParam Long id, @RequestParam String status, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        inquiryRepository.findById(id).ifPresent(i -> {
            i.setStatus(status);
            inquiryRepository.save(i);
        });
        return "redirect:/admin/inquiries?saved=updated";
    }

    @PostMapping("/admin/inquiries/delete")
    public String inquiryDelete(@RequestParam Long id, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        inquiryRepository.deleteById(id);
        return "redirect:/admin/inquiries?saved=deleted";
    }

    // ==================== 定制需求 ====================

    @GetMapping("/admin/custom-ideas")
    public String ideas(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        model.addAttribute("ideas", ideaRepository.findAll());
        return "admin-custom-ideas";
    }

    @PostMapping("/admin/custom-ideas/status")
    public String ideaStatus(@RequestParam Long id, @RequestParam String status, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        ideaRepository.findById(id).ifPresent(i -> {
            i.setStatus(status);
            ideaRepository.save(i);
        });
        return "redirect:/admin/custom-ideas?saved=updated";
    }

    @PostMapping("/admin/custom-ideas/delete")
    public String ideaDelete(@RequestParam Long id, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        ideaRepository.deleteById(id);
        return "redirect:/admin/custom-ideas?saved=deleted";
    }

    // ==================== 到货通知 ====================

    @GetMapping("/admin/restock")
    public String restock(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        model.addAttribute("requests", restockRepository.findAll());
        return "admin-restock";
    }

    @PostMapping("/admin/restock/delete")
    public String restockDelete(@RequestParam Long id, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        restockRepository.deleteById(id);
        return "redirect:/admin/restock?saved=deleted";
    }

    // ==================== 订阅 ====================

    @GetMapping("/admin/subscriptions")
    public String subscriptions(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        model.addAttribute("subs", subscriptionRepository.findAll());
        return "admin-subscriptions";
    }

    @PostMapping("/admin/subscriptions/delete")
    public String subDelete(@RequestParam Long id, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        subscriptionRepository.deleteById(id);
        return "redirect:/admin/subscriptions?saved=deleted";
    }

    @GetMapping("/admin/subscriptions/export.csv")
    public void exportSubs(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"subscriptions.csv\"");
        PrintWriter w = response.getWriter();
        w.write('\uFEFF');
        w.println("邮箱,订阅时间");
        for (Subscription s : subscriptionRepository.findAll()) {
            w.println(csv(s.getEmail()) + "," + (s.getCreatedAt() == null ? "" : s.getCreatedAt()));
        }
        w.flush();
    }

    // ==================== 站点设置 ====================

    @GetMapping("/admin/settings")
    public String settingsPage(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        model.addAttribute("groups", settings.grouped());
        model.addAttribute("values", settings.map());
        return "admin-settings";
    }

    @PostMapping("/admin/settings/save")
    public String saveSettings(@RequestParam Map<String, String> form, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        settings.save(form);
        return "redirect:/admin/settings?saved=1";
    }

    @PostMapping("/admin/settings/reset")
    public String resetSettings(@RequestParam String group, HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        settings.resetGroup(group);
        return "redirect:/admin/settings?reset=1";
    }

    // ==================== 站点自检 ====================

    @GetMapping("/admin/health")
    public String healthPage(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        List<SiteCheck> last = health.lastResults();
        model.addAttribute("checks", last);
        model.addAttribute("lastRunAt", health.lastRunAt());
        model.addAttribute("healthy", !last.isEmpty() && last.stream().allMatch(SiteCheck::isOk));
        model.addAttribute("pending", last.isEmpty());
        model.addAttribute("history", health.history(30));
        return "admin-health";
    }

    @PostMapping("/admin/health/run")
    public String runHealth(HttpSession session) {
        String guard = writeGuard(session);
        if (guard != null) return guard;
        health.run();
        return "redirect:/admin/health?ran=1";
    }

    // ==================== 小工具 ====================

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    /** 给模板用的状态中文名（列表里直接显示） */
    @ModelAttribute("statusLabels")
    public Map<String, String> statusLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("PENDING", "待支付");
        m.put("PAID", "已支付");
        m.put("SHIPPED", "已发货");
        m.put("DONE", "已完成");
        m.put("CANCELLED", "已取消");
        m.put("NEW", "待处理");
        m.put("HANDLED", "已处理");
        m.put("CLOSED", "已关闭");
        m.put("PARTIAL_RETURN", "部分退货");
        m.put("REFUNDED", "已退货");
        return m;
    }
}
