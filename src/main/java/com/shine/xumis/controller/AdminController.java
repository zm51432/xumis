package com.shine.xumis.controller;

import com.shine.xumis.config.AdminAuthInterceptor;
import com.shine.xumis.model.AdminRole;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.model.Category;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.UserAccount;
import com.shine.xumis.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 后台：仪表盘 + 商品管理 + 订单处理 + 管理员账号管理。
 *
 * 访问控制：进入 /admin/** 必须先登录（AdminAuthInterceptor），
 * 再按角色细分——OWNER 全权限 / OPERATOR 可维护业务数据 / VIEWER 只读。
 */
@Controller
public class AdminController {

    private final CustomIdeaService ideaService;
    private final InquiryService inquiryService;
    private final OrderService orderService;
    private final SubscriptionService subscriptionService;
    private final ReviewService reviewService;
    private final ProductService productService;
    private final FileStorageService fileStorageService;
    private final AdminUserService adminUserService;
    private final UserAccountService userService;

    public AdminController(CustomIdeaService ideaService, InquiryService inquiryService,
                           OrderService orderService, SubscriptionService subscriptionService,
                           ReviewService reviewService, ProductService productService,
                           FileStorageService fileStorageService, AdminUserService adminUserService,
                           UserAccountService userService) {
        this.ideaService = ideaService;
        this.inquiryService = inquiryService;
        this.orderService = orderService;
        this.subscriptionService = subscriptionService;
        this.reviewService = reviewService;
        this.productService = productService;
        this.fileStorageService = fileStorageService;
        this.adminUserService = adminUserService;
        this.userService = userService;
    }

    /** 当前登录管理员；未登录返回 null（调用方应重定向到登录页） */
    private AdminUser me(HttpSession session) {
        return AdminAuthInterceptor.current(session);
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/admin")
    public String admin(HttpSession session, Model model) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("me", me);
        model.addAttribute("ideas", ideaService.findAll());
        model.addAttribute("inquiries", inquiryService.findAll());
        model.addAttribute("orders", orderService.findAll());
        model.addAttribute("subscriptions", subscriptionService.findAll());
        model.addAttribute("pendingReviews", reviewService.pending());
        model.addAttribute("countProducts", productService.findAll().size());
        model.addAttribute("countOrders", orderService.countAll());
        model.addAttribute("countIdeas", ideaService.countAll());
        model.addAttribute("countInquiries", inquiryService.countAll());
        model.addAttribute("countSubs", subscriptionService.countAll());
        model.addAttribute("countPendingReviews", reviewService.pendingCount());
        model.addAttribute("countPaidOrders", orderService.countByStatus("PAID"));
        model.addAttribute("orderStatuses", List.of("PENDING", "PAID", "SHIPPED", "DONE", "CANCELLED"));
        return "admin";
    }

