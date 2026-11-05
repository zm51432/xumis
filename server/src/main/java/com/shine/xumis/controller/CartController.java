package com.shine.xumis.controller;

import com.shine.xumis.dto.CartLine;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.Product;
import com.shine.xumis.model.ProductSku;
import com.shine.xumis.repository.ProductSkuRepository;
import com.shine.xumis.service.AnalyticsService;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.CatalogExtraService;
import com.shine.xumis.service.CouponService;
import com.shine.xumis.service.OrderMailService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import com.shine.xumis.service.PricingService;
import com.shine.xumis.service.StockReservationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车与结算（P0/P1 增强版）：
 * 规格 SKU、优惠券/满减、库存 30 分钟预占、订单状态邮件、转化埋点，
 * 与网页 / App 共用同一套 HttpSession 购物车。
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ErpSyncService erpSyncService;
    private final CouponService couponService;
    private final StockReservationService reservations;
    private final OrderMailService orderMail;
    private final AnalyticsService analytics;
    private final CatalogExtraService catalog;
    private final ProductSkuRepository skuRepository;
    private final PricingService pricing;

    public CartController(CartService cartService, ProductService productService,
                          OrderService orderService, ErpSyncService erpSyncService,
                          CouponService couponService, StockReservationService reservations,
                          OrderMailService orderMail, AnalyticsService analytics,
                          CatalogExtraService catalog, ProductSkuRepository skuRepository,
                          PricingService pricing) {
        this.cartService = cartService;
        this.productService = productService;
        this.orderService = orderService;
        this.erpSyncService = erpSyncService;
        this.couponService = couponService;
        this.reservations = reservations;
        this.orderMail = orderMail;
        this.analytics = analytics;
        this.catalog = catalog;
        this.skuRepository = skuRepository;
        this.pricing = pricing;
    }

    @GetMapping
    public String view(HttpSession session, Model model) {
        buildCart(session, model);
        return "cart";
    }

    @PostMapping("/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      @RequestParam(required = false) Long skuId,
                      @RequestParam(name = "goto", required = false) String gotoPage,
                      HttpSession session) {
        cartService.add(session, productId, quantity);
        cartService.setSku(session, productId, skuId);
        analytics.record("ADD_CART", "/cart/add", productId, session.getId());
        // 立即购买：加购后直达结算页，不绕购物车
        if ("checkout".equalsIgnoreCase(gotoPage)) return "redirect:/checkout";
        return "redirect:/cart";
    }

    /** 无刷新加购：返回 JSON，前端据此更新角标并弹 Toast */
    @PostMapping(value = "/add-json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> addJson(@RequestParam Long productId,
                                       @RequestParam(defaultValue = "1") int quantity,
                                       @RequestParam(required = false) Long skuId,
                                       HttpSession session) {
        Map<String, Object> res = new LinkedHashMap<>();
        Product p = productService.findById(productId);
        if (p == null) {
            res.put("ok", false); res.put("message", "商品不存在"); return res;
        }
        if (p.getStock() != null && p.getStock() <= 0) {
            res.put("ok", false); res.put("message", "该商品暂时缺货"); return res;
        }
        int q = Math.max(1, Math.min(quantity, 99));
        int added = cartService.add(session, productId, q);
        if (added <= 0) {
            res.put("ok", false);
            res.put("message", "库存不足，最多可购买 " + p.getStock() + " 件");
            return res;
        }
        cartService.setSku(session, productId, skuId);
        analytics.record("ADD_CART", "/cart/add-json", productId, session.getId());
        res.put("ok", true);
        res.put("count", cartService.count(session));
        res.put("qty", added);
        res.put("name", p.getName());
        return res;
    }

    @PostMapping("/update")
    public String update(@RequestParam Long productId, @RequestParam int quantity, HttpSession session) {
        cartService.setQty(session, productId, quantity);
        if (quantity <= 0) cartService.removeSku(session, productId);
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam Long productId, HttpSession session) {
        cartService.remove(session, productId);
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        if (cartService.isEmpty(session)) return "redirect:/cart";
        buildCart(session, model);
        var user = AccountController.currentUser(session);
        if (user != null) model.addAttribute("user", user);
        model.addAttribute("availableCoupons", couponService.findAll().stream()
                .filter(c -> c.isEnabled()).toList());
        model.addAttribute("quote", pricing.quote(cartSubtotal(session), BigDecimal.ZERO, false));
        return "checkout";
    }

    /** 结算页实时校验优惠码（POST + CSRF，返回折扣后的应付金额） */
    @PostMapping(value = "/coupon-check", produces = "application/json")
    @ResponseBody
    public Map<String, Object> couponCheck(@RequestParam(required = false) String code, HttpSession session) {
        BigDecimal subtotal = cartSubtotal(session);
        CouponService.Result r = couponService.evaluate(code, subtotal);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", r.ok());
        res.put("message", r.message());
        res.put("discount", r.discount());
        res.put("subtotal", subtotal);
        PricingService.Quote q = pricing.quote(subtotal, r.discount(), r.freeShip());
        res.put("total", q.total());
        res.put("shippingFee", q.shippingFee());
        res.put("taxAmount", q.taxAmount());
        res.put("freeShipping", q.freeShipping());
        res.put("freeShip", r.freeShip());
        return res;
    }

    @PostMapping("/place")
    @org.springframework.transaction.annotation.Transactional
    public String place(@RequestParam String customerName,
                        @RequestParam String email,
                        @RequestParam(required = false) String phone,
                        @RequestParam(required = false) String country,
                        @RequestParam(required = false) String address,
                        @RequestParam(required = false) String note,
                        @RequestParam String currency,
                        @RequestParam(required = false) String paymentMethod,
                        @RequestParam(required = false) String couponCode,
                        HttpSession session,
                        Model model) {
        if (cartService.isEmpty(session)) return "redirect:/cart";
        cartService.pruneInvalid(session);
        if (cartService.isEmpty(session)) return "redirect:/cart?invalid=1";

        // 先算含规格加价的小计，再套优惠券，保证折扣口径与页面一致
        BigDecimal subtotal = cartSubtotal(session);
        CouponService.Result cr = couponService.evaluate(couponCode, subtotal);
        if (couponCode != null && !couponCode.isBlank() && !cr.ok()) {
            return "redirect:/cart/checkout?couponError=" + enc(cr.message());
        }
        BigDecimal discount = cr.discount();
        PricingService.Quote quote = pricing.quote(subtotal, discount, cr.freeShip());
        BigDecimal total = quote.total();

        Map<Long, Integer> cart = cartService.getCart(session);
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setCustomerName(customerName);
        order.setEmail(email);
        order.setPhone(phone);
        order.setCountry(country);
        order.setAddress(address);
        order.setNote(note);
        order.setCurrency(currency);
        order.setPaymentMethod(normalizePayment(paymentMethod));
        order.setStatus("PENDING");
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setCouponCode(cr.coupon() == null ? null : cr.coupon().getCode());
        order.setTotalAmount(total);
        order.setShippingFee(quote.shippingFee());
        order.setTaxAmount(quote.taxAmount());
        order.setTaxRate(quote.taxRate());
        var user = AccountController.currentUser(session);
        if (user != null) order.setUserId(user.getId());

        List<Product> toSave = new ArrayList<>();
        List<Object[]> holds = new ArrayList<>();   // [productId, skuId, qty]
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            Long skuId = cartService.getSku(session, entry.getKey());
            ProductSku sku = skuId == null ? null : skuRepository.findById(skuId).orElse(null);
            if (sku != null && (!sku.isEnabled() || !entry.getKey().equals(sku.getProductId()))) sku = null;
            int want = Math.max(1, entry.getValue());
            int stock = p.getStock() == null ? 0 : p.getStock();
            if (sku != null) stock = Math.min(stock, sku.getStock() == null ? 0 : sku.getStock());
            if (stock <= 0) continue;
            int qty = Math.min(want, stock);

            BigDecimal unit = (p.getPrice() == null ? BigDecimal.ZERO : p.getPrice())
                    .add(sku == null || sku.getPriceDelta() == null ? BigDecimal.ZERO : sku.getPriceDelta());
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductNameEn(p.getNameEn());
            item.setPrice(unit);
            item.setQuantity(qty);
            item.setImageUrl(p.getImageUrl());
            if (sku != null) {
                item.setSkuId(sku.getId());
                item.setSkuLabel(sku.getLabel());
                sku.setStock((sku.getStock() == null ? 0 : sku.getStock()) - qty);
                skuRepository.save(sku);
                erpSyncService.syncSkuStock(sku.getId(), sku.getStock());
            }
            order.addItem(item);
            p.setStock((p.getStock() == null ? 0 : p.getStock()) - qty);
            toSave.add(p);
            holds.add(new Object[]{p.getId(), sku == null ? null : sku.getId(), qty});
        }
        if (order.getItems().isEmpty()) return "redirect:/cart?invalid=1";

        for (Product p : toSave) productService.save(p);
        orderService.save(order);
        for (Object[] h : holds) {
            reservations.hold(order.getOrderNo(), (Long) h[0], (Long) h[1], (Integer) h[2]);
        }
        if (cr.coupon() != null) {
            try { couponService.consume(cr.coupon()); } catch (Exception ignored) {}
        }
        cartService.clear(session);
        erpSyncService.syncOrder(order);
        try { orderMail.placed(order); } catch (Exception ignored) {}
        analytics.record("ORDER", "/cart/place", null, session.getId());
        return "redirect:/order-success?no=" + order.getOrderNo();
    }

    @GetMapping("/success")
    public String success() { return "redirect:/order-success"; }

    /** 购物车 + 结算页共用：行明细（含规格）、小计、件数 */
    private void buildCart(HttpSession session, Model model) {
        cartService.pruneInvalid(session);
        Map<Long, Integer> cart = cartService.getCart(session);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            int qty = Math.max(1, entry.getValue());
            CartLine line = new CartLine(p, qty);
            Long skuId = cartService.getSku(session, entry.getKey());
            if (skuId != null) line.applySku(catalog.findSku(skuId));
            lines.add(line);
            total = total.add(line.getLineTotalWithSku());
        }
        model.addAttribute("lines", lines);
        model.addAttribute("total", total);
        model.addAttribute("count", cartService.count(session));
        model.addAttribute("isEmpty", lines.isEmpty());
    }

    /** 含 SKU 加价的小计（优惠券校验 / 下单共用同一口径） */
    private BigDecimal cartSubtotal(HttpSession session) {
        cartService.pruneInvalid(session);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : cartService.getCart(session).entrySet()) {
            Product p = productService.findById(e.getKey());
            if (p == null) continue;
            BigDecimal unit = p.getPrice() == null ? BigDecimal.ZERO : p.getPrice();
            Long skuId = cartService.getSku(session, e.getKey());
            if (skuId != null) {
                ProductSku sku = catalog.findSku(skuId);
                if (sku != null && sku.isEnabled() && sku.getPriceDelta() != null) {
                    unit = unit.add(sku.getPriceDelta());
                }
            }
            total = total.add(unit.multiply(BigDecimal.valueOf(Math.max(1, e.getValue()))));
        }
        return total;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String generateOrderNo() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = 100 + new java.security.SecureRandom().nextInt(900);
        return "SH" + stamp + rnd;
    }

    private static final java.util.Set<String> PAY_METHODS =
            java.util.Set.of("PAYPAL", "WECHAT", "ALIPAY", "MASTERCARD");

    private static String normalizePayment(String m) {
        if (m == null) return null;
        String u = m.trim().toUpperCase();
        return PAY_METHODS.contains(u) ? u : null;
    }
}
