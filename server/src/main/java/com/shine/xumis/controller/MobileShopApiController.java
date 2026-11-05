package com.shine.xumis.controller;

import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.ProductSku;
import com.shine.xumis.model.TrackingEvent;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.repository.TrackingEventRepository;
import com.shine.xumis.model.UserAccount;
import com.shine.xumis.service.AnalyticsService;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.CouponService;
import com.shine.xumis.service.OrderMailService;
import com.shine.xumis.service.StockReservationService;
import com.shine.xumis.service.LoginAttemptService;
import com.shine.xumis.service.MailService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.PricingService;
import com.shine.xumis.service.UserAccountService;
import com.shine.xumis.service.VerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机 App 专用接口：原生购物车与账号页。
 * <p>
 * 与网页端共用同一个 HttpSession（OkHttp 携带 JSESSIONID Cookie 即可），
 * 因此 App 里加到购物车的商品，在 App 内打开网页结算时仍然是同一份购物车。
 * 路径在 /api/ 下，按 CsrfInterceptor 规则免 CSRF 令牌。
 */
@RestController
@RequestMapping("/api/m")
public class MobileShopApiController {

    private final CartService cartService;
    private final ProductService productService;
    private final UserAccountService userService;
    private final OrderService orderService;
    private final LoginAttemptService attemptService;
    private final ErpSyncService erpSyncService;
    private final VerificationCodeService codeService;
    private final MailService mailService;
    private final CouponService couponService;
    private final StockReservationService reservations;
    private final OrderMailService orderMail;
    private final AnalyticsService analytics;
    private final PricingService pricing;
    private final ProductSkuRepository skuRepository;
    private final TrackingEventRepository trackingRepo;

    public MobileShopApiController(CartService cartService, ProductService productService,
                                   UserAccountService userService, OrderService orderService,
                                   LoginAttemptService attemptService,
                                   ErpSyncService erpSyncService,
                                   VerificationCodeService codeService,
                                   MailService mailService, CouponService couponService,
                                   StockReservationService reservations,
                                   OrderMailService orderMail, AnalyticsService analytics,
                                   PricingService pricing,
                                   ProductSkuRepository skuRepository,
                                   TrackingEventRepository trackingRepo) {
        this.cartService = cartService;
        this.productService = productService;
        this.userService = userService;
        this.orderService = orderService;
        this.attemptService = attemptService;
        this.erpSyncService = erpSyncService;
        this.codeService = codeService;
        this.mailService = mailService;
        this.couponService = couponService;
        this.reservations = reservations;
        this.orderMail = orderMail;
        this.analytics = analytics;
        this.pricing = pricing;
        this.skuRepository = skuRepository;
        this.trackingRepo = trackingRepo;
    }

    // ==================== 购物车 ====================

    /** 购物车内容（原生页面直接渲染这份数据） */
    @GetMapping("/cart")
    public Map<String, Object> cart(HttpSession session) {
        return cartPayload(session);
    }

    @PostMapping("/cart/add")
    public Map<String, Object> addToCart(@RequestParam Long productId,
                                         @RequestParam(defaultValue = "1") int quantity,
                                         @RequestParam(required = false) Long skuId,
                                         HttpSession session) {
        int q = Math.max(1, Math.min(quantity, 99));
        int after = cartService.add(session, productId, q);
        if (after > 0) cartService.setSku(session, productId, skuId);
        Map<String, Object> body = cartPayload(session);
        body.put("ok", after > 0);
        if (after <= 0) {
            body.put("message", "该商品暂时缺货或不存在");
        }
        return body;
    }

    /** 设置数量：quantity<=0 等同于移除 */
    @PostMapping("/cart/set")
    public Map<String, Object> setQuantity(@RequestParam Long productId,
                                           @RequestParam int quantity,
                                           HttpSession session) {
        cartService.setQty(session, productId, quantity);
        Map<String, Object> body = cartPayload(session);
        body.put("ok", true);
        return body;
    }

    @PostMapping("/cart/remove")
    public Map<String, Object> removeFromCart(@RequestParam Long productId, HttpSession session) {
        cartService.remove(session, productId);
        Map<String, Object> body = cartPayload(session);
        body.put("ok", true);
        return body;
    }