    @PostMapping("/admin/review/approve")
    public String approveReview(HttpSession session, @RequestParam Long id) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canWrite()) {
            return "redirect:/admin?denied=1";
        }
        reviewService.approve(id);
        return "redirect:/admin";
    }

    /** 推进订单状态（运营及以上） */
    @PostMapping("/admin/order/status")
    public String updateOrderStatus(HttpSession session,
                                    @RequestParam Long id,
                                    @RequestParam String status) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canWrite()) {
            return "redirect:/admin?denied=1";
        }
        // 状态白名单：避免被塞入任意值导致前台状态徽章/i18n 取不到文案
        String s = (status == null) ? "" : status.trim().toUpperCase();
        if (!List.of("PENDING", "PAID", "SHIPPED", "DONE", "CANCELLED").contains(s)) {
            return "redirect:/admin?err=" + urlEncode("非法的订单状态：" + status);
        }
        orderService.updateStatus(id, s);
        return "redirect:/admin";
    }

    // ==================== 商品管理 ====================

    @GetMapping("/admin/products")
    public String products(HttpSession session,
                           @RequestParam(value = "id", required = false) Long id,
                           @RequestParam(value = "saved", required = false) String saved,
                           @RequestParam(value = "err", required = false) String err,
                           Model model) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("me", me);
        model.addAttribute("categories", Category.values());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("edit", id != null ? productService.findById(id) : null);
        model.addAttribute("saved", saved);
        model.addAttribute("err", err);
        return "admin-products";
    }

    /**
     * 新增 / 修改商品。图片两种维护方式：本地上传，或填写图片链接 URL。
     */
    @PostMapping("/admin/product/save")
    public String saveProduct(HttpSession session,
                              @RequestParam(value = "id", required = false) Long id,
                              @RequestParam String name,
                              @RequestParam(required = false) String nameEn,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String price,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String descriptionEn,
                              @RequestParam(required = false) String material,
                              @RequestParam(required = false) String stock,
                              @RequestParam(required = false) String imageUrl,
                              @RequestParam(value = "customizable", defaultValue = "false") boolean customizable,
                              @RequestParam(value = "featured", defaultValue = "false") boolean featured,
                              @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canWrite()) {
            return "redirect:/admin/products?err=" + urlEncode("当前角色为只读，不能修改商品");
        }
        Product p = (id != null) ? productService.findById(id) : null;
        boolean isNew = (p == null);
        if (isNew) {
            p = new Product();
        }

        p.setName(name == null || name.isBlank() ? "未命名商品" : name.trim());
        p.setNameEn(nameEn);
        p.setCategory(parseCategory(category));
        p.setPrice(parsePrice(price));
        p.setDescription(description);
        p.setDescriptionEn(descriptionEn);
        p.setMaterial(material);
        p.setStock(parseStock(stock));
        p.setCustomizable(customizable);
        p.setFeatured(featured);

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                p.setImageUrl(fileStorageService.store(imageFile));
            } catch (IllegalArgumentException ex) {
                return "redirect:/admin/products?err=" + urlEncode(ex.getMessage());
            } catch (IOException ex) {
                return "redirect:/admin/products?err=" + urlEncode("图片保存失败，请检查上传目录权限");
            }
        } else if (imageUrl != null && !imageUrl.isBlank()) {
            p.setImageUrl(imageUrl.trim());
        } else if (isNew) {
            p.setImageUrl("/img/placeholder.svg");
        }

        productService.save(p);
        return "redirect:/admin/products?saved=" + (isNew ? "created" : "updated");
    }

    @PostMapping("/admin/product/delete")
    public String deleteProduct(HttpSession session, @RequestParam Long id) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.isOwner()) {
            return "redirect:/admin/products?err=" + urlEncode("仅站长可删除商品");
        }
        productService.delete(id);
        return "redirect:/admin/products?saved=deleted";
    }

    @PostMapping("/admin/product/toggle")
    public String toggle(HttpSession session, @RequestParam Long id, @RequestParam String field) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canWrite()) {
            return "redirect:/admin/products?err=" + urlEncode("只读角色不能修改");
        }
        Product p = productService.findById(id);
        if (p != null) {
            if ("featured".equals(field)) {
                p.setFeatured(!p.isFeatured());
            } else if ("customizable".equals(field)) {
                p.setCustomizable(!p.isCustomizable());
            }
            productService.save(p);
        }
        return "redirect:/admin/products?saved=updated";
    }

    // ==================== 客户管理 ====================

    /**
     * 注册客户列表：邮箱、姓名、订单数、累计消费。
     * 只读角色也能查看；停用账号仅限站长。
     */
    @GetMapping("/admin/customers")
    public String customers(HttpSession session, Model model,
                            @RequestParam(value = "saved", required = false) String saved,
                            @RequestParam(value = "err", required = false) String err,
                            @RequestParam(value = "q", required = false) String q) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        List<UserAccount> users = userService.findAll();
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw))
                            || (u.getName() != null && u.getName().toLowerCase().contains(kw)))
                    .toList();
        }
        // 订单统计（数据量小，内存聚合即可）
        Map<String, long[]> stat = new java.util.HashMap<>();
        for (Order o : orderService.findAll()) {
            String key = o.getEmail() == null ? "" : o.getEmail().trim().toLowerCase();
            long[] s = stat.computeIfAbsent(key, k -> new long[2]);
            s[0]++;
            if (o.getTotalAmount() != null && !"CANCELLED".equalsIgnoreCase(o.getStatus())) {
                s[1] += o.getTotalAmount().movePointRight(2).longValue();
            }
        }
        model.addAttribute("me", me);
        model.addAttribute("customers", users);
        model.addAttribute("stat", stat);
        model.addAttribute("q", q);
        model.addAttribute("saved", saved);
        model.addAttribute("err", err);
        return "admin-customers";
    }

    @PostMapping("/admin/customers/enabled")
    public String toggleCustomer(HttpSession session, @RequestParam Long id, @RequestParam boolean enabled) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.isOwner()) {
            return "redirect:/admin/customers?err=" + urlEncode("仅站长可停用客户账号");
        }
        userService.setEnabled(id, enabled);
        return "redirect:/admin/customers?saved=updated";
    }

    // ==================== 管理员账号（仅站长） ====================

    @GetMapping("/admin/admins")
    public String admins(HttpSession session, Model model,
                         @RequestParam(value = "saved", required = false) String saved,
                         @RequestParam(value = "err", required = false) String err) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        model.addAttribute("me", me);
        if (!me.canManageAdmins()) {
            model.addAttribute("forbidden", true);
            return "admin-admins";
        }
        model.addAttribute("forbidden", false);
        model.addAttribute("admins", adminUserService.findAll());
        model.addAttribute("roles", AdminRole.values());
        model.addAttribute("saved", saved);
        model.addAttribute("err", err);
        return "admin-admins";
    }

    @PostMapping("/admin/admins/create")
    public String createAdmin(HttpSession session,
                              @RequestParam String username,
                              @RequestParam String password,
                              @RequestParam(required = false) String displayName,
                              @RequestParam(required = false) String role) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canManageAdmins()) {
            return "redirect:/admin/admins?err=" + urlEncode("无权操作");
        }
        if (password == null || password.length() < 8) {
            return "redirect:/admin/admins?err=" + urlEncode("密码至少 8 位");
        }
        AdminRole r = "OWNER".equalsIgnoreCase(role) ? AdminRole.OWNER
                : "VIEWER".equalsIgnoreCase(role) ? AdminRole.VIEWER : AdminRole.OPERATOR;
        boolean ok = adminUserService.create(username, password, displayName, r).isPresent();
        return "redirect:/admin/admins?saved=" + (ok ? "created" : "dup");
    }

    @PostMapping("/admin/admins/role")
    public String changeRole(HttpSession session, @RequestParam Long id, @RequestParam String role) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canManageAdmins()) {
            return "redirect:/admin/admins?err=" + urlEncode("无权操作");
        }
        // 不允许把自己降级，避免把自己锁在门外
        if (me.getId() != null && me.getId().equals(id)) {
            return "redirect:/admin/admins?err=" + urlEncode("不能修改自己的角色");
        }
        AdminRole r = "OWNER".equalsIgnoreCase(role) ? AdminRole.OWNER
                : "OPERATOR".equalsIgnoreCase(role) ? AdminRole.OPERATOR : AdminRole.VIEWER;
        adminUserService.updateRole(id, r);
        return "redirect:/admin/admins?saved=updated";
    }

    @PostMapping("/admin/admins/enabled")
    public String toggleEnabled(HttpSession session, @RequestParam Long id, @RequestParam boolean enabled) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (!me.canManageAdmins()) {
            return "redirect:/admin/admins?err=" + urlEncode("无权操作");
        }
        if (me.getId() != null && me.getId().equals(id)) {
            return "redirect:/admin/admins?err=" + urlEncode("不能停用自己");
        }
        adminUserService.setEnabled(id, enabled);
        return "redirect:/admin/admins?saved=updated";
    }

    /** 修改自己的密码（所有角色可用） */
    @PostMapping("/admin/password")
    public String changePassword(HttpSession session,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword) {
        AdminUser me = me(session);
        if (me == null) {
            return "redirect:/admin/login";
        }
        if (newPassword == null || newPassword.length() < 8) {
            return "redirect:/admin/admins?err=" + urlEncode("新密码至少 8 位");
        }
        // 应急钥匙登录（无持久化账号）不允许改密
        if (me.getId() == null || me.getId() < 0) {
            return "redirect:/admin/admins?err=" + urlEncode("应急钥匙登录不能修改密码");
        }
        AdminUser fresh = adminUserService.login(me.getUsername(), oldPassword).orElse(null);
        if (fresh == null) {
            return "redirect:/admin/admins?err=" + urlEncode("原密码不正确");
        }
        adminUserService.changePassword(me.getId(), newPassword);
        return "redirect:/admin/admins?saved=pwd";
    }

    // ==================== 工具方法 ====================

    private Category parseCategory(String v) {
        if (v != null) {
            for (Category c : Category.values()) {
                if (c.name().equalsIgnoreCase(v)) {
                    return c;
                }
            }
        }
        return Category.OTHER;
    }

    private BigDecimal parsePrice(String v) {
        if (v == null || v.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseStock(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
