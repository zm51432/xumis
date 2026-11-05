package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.*;
import com.shine.xumis.repository.OrderRepository;
import com.shine.xumis.repository.TrackingEventRepository;
import com.shine.xumis.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 后台新增模块：优惠券、内容页、分类、访问分析、物流轨迹（P1/P2）。 */
@Controller
public class AdminExtrasController {

    private final CouponService coupons;
    private final ContentPageService pages;
    private final CatalogExtraService catalog;
    private final AnalyticsService analytics;
    private final OrderRepository orderRepository;
    private final TrackingEventRepository trackingRepo;
    private final OrderMailService orderMail;
    private final StockReservationService reservations;
    private final com.shine.xumis.integration.erp.ErpSyncService erpSyncService;

    public AdminExtrasController(CouponService coupons, ContentPageService pages,
                                 CatalogExtraService catalog, AnalyticsService analytics,
                                 OrderRepository orderRepository, TrackingEventRepository trackingRepo,
                                 OrderMailService orderMail, StockReservationService reservations,
                                 com.shine.xumis.integration.erp.ErpSyncService erpSyncService) {
        this.coupons = coupons;
        this.pages = pages;
        this.catalog = catalog;
        this.analytics = analytics;
        this.orderRepository = orderRepository;
        this.trackingRepo = trackingRepo;
        this.orderMail = orderMail;
        this.reservations = reservations;
        this.erpSyncService = erpSyncService;
    }