    private Map<String, Object> cartPayload(HttpSession session) {
        Map<Long, Integer> raw = cartService.getCart(session);
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (Map.Entry<Long, Integer> e : raw.entrySet()) {
            Product p = productService.findById(e.getKey());
            if (p == null) {
                continue;
            }
            int qty = (e.getValue() == null) ? 0 : e.getValue();
            BigDecimal price = (p.getPrice() == null) ? BigDecimal.ZERO : p.getPrice();
            Long skuId = cartService.getSku(session, e.getKey());
            ProductSku sku = (skuId == null) ? null : skuRepository.findById(skuId).orElse(null);
            BigDecimal unit = price.add(sku == null || sku.getPriceDelta() == null ? BigDecimal.ZERO : sku.getPriceDelta());
            BigDecimal line = unit.multiply(BigDecimal.valueOf(qty));
            total = total.add(line);
            count += qty;

            Map<String, Object> it = new LinkedHashMap<>();
            it.put("productId", p.getId());
            it.put("name", p.getName());
            it.put("nameEn", p.getNameEn());
            it.put("price", price);
            it.put("quantity", qty);
            it.put("stock", (p.getStock() == null) ? 0 : p.getStock());
            it.put("imageUrl", p.getImageUrl());
            it.put("lineTotal", line);
            it.put("skuId", sku == null ? null : sku.getId());
            it.put("skuLabel", sku == null ? null : sku.getLabel());
            items.add(it);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("count", count);
        body.put("total", total);
        body.put("currency", "USD");
        return body;
    }

    // ==================== 账号 ====================

    /** 当前登录状态 + 账号信息 + 最近订单 */
    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        UserAccount u = currentUser(session);
        if (u == null) {
            body.put("loggedIn", false);
            return body;
        }
        body.put("loggedIn", true);
        body.put("email", u.getEmail());
        body.put("name", u.getName() == null ? "" : u.getName());
        body.put("phone", u.getPhone() == null ? "" : u.getPhone());
        body.put("country", u.getCountry() == null ? "" : u.getCountry());
        body.put("address", u.getAddress() == null ? "" : u.getAddress());
        body.put("emailVerified", u.isEmailVerified());

        List<Map<String, Object>> orders = new ArrayList<>();
        try {
            for (Order o : orderService.findMyOrders(u.getId(), u.getEmail())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", o.getOrderNo());
                m.put("status", o.getStatus());
                m.put("totalAmount", o.getTotalAmount());
                m.put("currency", o.getCurrency() == null ? "USD" : o.getCurrency());
                m.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
                m.put("itemCount", (o.getItems() == null) ? 0 : o.getItems().size());
                orders.add(m);
            }
        } catch (Exception ignored) {
            // 订单查询失败不影响账号信息展示
        }
        body.put("orders", orders);
        return body;
    }

