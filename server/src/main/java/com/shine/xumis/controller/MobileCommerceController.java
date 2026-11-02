package com.shine.xumis.controller;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.UserAccount;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.LoginAttemptService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.UserAccountService;
import com.shine.xumis.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 手机 App 原生交易接口（购物车 / 下单 / 我的账户 / 订单）。
 *
 * 与站点共用同一份 HttpSession（JSESSIONID），因此 App 内原生下单与网页端是同一个购物车；
 * 走 /api/m 前缀，CsrfInterceptor 对 /api/** 放行（接口非浏览器表单，走会话 Cookie 即可）。
 *
 * 所有响应均为 JSON，写操作返回 { ok, ... }，失败时返回 { ok:false, error }。
 */
@RestController
@RequestMapping("/api/m")
public class MobileCommerceController {

    private static final String DEFAULT_IMG = "/img/products/p01_necklace_star_heart.png";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CartService cartService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ErpSyncService erpSyncService;
    private final UserAccountService userService;
    private final LoginAttemptService attemptService;

    public MobileCommerceController(CartService cartService, ProductService productService,
                                    OrderService orderService, ErpSyncService erpSyncService,
                                    UserAccountService userService, LoginAttemptService attemptService) {
        this.cartService = cartService;
        this.productService = productService;
        this.orderService = orderService;
        this.erpSyncService = erpSyncService;
        this.userService = userService;
        this.attemptService = attemptService;
    }

    // ==================== 购物车 ====================

