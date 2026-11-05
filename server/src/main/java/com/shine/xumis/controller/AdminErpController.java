package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.model.ErpSyncEvent;
import com.shine.xumis.repository.ErpSyncEventRepository;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 后台「ERP 同步」：可观测、可重试、可对账、可补推。 */
@Controller
public class AdminErpController {

    private final ErpSyncEventRepository events;
    private final ErpSyncService erp;
    private final ProductService products;
    private final OrderService orders;
    private final ObjectMapper mapper;

    public AdminErpController(ErpSyncEventRepository events, ErpSyncService erp,
                              ProductService products, OrderService orders, ObjectMapper mapper) {
        this.events = events;
        this.erp = erp;
        this.products = products;
        this.orders = orders;
        this.mapper = mapper;
    }

    private AdminUser me(HttpSession s) { return AdminAuthInterceptor.current(s); }
    private boolean needLogin(HttpSession s, Model m) {
        AdminUser u = me(s);
        if (u == null) return true;
        m.addAttribute("me", u);
        return false;
    }
    private String guard(HttpSession s) {
        AdminUser u = me(s);
        if (u == null) return "redirect:/admin/login";
        if (!u.canWrite()) return "redirect:/admin/erp-sync?denied=1";
        return null;
    }

    @GetMapping("/admin/erp-sync")
    public String page(HttpSession session, Model model) {
        if (needLogin(session, model)) return "redirect:/admin/login";
        List<ErpSyncEvent> list = events.findTop60ByOrderByIdDesc();
        model.addAttribute("events", list);
        model.addAttribute("failCount", events.countByStatus("FAIL"));
        model.addAttribute("gaveUpCount", events.countByStatus("GAVE_UP"));
        model.addAttribute("okCount", events.countByStatus("OK"));
        model.addAttribute("productCount", products.findAll().size());
        model.addAttribute("orderCount", orders.findAll().size());
        Map<String, Object> diff = reconcileMap();
        model.addAttribute("reconcile", diff);
        model.addAttribute("reconcileJson", toJson(diff));
        return "admin-erp-sync";
    }

    @PostMapping("/admin/erp-sync/retry")
    public String retry(HttpSession s) {
        String g = guard(s); if (g != null) return g;
        int n = erp.retryFailedNow();
        return "redirect:/admin/erp-sync?saved=retry&n=" + n;
    }

    @PostMapping("/admin/erp-sync/sync-products")
    public String syncProducts(HttpSession s) {
        String g = guard(s); if (g != null) return g;
        erp.syncAllProducts(products.findAll());
        return "redirect:/admin/erp-sync?saved=products";
    }

    @PostMapping("/admin/erp-sync/sync-orders")
    public String syncOrders(HttpSession s) {
        String g = guard(s); if (g != null) return g;
        orders.findAll().forEach(erp::syncOrder);
        return "redirect:/admin/erp-sync?saved=orders";
    }

    @PostMapping("/admin/erp-sync/inventory-sync")
    public String inventorySync(HttpSession s) {
        String g = guard(s); if (g != null) return g;
        erp.inventorySync();
        return "redirect:/admin/erp-sync?saved=inventory";
    }

    @PostMapping("/admin/erp-sync/reconcile")
    public String refresh(HttpSession s) {
        String g = guard(s); if (g != null) return g;
        erp.recordReconcile(reconcileMap());
        return "redirect:/admin/erp-sync?saved=reconcile";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reconcileMap() {
        try {
            String raw = erp.reconcile();
            Map<String, Object> m = mapper.readValue(raw, Map.class);
            return m == null ? Map.of("ok", false, "error", "empty") : m;
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage() == null ? "reconcile failed" : e.getMessage());
        }
    }

    private String toJson(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }
}