    /** App 内登录：成功后把账号写入同一个 HttpSession */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String email,
                                                    @RequestParam String password,
                                                    HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        String key = "user:" + mail;
        if (attemptService.isLocked(key)) {
            body.put("ok", false);
            body.put("error", "locked");
            body.put("remainingSeconds", attemptService.remainingSeconds(key));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        }
        var found = userService.login(mail, password,
                com.shine.xumis.util.IpUtils.clientIp(request), request.getHeader("User-Agent"));
        if (found.isEmpty()) {
            attemptService.recordFailure(key);
            body.put("ok", false);
            body.put("error", "badCredentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        attemptService.recordSuccess(key);
        // 防会话固定：先确保会话存在，再更换 Session ID（无会话时 changeSessionId 会抛异常）
        request.getSession(true);
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // 极端情况下换个失败也不影响登录
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(UserAccountService.SESSION_KEY, found.get());
        session.removeAttribute(com.shine.xumis.util.Csrf.SESSION_ATTR);

        UserAccount u = found.get();
        body.put("ok", true);
        body.put("email", u.getEmail());
        body.put("name", u.getName() == null ? "" : u.getName());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        return body;
    }

    // ==================== 注册 / 找回密码（App 内完成，不跳网页） ====================

    /** 注册：发送 6 位邮箱验证码（60 秒冷却，10 分钟有效，一码一用） */
    @PostMapping("/register/send-code")
    public Map<String, Object> registerSendCode(@RequestParam(required = false) String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (!mail.contains("@") || mail.startsWith("@") || mail.endsWith("@")) {
            body.put("ok", false);
            body.put("error", "badEmail");
            return body;
        }
        if (userService.findByEmail(mail).isPresent()) {
            body.put("ok", false);
            body.put("error", "dupEmail");
            return body;
        }
        VerificationCodeService.SendOutcome out = codeService.send(mail);
        if (out.result == VerificationCodeService.SendResult.INVALID_EMAIL) {
            body.put("ok", false);
            body.put("error", "badEmail");
        } else if (out.result == VerificationCodeService.SendResult.RATE_LIMITED) {
            body.put("ok", false);
            body.put("error", "rateLimited");
        } else {
            mailService.sendCode(mail, out.code);
            body.put("ok", true);
        }
        return body;
    }

    /** 注册：先校验邮箱验证码，通过后才建号（并直接登录） */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestParam(required = false) String email,
                                                        @RequestParam(required = false) String password,
                                                        @RequestParam(required = false) String name,
                                                        @RequestParam(required = false) String code,
                                                        HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (!mail.contains("@")) {
            body.put("ok", false);
            body.put("error", "badEmail");
            return ResponseEntity.badRequest().body(body);
        }
        if (password == null || password.length() < 8) {
            body.put("ok", false);
            body.put("error", "weakPassword");
            return ResponseEntity.badRequest().body(body);
        }
        if (userService.findByEmail(mail).isPresent()) {
            body.put("ok", false);
            body.put("error", "dupEmail");
            return ResponseEntity.badRequest().body(body);
        }
        if (!codeService.verify(mail, code)) {
            body.put("ok", false);
            body.put("error", "badCode");
            return ResponseEntity.badRequest().body(body);
        }
        var created = userService.register(mail, password, name,
                com.shine.xumis.util.IpUtils.clientIp(request), request.getHeader("User-Agent"), true);
        if (created.isEmpty()) {
            body.put("ok", false);
            body.put("error", "dupEmail");
            return ResponseEntity.badRequest().body(body);
        }
        // 注册即登录，避免用户再输一次密码
        openSession(request, created.get());
        body.put("ok", true);
        body.put("email", created.get().getEmail());
        body.put("name", created.get().getName() == null ? "" : created.get().getName());
        return ResponseEntity.ok(body);
    }

    /** 找回密码：发送验证码。exists=false 表示该邮箱未注册（App 侧给出更明确的引导） */
    @PostMapping("/password/send-code")
    public Map<String, Object> passwordSendCode(@RequestParam(required = false) String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (!mail.contains("@") || mail.startsWith("@") || mail.endsWith("@")) {
            body.put("ok", false);
            body.put("error", "badEmail");
            return body;
        }
        if (userService.findByEmail(mail).isEmpty()) {
            body.put("ok", false);
            body.put("error", "noAccount");
            return body;
        }
        VerificationCodeService.SendOutcome out = codeService.send(mail);
        if (out.result == VerificationCodeService.SendResult.INVALID_EMAIL) {
            body.put("ok", false);
            body.put("error", "badEmail");
        } else if (out.result == VerificationCodeService.SendResult.RATE_LIMITED) {
            body.put("ok", false);
            body.put("error", "rateLimited");
        } else {
            mailService.sendCode(mail, out.code);
            body.put("ok", true);
        }
        return body;
    }

    /** 找回密码：验证码校验通过后直接改密（不依赖邮件里的网页链接） */
    @PostMapping("/password/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestParam(required = false) String email,
                                                             @RequestParam(required = false) String code,
                                                             @RequestParam(required = false) String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (newPassword == null || newPassword.length() < 8) {
            body.put("ok", false);
            body.put("error", "weakPassword");
            return ResponseEntity.badRequest().body(body);
        }
        if (!codeService.verify(mail, code)) {
            body.put("ok", false);
            body.put("error", "badCode");
            return ResponseEntity.badRequest().body(body);
        }
        if (!userService.resetPasswordByEmail(mail, newPassword)) {
            body.put("ok", false);
            body.put("error", "noAccount");
            return ResponseEntity.badRequest().body(body);
        }
        body.put("ok", true);
        return ResponseEntity.ok(body);
    }