    @GetMapping("/cart")
    public ResponseEntity<?> cart(@RequestHeader(value = "Accept-Language", required = false) String lang,
                                  HttpSession session) {
        cartService.pruneInvalid(session);
        boolean en = lang != null && lang.toLowerCase().contains("en");
        Map<Long, Integer> cart = cartService.getCart(session);
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : cart.entrySet()) {
            Product p = productService.findById(e.getKey());
            if (p == null) continue;
            int qty = Math.max(1, e.getValue());
            BigDecimal price = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(lineTotal);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", en ? p.getNameEn() : p.getName());
            m.put("nameEn", p.getNameEn());
            m.put("price", price);
            m.put("imageUrl", img(p));
            m.put("stock", p.getStock());
            m.put("qty", qty);
            m.put("lineTotal", lineTotal);
            lines.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lines", lines);
        body.put("count", cartService.count(session));
        body.put("total", total);
        body.put("isEmpty", lines.isEmpty());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/cart/add")
    public ResponseEntity<?> add(@RequestParam Long productId,
                                 @RequestParam(defaultValue = "1") int quantity,
                                 HttpSession session) {
        Product p = productService.findById(productId);
        if (p == null) return err("商品不存在");
        if (p.getStock() != null && p.getStock() <= 0) return err("该商品暂时缺货");
        int q = Math.max(1, Math.min(quantity, 99));
        int added = cartService.add(session, productId, q);
        if (added <= 0) return err("库存不足");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("count", cartService.count(session));
        body.put("qty", added);
        body.put("name", p.getName());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/cart/update")
    public ResponseEntity<?> update(@RequestParam Long productId,
                                    @RequestParam int quantity,
                                    HttpSession session) {
        cartService.setQty(session, productId, quantity);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("count", cartService.count(session));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/cart/remove")
    public ResponseEntity<?> remove(@RequestParam Long productId, HttpSession session) {
        cartService.remove(session, productId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("count", cartService.count(session));
        return ResponseEntity.ok(body);
    }

    // ==================== 下单 ====================

    @PostMapping("/orders")
    public ResponseEntity<?> place(@RequestParam String customerName,
                                   @RequestParam String email,
                                   @RequestParam(required = false) String phone,
                                   @RequestParam(required = false) String country,
                                   @RequestParam(required = false) String address,
                                   @RequestParam(required = false) String note,
                                   @RequestParam(defaultValue = "USD") String currency,
                                   @RequestParam(required = false) String paymentMethod,
                                   HttpSession session) {
        if (customerName == null || customerName.isBlank()) return err("请填写收货人姓名");
        if (email == null || !email.contains("@")) return err("请填写正确的邮箱");
        cartService.pruneInvalid(session);
        if (cartService.isEmpty(session)) return err("购物车是空的");

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setCustomerName(customerName.trim());
        order.setEmail(email.trim());
        order.setPhone(phone);
        order.setCountry(country);
        order.setAddress(address);
        order.setNote(note);
        order.setCurrency(currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase());
        order.setPaymentMethod(normalizePayment(paymentMethod));
        order.setStatus("PENDING");
        UserAccount user = AccountController.currentUser(session);
        if (user != null) order.setUserId(user.getId());

        Map<Long, Integer> cart = cartService.getCart(session);
        BigDecimal total = BigDecimal.ZERO;
        List<Product> toRestock = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : cart.entrySet()) {
            Product p = productService.findById(e.getKey());
            if (p == null) continue;
            int want = Math.max(1, e.getValue());
            int stock = p.getStock() == null ? 0 : p.getStock();
            if (stock <= 0) continue;
            int qty = Math.min(want, stock);
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductNameEn(p.getNameEn());
            item.setPrice(p.getPrice());
            item.setQuantity(qty);
            item.setImageUrl(img(p));
            order.addItem(item);
            total = total.add((p.getPrice() == null ? BigDecimal.ZERO : p.getPrice()).multiply(BigDecimal.valueOf(qty)));
            p.setStock(stock - qty);
            toRestock.add(p);
        }
        if (order.getItems().isEmpty()) return err("购物车已失效，请重新选择商品");
        for (Product p : toRestock) productService.save(p);
        order.setTotalAmount(total);
        orderService.save(order);
        cartService.clear(session);
        erpSyncService.syncOrder(order);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("orderNo", order.getOrderNo());
        body.put("total", total);
        body.put("currency", order.getCurrency());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(HttpSession session,
                                    @RequestParam(value = "email", required = false) String email) {
        UserAccount user = AccountController.currentUser(session);
        List<Order> list;
        if (user != null) {
            list = orderService.findMyOrders(user.getId(), user.getEmail());
        } else if (email != null && !email.isBlank()) {
            list = orderService.findMyOrders(null, email.trim());
        } else {
            list = new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Order o : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNo", o.getOrderNo());
            m.put("status", o.getStatus());
            m.put("total", o.getTotalAmount());
            m.put("currency", o.getCurrency());
            m.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().format(TS));
            List<Map<String, Object>> items = new ArrayList<>();
            if (o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("name", it.getProductName());
                    im.put("quantity", it.getQuantity());
                    im.put("price", it.getPrice());
                    im.put("imageUrl", it.getImageUrl());
                    items.add(im);
                }
            }
            m.put("items", items);
            out.add(m);
        }
        return ResponseEntity.ok(Map.of("orders", out));
    }

    @GetMapping("/orders/{orderNo}")
    public ResponseEntity<?> orderDetail(@PathVariable String orderNo) {
        Order o = orderService.findByOrderNo(orderNo);
        if (o == null) return ResponseEntity.notFound().build();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", o.getOrderNo());
        m.put("status", o.getStatus());
        m.put("total", o.getTotalAmount());
        m.put("currency", o.getCurrency());
        m.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().format(TS));
        m.put("paymentMethod", o.getPaymentMethod());
        m.put("customerName", o.getCustomerName());
        m.put("email", o.getEmail());
        m.put("phone", o.getPhone());
        m.put("country", o.getCountry());
        m.put("address", o.getAddress());
        m.put("note", o.getNote());
        List<Map<String, Object>> items = new ArrayList<>();
        if (o.getItems() != null) {
            for (OrderItem it : o.getItems()) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("name", it.getProductName());
                im.put("quantity", it.getQuantity());
                im.put("price", it.getPrice());
                im.put("imageUrl", it.getImageUrl());
                items.add(im);
            }
        }
        m.put("items", items);
        return ResponseEntity.ok(m);
    }