    private AdminUser me(HttpSession s) { return AdminAuthInterceptor.current(s); }
    private boolean needLogin(HttpSession s, Model m) {
        AdminUser u = me(s);
        if (u == null) return true;
        m.addAttribute("me", u);
        return false;
    }
    private String guard(HttpSession s, String back) {
        AdminUser u = me(s);
        if (u == null) return "redirect:/admin/login";
        if (!u.canWrite()) return "redirect:" + back + "?denied=1";
        return null;
    }
    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
    private static BigDecimal dec(String s) {
        try { return new BigDecimal(s == null ? "0" : s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
    private static Integer integer(String s, int def) {
        try { return Integer.parseInt(s == null ? "" : s.trim()); }
        catch (Exception e) { return def; }
    }
    private static LocalDateTime dateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s.trim() + (s.trim().length() == 10 ? "T00:00:00" : ":00")); }
        catch (Exception e) {
            try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); }
            catch (Exception e2) { return null; }
        }
    }

    // ============ 优惠券 ============

    @GetMapping("/admin/coupons")
    public String couponPage(@RequestParam(required = false) Long id, HttpSession s, Model m) {
        if (needLogin(s, m)) return "redirect:/admin/login";
        m.addAttribute("coupons", coupons.findAll());
        m.addAttribute("edit", coupons.findById(id));
        m.addAttribute("heldReservations", reservations.heldCount());
        return "admin-coupons";
    }

    @PostMapping("/admin/coupons/save")
    public String couponSave(@RequestParam(required = false) Long id,
                             @RequestParam String code,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false) String value,
                             @RequestParam(required = false) String minSubtotal,
                             @RequestParam(required = false) String maxDiscount,
                             @RequestParam(required = false) String startsAt,
                             @RequestParam(required = false) String endsAt,
                             @RequestParam(required = false) String usageLimit,
                             @RequestParam(required = false) String enabled,
                             @RequestParam(required = false) String note,
                             HttpSession s) {
        String g = guard(s, "/admin/coupons");
        if (g != null) return g;
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z0-9_-]{2,60}")) return "redirect:/admin/coupons?err=" + enc("优惠码需 2-60 位字母数字");
        Coupon existing = coupons.findAll().stream()
                .filter(x -> x.getCode().equalsIgnoreCase(c) && !Objects.equals(x.getId(), id))
                .findFirst().orElse(null);
        if (existing != null) return "redirect:/admin/coupons?err=" + enc("优惠码已存在");
        Coupon cp = id == null ? new Coupon() : (coupons.findById(id) == null ? new Coupon() : coupons.findById(id));
        cp.setCode(c);
        cp.setType(type == null ? "FIXED" : type.trim().toUpperCase(Locale.ROOT));
        cp.setValue(dec(value));
        cp.setMinSubtotal(dec(minSubtotal));
        cp.setMaxDiscount(dec(maxDiscount));
        cp.setStartsAt(dateTime(startsAt));
        cp.setEndsAt(dateTime(endsAt));
        cp.setUsageLimit(integer(usageLimit, 0));
        cp.setEnabled("on".equalsIgnoreCase(enabled) || "true".equalsIgnoreCase(enabled) || "1".equals(enabled));
        cp.setNote(note);
        if (cp.getUsedCount() == null) cp.setUsedCount(0);
        coupons.save(cp);
        return "redirect:/admin/coupons?saved=1";
    }

    @PostMapping("/admin/coupons/delete")
    public String couponDelete(@RequestParam Long id, HttpSession s) {
        String g = guard(s, "/admin/coupons");
        if (g != null) return g;
        coupons.delete(id);
        return "redirect:/admin/coupons?saved=deleted";
    }

    // ============ 内容页 ============

    @GetMapping("/admin/pages")
    public String pagesPage(@RequestParam(required = false) String slug, HttpSession s, Model m) {
        if (needLogin(s, m)) return "redirect:/admin/login";
        m.addAttribute("pages", pages.all());
        m.addAttribute("edit", pages.find(slug));
        return "admin-pages";
    }

    @PostMapping("/admin/pages/save")
    public String pageSave(@RequestParam String slug,
                           @RequestParam(required = false) String titleZh,
                           @RequestParam(required = false) String titleEn,
                           @RequestParam(required = false) String bodyZh,
                           @RequestParam(required = false) String bodyEn,
                           @RequestParam(required = false) String sortOrder,
                           @RequestParam(required = false) String published,
                           HttpSession s) {
        String g = guard(s, "/admin/pages");
        if (g != null) return g;
        String key = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z0-9_-]{2,60}")) return "redirect:/admin/pages?err=" + enc("路径需 2-60 位小写字母数字");
        ContentPage p = pages.find(key);
        if (p == null) { p = new ContentPage(); p.setSlug(key); }
        p.setTitleZh(titleZh); p.setTitleEn(titleEn);
        p.setBodyZh(bodyZh); p.setBodyEn(bodyEn);
        p.setSortOrder(integer(sortOrder, 99));
        p.setPublished("on".equalsIgnoreCase(published) || "true".equalsIgnoreCase(published) || "1".equals(published));
        pages.save(p);
        return "redirect:/admin/pages?saved=1";
    }

    @PostMapping("/admin/pages/delete")
    public String pageDelete(@RequestParam String slug, HttpSession s) {
        String g = guard(s, "/admin/pages");
        if (g != null) return g;
        pages.find(slug);
        // 只隐藏，不物理删除，避免外链 404
        ContentPage p = pages.find(slug);
        if (p != null) { p.setPublished(false); pages.save(p); }
        return "redirect:/admin/pages?saved=hidden";
    }

    // ============ 分类 ============

    @GetMapping("/admin/categories")
    public String categoriesPage(HttpSession s, Model m) {
        if (needLogin(s, m)) return "redirect:/admin/login";
        m.addAttribute("cats", catalog.allCategories());
        return "admin-categories";
    }

    @PostMapping("/admin/categories/save")
    public String categorySave(@RequestParam String code,
                               @RequestParam(required = false) String nameZh,
                               @RequestParam(required = false) String nameEn,
                               @RequestParam(required = false) String sortOrder,
                               @RequestParam(required = false) String enabled,
                               HttpSession s) {
        String g = guard(s, "/admin/categories");
        if (g != null) return g;
        boolean on = "on".equalsIgnoreCase(enabled) || "true".equalsIgnoreCase(enabled) || "1".equals(enabled);
        catalog.saveCategory(code == null ? "" : code.trim().toUpperCase(Locale.ROOT),
                nameZh, nameEn, integer(sortOrder, 99), on);
        return "redirect:/admin/categories?saved=1";
    }

    @PostMapping("/admin/categories/delete")
    public String categoryDelete(@RequestParam String code, HttpSession s) {
        String g = guard(s, "/admin/categories");
        if (g != null) return g;
        boolean ok = catalog.deleteCategory(code);
        return "redirect:/admin/categories?saved=" + (ok ? "deleted" : "builtin");
    }

    /** 运维手动触发一次过期预占释放（定时任务每 30 秒自动跑，这里是兜底按钮） */
    @PostMapping("/admin/reservations/run")
    public String runReservations(HttpSession s) {
        String g = guard(s, "/admin/coupons");
        if (g != null) return g;
        reservations.releaseExpired();
        return "redirect:/admin/coupons?saved=released";
    }

    // ============ 访问分析 ============

    @GetMapping("/admin/analytics")
    public String analyticsPage(@RequestParam(defaultValue = "7") int days, HttpSession s, Model m) {
        if (needLogin(s, m)) return "redirect:/admin/login";
        m.addAttribute("report", analytics.report(days));
        m.addAttribute("days", days);
        return "admin-analytics";
    }

    // ============ 物流单号与轨迹 ============

    @PostMapping("/admin/orders/tracking")
    public String trackingSave(@RequestParam Long id,
                               @RequestParam(required = false) String trackingNo,
                               @RequestParam(required = false) String carrier,
                               @RequestParam(required = false) String trackingUrl,
                               @RequestParam(required = false) String eventLocation,
                               @RequestParam(required = false) String eventDescription,
                               @RequestParam(required = false) String markShipped,
                               HttpSession s) {
        String g = guard(s, "/admin/orders");
        if (g != null) return g;
        Order o = id == null ? null : orderRepository.findById(id).orElse(null);
        if (o == null) return "redirect:/admin/orders?err=" + enc("订单不存在");
        o.setTrackingNo(trackingNo == null ? "" : trackingNo.trim());
        o.setCarrier(carrier == null ? "" : carrier.trim());
        o.setTrackingUrl(trackingUrl == null ? "" : trackingUrl.trim());
        if (o.getShippedAt() == null) o.setShippedAt(LocalDateTime.now());
        boolean statusChanged = false;
        if ("on".equalsIgnoreCase(markShipped) || "true".equalsIgnoreCase(markShipped) || "1".equals(markShipped)) {
            if (!"SHIPPED".equals(o.getStatus()) && !"DONE".equals(o.getStatus())) {
                o.setStatus("SHIPPED");
                statusChanged = true;
            }
        }
        orderRepository.save(o);
        erpSyncService.syncOrder(o);   // 物流 / 发货状态同步 ERP
        if (eventDescription != null && !eventDescription.isBlank()) {
            TrackingEvent ev = new TrackingEvent();
            ev.setOrderNo(o.getOrderNo());
            ev.setEventTime(LocalDateTime.now());
            ev.setLocation(eventLocation);
            ev.setDescription(eventDescription.trim());
            trackingRepo.save(ev);
        } else if (trackingNo != null && !trackingNo.isBlank()) {
            TrackingEvent ev = new TrackingEvent();
            ev.setOrderNo(o.getOrderNo());
            ev.setDescription("包裹已交运，运单号 " + trackingNo.trim());
            ev.setLocation(eventLocation);
            trackingRepo.save(ev);
        }
        if (statusChanged) {
            try { orderMail.statusChanged(o); } catch (Exception ignored) {}
        }
        return "redirect:/admin/orders?saved=tracking";
    }

    @PostMapping("/admin/orders/tracking/event")
    public String trackingEvent(@RequestParam Long id,
                                @RequestParam String description,
                                @RequestParam(required = false) String location,
                                HttpSession s) {
        String g = guard(s, "/admin/orders");
        if (g != null) return g;
        Order o = id == null ? null : orderRepository.findById(id).orElse(null);
        if (o != null && description != null && !description.isBlank()) {
            TrackingEvent ev = new TrackingEvent();
            ev.setOrderNo(o.getOrderNo());
            ev.setDescription(description.trim());
            ev.setLocation(location);
            trackingRepo.save(ev);
        }
        return "redirect:/admin/orders?saved=event";
    }
}