    // ==================== 账单 / 订单详情（原生页面） ====================

    /** 订单详情：仅本人（或本账号下单邮箱）可看 */
    @GetMapping("/order/{orderNo}")
    public ResponseEntity<Map<String, Object>> orderDetail(@PathVariable String orderNo,
                                                           HttpSession session) {
        Map<String, Object> body = new LinkedHashMap<>();
        UserAccount u = currentUser(session);
        if (u == null) {
            body.put("ok", false);
            body.put("error", "notLoggedIn");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        Order o = orderService.findByOrderNo(orderNo);
        if (o == null) {
            body.put("ok", false);
            body.put("error", "notFound");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        boolean owner = (o.getUserId() != null && o.getUserId().equals(u.getId()))
                || (o.getEmail() != null && o.getEmail().equalsIgnoreCase(u.getEmail()));
        if (!owner) {
            body.put("ok", false);
            body.put("error", "forbidden");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        body.put("ok", true);
        body.put("orderNo", o.getOrderNo());
        body.put("status", o.getStatus());
        body.put("paymentMethod", o.getPaymentMethod());
        body.put("totalAmount", o.getTotalAmount());
        body.put("currency", o.getCurrency() == null ? "USD" : o.getCurrency());
        body.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
        body.put("customerName", nz(o.getCustomerName()));
        body.put("email", nz(o.getEmail()));
        body.put("phone", nz(o.getPhone()));
        body.put("country", nz(o.getCountry()));
        body.put("address", nz(o.getAddress()));
        body.put("note", nz(o.getNote()));
        List<Map<String, Object>> items = new ArrayList<>();
        if (o.getItems() != null) {
            for (OrderItem it : o.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", it.getProductId());
                m.put("name", nz(it.getProductName()));
                m.put("nameEn", nz(it.getProductNameEn()));
                m.put("price", it.getPrice());
                m.put("quantity", it.getQuantity() == null ? 1 : it.getQuantity());
                m.put("imageUrl", nz(it.getImageUrl()));
                items.add(m);
            }
        }
        body.put("items", items);
        body.put("subtotalAmount", o.getSubtotalAmount());
        body.put("discountAmount", o.getDiscountAmount());
        body.put("shippingFee", o.getShippingFee());
        body.put("taxAmount", o.getTaxAmount());
        body.put("couponCode", o.getCouponCode());
        body.put("trackingNo", o.getTrackingNo());
        body.put("carrier", o.getCarrier());
        body.put("trackingUrl", o.getTrackingUrl());
        body.put("shippedAt", o.getShippedAt() == null ? null : o.getShippedAt().toString());
        List<Map<String, Object>> events = new ArrayList<>();
        for (TrackingEvent ev : trackingRepo.findByOrderNoOrderByEventTimeDescIdDesc(orderNo)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventTime", ev.getEventTime() == null ? "" : ev.getEventTime().toString());
            m.put("location", ev.getLocation());
            m.put("description", ev.getDescription());
            events.add(m);
        }
        body.put("trackingEvents", events);
        return ResponseEntity.ok(body);
    }

    /** 把账号写入会话（登录/注册共用），并换一次 Session ID 防会话固定 */
    private void openSession(HttpServletRequest request, UserAccount u) {
        request.getSession(true);
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // 无会话时换 ID 会抛异常，忽略即可
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(UserAccountService.SESSION_KEY, u);
        session.removeAttribute(com.shine.xumis.util.Csrf.SESSION_ATTR);
    }

    // ==================== 结算 / 下单（App 内完成，不跳网页） ====================

    /** 支付渠道白名单：与网页端保持一致（当前为「记录渠道 + 待付款」） */
    private static final String[][] PAY_METHODS = {
            {"PAYPAL", "PayPal"},
            {"WECHAT", "微信支付"},
            {"ALIPAY", "支付宝"},
            {"MASTERCARD", "信用卡 / Mastercard"}
    };

    /** 结算页数据：购物车 + 收货资料（已登录自动带出）+ 可选支付方式 */
    @GetMapping("/checkout")
    public Map<String, Object> checkout(HttpSession session) {
        Map<String, Object> body = cartPayload(session);
        UserAccount u = currentUser(session);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("loggedIn", u != null);
        if (u != null) {
            user.put("email", nz(u.getEmail()));
            user.put("name", nz(u.getName()));
            user.put("phone", nz(u.getPhone()));
            user.put("country", nz(u.getCountry()));
            user.put("address", nz(u.getAddress()));
        }
        List<Map<String, Object>> methods = new ArrayList<>();
        for (String[] m : PAY_METHODS) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("code", m[0]);
            mm.put("label", m[1]);
            methods.add(mm);
        }
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) body.get("items");
        body.put("user", user);
        body.put("methods", methods);
        BigDecimal sub = (BigDecimal) body.get("total");
        PricingService.Quote q = pricing.quote(sub, BigDecimal.ZERO, false);
        body.put("subtotal", q.subtotal());
        body.put("shippingFee", q.shippingFee());
        body.put("taxAmount", q.taxAmount());
        body.put("discount", BigDecimal.ZERO);
        body.put("total", q.total());
        body.put("ok", items != null && !items.isEmpty());
        return body;
    }

    /** 阶段3：商品规格列表（App 详情页选择 SKU） */
    @GetMapping("/products/{id}/skus")
    public List<Map<String, Object>> productSkus(@PathVariable Long id) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductSku s : skuRepository.findByProductIdAndEnabledTrueOrderBySortOrderAscIdAsc(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", s.getId());
            m.put("skuCode", s.getSkuCode());
            m.put("label", s.getLabel());
            m.put("labelEn", s.getLabelEn());
            m.put("priceDelta", s.getPriceDelta());
            m.put("stock", s.getStock());
            out.add(m);
        }
        return out;
    }