    // ==================== 我的账户 ====================

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        UserAccount u = AccountController.currentUser(session);
        Map<String, Object> body = new LinkedHashMap<>();
        if (u == null) {
            body.put("loggedIn", false);
            return ResponseEntity.ok(body);
        }
        body.put("loggedIn", true);
        body.put("user", userMap(u));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestParam(required = false) String email,
                                   @RequestParam(required = false) String password,
                                   HttpServletRequest request) {
        String key = "user:" + (email == null ? "" : email.trim().toLowerCase());
        if (attemptService.isLocked(key)) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "error", "尝试次数过多，请稍后再试"));
        }
        var u = userService.login(email, password, IpUtils.clientIp(request), request.getHeader("User-Agent"));
        if (u.isEmpty()) {
            attemptService.recordFailure(key);
            return err("邮箱或密码不正确");
        }
        attemptService.recordSuccess(key);
        request.changeSessionId();
        HttpSession session = request.getSession(true);
        session.setAttribute(UserAccountService.SESSION_KEY, u.get());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("user", userMap(u.get()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestParam(required = false) String email,
                                      @RequestParam(required = false) String password,
                                      @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                                      @RequestParam(required = false) String name,
                                      HttpServletRequest request) {
        if (email == null || !email.contains("@")) return err("请输入正确的邮箱");
        if (password == null || password.length() < 8) return err("密码至少 8 位");
        if (confirmPassword == null || !confirmPassword.equals(password)) return err("两次输入的密码不一致");
        var created = userService.register(email, password, name, IpUtils.clientIp(request), request.getHeader("User-Agent"));
        if (created.isEmpty()) return err("该邮箱已注册");
        request.changeSessionId();
        HttpSession session = request.getSession(true);
        session.setAttribute(UserAccountService.SESSION_KEY, created.get());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("user", userMap(created.get()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/auth/profile")
    public ResponseEntity<?> profile(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam(required = false) String country,
                                     @RequestParam(required = false) String address,
                                     HttpSession session) {
        UserAccount u = AccountController.currentUser(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false, "error", "未登录"));
        userService.updateProfile(u.getId(), name, phone, country, address);
        UserAccount fresh = userService.findByEmail(u.getEmail()).orElse(u);
        session.setAttribute(UserAccountService.SESSION_KEY, fresh);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("user", userMap(fresh));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/auth/password")
    public ResponseEntity<?> password(@RequestParam(required = false) String oldPassword,
                                      @RequestParam(required = false) String newPassword,
                                      HttpSession session) {
        UserAccount u = AccountController.currentUser(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false, "error", "未登录"));
        if (newPassword == null || newPassword.length() < 8) return err("新密码至少 8 位");
        boolean ok = userService.changePassword(u.getId(), oldPassword, newPassword);
        if (!ok) return err("原密码不正确");
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam(required = false) String email) {
        if (email == null || !email.contains("@")) return err("请输入正确的邮箱");
        // 无论账号是否存在都给同样提示，避免泄露注册信息
        userService.createPasswordResetToken(email);
        return ResponseEntity.ok(Map.of("ok", true, "message", "重置邮件已发送，请查看邮箱"));
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam(required = false) String token,
                                           @RequestParam(required = false) String password,
                                           @RequestParam(value = "confirmPassword", required = false) String confirmPassword) {
        if (token == null || token.isBlank()) return err("重置令牌不能为空");
        if (password == null || password.length() < 8) return err("新密码至少 8 位");
        if (confirmPassword == null || !confirmPassword.equals(password)) return err("两次输入的密码不一致");
        boolean ok = userService.resetPassword(token, password);
        if (!ok) return err("令牌无效或已过期");
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ==================== helpers ====================

    private Map<String, Object> userMap(UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", u.getEmail());
        m.put("name", u.getName());
        m.put("phone", u.getPhone());
        m.put("country", u.getCountry());
        m.put("address", u.getAddress());
        return m;
    }

    private String img(Product p) {
        return (p.getImageUrl() != null && !p.getImageUrl().isBlank()) ? p.getImageUrl() : DEFAULT_IMG;
    }

    private ResponseEntity<?> err(String msg) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "error", msg));
    }

    private String generateOrderNo() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = 100 + new java.security.SecureRandom().nextInt(900);
        return "SH" + stamp + rnd;
    }

    private static final Set<String> PAY_METHODS = Set.of("PAYPAL", "WECHAT", "ALIPAY", "MASTERCARD");

    private static String normalizePayment(String m) {
        if (m == null) return null;
        String u = m.trim().toUpperCase();
        return PAY_METHODS.contains(u) ? u : null;
    }
}