    /** 阶段3：App 结算页实时校验优惠码（与网页 /cart/coupon-check 同一口径） */
    @PostMapping("/coupon-check")
    public Map<String, Object> couponCheck(@RequestParam(required = false) String code, HttpSession session) {
        Map<String, Object> res = new LinkedHashMap<>();
        BigDecimal sub = (BigDecimal) cartPayload(session).get("total");
        CouponService.Result r = couponService.evaluate(code, sub);
        PricingService.Quote q = pricing.quote(sub, r.discount(), r.freeShip());
        res.put("ok", r.ok());
        res.put("message", r.message());
        res.put("subtotal", q.subtotal());
        res.put("discount", q.discount());
        res.put("shippingFee", q.shippingFee());
        res.put("taxAmount", q.taxAmount());
        res.put("total", q.total());
        res.put("freeShipping", q.freeShipping());
        return res;
    }

    /**
     * 提交订单：与网页 /cart/place 同一套规则——清理失效条目、按库存截断、扣库存、
     * 生成订单号、清空购物车、同步 ERP。返回订单号供 App 展示成功页。
     */
    @PostMapping("/order/place")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestParam String customerName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String note,
            @RequestParam(required = false, defaultValue = "USD") String currency,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String couponCode,
            HttpSession session) {

        Map<String, Object> body = new LinkedHashMap<>();
        if (customerName == null || customerName.isBlank()
                || email == null || !email.contains("@")
                || address == null || address.isBlank()) {
            body.put("ok", false);
            body.put("error", "badInfo");
            return ResponseEntity.badRequest().body(body);
        }
        if (cartService.isEmpty(session)) {
            body.put("ok", false);
            body.put("error", "emptyCart");
            return ResponseEntity.badRequest().body(body);
        }
        cartService.pruneInvalid(session);
        if (cartService.isEmpty(session)) {
            body.put("ok", false);
            body.put("error", "invalidCart");
            return ResponseEntity.badRequest().body(body);
        }

        Map<Long, Integer> cart = cartService.getCart(session);
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setCustomerName(customerName.trim());
        order.setEmail(email.trim());
        order.setPhone(phone);
        order.setCountry(country);
        order.setAddress(address);
        order.setNote(note);
        order.setCurrency(currency == null || currency.isBlank() ? "USD" : currency);
        order.setPaymentMethod(normalizePayment(paymentMethod));
        order.setStatus("PENDING");

        UserAccount u = currentUser(session);
        if (u != null) {
            order.setUserId(u.getId());
        }

        BigDecimal total = BigDecimal.ZERO;
        List<Product> toSave = new ArrayList<>();
        List<Object[]> holds = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : cart.entrySet()) {
            Product p = productService.findById(e.getKey());
            if (p == null) continue;
            int stock = (p.getStock() == null) ? 0 : p.getStock();
            if (stock <= 0) continue;
            int qty = Math.min(Math.max(1, e.getValue()), stock);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductNameEn(p.getNameEn());
            item.setPrice(p.getPrice());
            item.setQuantity(qty);
            item.setImageUrl(p.getImageUrl());
            order.addItem(item);

            total = total.add(p.getPrice() == null ? BigDecimal.ZERO
                    : p.getPrice().multiply(BigDecimal.valueOf(qty)));
            p.setStock(stock - qty);
            toSave.add(p);
            holds.add(new Object[]{p.getId(), null, qty});
        }
        if (order.getItems().isEmpty()) {
            body.put("ok", false);
            body.put("error", "noStock");
            return ResponseEntity.badRequest().body(body);
        }
        for (Product p : toSave) {
            productService.save(p);
        }
        BigDecimal subtotal = total;
        com.shine.xumis.service.CouponService.Result cr = couponService.evaluate(couponCode, subtotal);
        if (couponCode != null && !couponCode.isBlank() && !cr.ok()) {
            body.put("ok", false);
            body.put("error", "badCoupon");
            body.put("message", cr.message());
            return ResponseEntity.badRequest().body(body);
        }
        BigDecimal discount = cr.discount();
        PricingService.Quote quote = pricing.quote(subtotal, discount, cr.freeShip());
        total = quote.total();
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setCouponCode(cr.coupon() == null ? null : cr.coupon().getCode());
        order.setTotalAmount(total);
        order.setShippingFee(quote.shippingFee());
        order.setTaxAmount(quote.taxAmount());
        order.setTaxRate(quote.taxRate());
        orderService.save(order);
        for (Object[] h : holds) {
            reservations.hold(order.getOrderNo(), (Long) h[0], (Long) h[1], (Integer) h[2]);
        }
        if (cr.coupon() != null) {
            try { couponService.consume(cr.coupon()); } catch (Exception ignored) {}
        }
        cartService.clear(session);
        try {
            erpSyncService.syncOrder(order);
        } catch (Exception ignored) {
            // ERP 同步失败不影响下单结果
        }

        try { orderMail.placed(order); } catch (Exception ignored) {}
        analytics.record("ORDER", "/api/m/order/place", null, session.getId());

        body.put("ok", true);
        body.put("orderNo", order.getOrderNo());
        body.put("subtotal", subtotal);
        body.put("discount", discount);
        body.put("shippingFee", quote.shippingFee());
        body.put("taxAmount", quote.taxAmount());
        body.put("couponCode", order.getCouponCode());
        body.put("total", total);
        body.put("currency", order.getCurrency());
        body.put("paymentMethod", order.getPaymentMethod());
        return ResponseEntity.ok(body);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String generateOrderNo() {
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = 100 + new java.security.SecureRandom().nextInt(900);
        return "SH" + stamp + rnd;
    }

    private static String normalizePayment(String m) {
        if (m == null) return null;
        String up = m.trim().toUpperCase();
        for (String[] p : PAY_METHODS) {
            if (p[0].equals(up)) return up;
        }
        return null;
    }

    private static UserAccount currentUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object o = session.getAttribute(UserAccountService.SESSION_KEY);
        return (o instanceof UserAccount u) ? u : null;
    }
}
