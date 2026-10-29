package com.shine.xumis.controller;

import com.shine.xumis.dto.CartLine;
import com.shine.xumis.integration.erp.ErpSyncService;
import com.shine.xumis.model.Order;
import com.shine.xumis.model.OrderItem;
import com.shine.xumis.model.Product;
import com.shine.xumis.service.CartService;
import com.shine.xumis.service.OrderService;
import com.shine.xumis.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车与结算：对标主流独立站的 guest checkout 流程。
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ErpSyncService erpSyncService;

    public CartController(CartService cartService, ProductService productService,
                          OrderService orderService, ErpSyncService erpSyncService) {
        this.cartService = cartService;
        this.productService = productService;
        this.orderService = orderService;
        this.erpSyncService = erpSyncService;
    }

    @GetMapping
    public String view(HttpSession session, Model model) {
        buildCart(session, model);
        return "cart";
    }

    @PostMapping("/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int quantity,
                      HttpSession session) {
        cartService.add(session, productId, quantity);
        return "redirect:/cart";
    }

    /**
     * 无刷新加购（C 端体验）：返回 JSON，前端据此更新角标并弹 Toast，不再整页跳转。
     */
    @PostMapping(value = "/add-json", produces = "application/json")
    @ResponseBody
    public Map<String, Object> addJson(@RequestParam Long productId,
                                       @RequestParam(defaultValue = "1") int quantity,
                                       HttpSession session) {
        Map<String, Object> res = new LinkedHashMap<>();
        Product p = productService.findById(productId);
        if (p == null) {
            res.put("ok", false);
            res.put("message", "商品不存在");
            return res;
        }
        if (p.getStock() != null && p.getStock() <= 0) {
            res.put("ok", false);
            res.put("message", "该商品暂时缺货");
            return res;
        }
        int q = Math.max(1, Math.min(quantity, 99));
        int added = cartService.add(session, productId, q);
        if (added <= 0) {
            res.put("ok", false);
            res.put("message", "库存不足，最多可购买 " + p.getStock() + " 件");
            return res;
        }
        res.put("ok", true);
        res.put("count", cartService.count(session));
        res.put("qty", added);
        res.put("name", p.getName());
        return res;
    }

    @PostMapping("/update")
    public String update(@RequestParam Long productId,
                         @RequestParam int quantity,
                         HttpSession session) {
        cartService.setQty(session, productId, quantity);
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam Long productId, HttpSession session) {
        cartService.remove(session, productId);
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String checkout(HttpSession session, Model model) {
        if (cartService.isEmpty(session)) {
            return "redirect:/cart";
        }
        buildCart(session, model);
        // 已登录客户：结算页自动带出收货资料
        var user = AccountController.currentUser(session);
        if (user != null) {
            model.addAttribute("user", user);
        }
        return "checkout";
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
                        HttpSession session,
                        Model model) {
        if (cartService.isEmpty(session)) {
            return "redirect:/cart";
        }
        // 结算前清理已删除/缺货的条目，避免下出空单
        cartService.pruneInvalid(session);
        if (cartService.isEmpty(session)) {
            return "redirect:/cart?invalid=1";
        }
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
        // 记录客户选择的支付渠道（仅接受白名单内的值，避免被塞入任意字符串）
        order.setPaymentMethod(normalizePayment(paymentMethod));
        order.setStatus("PENDING");
        // 登录用户下单：订单归属账号，便于在「我的账户」回看
        var user = AccountController.currentUser(session);
        if (user != null) {
            order.setUserId(user.getId());
        }

        BigDecimal total = BigDecimal.ZERO;
        List<Product> toRestock = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            int want = Math.max(1, entry.getValue());
            int stock = (p.getStock() == null) ? 0 : p.getStock();
            // 库存不足：按现有库存截断，不够则跳过
            if (stock <= 0) continue;
            int qty = Math.min(want, stock);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductNameEn(p.getNameEn());
            item.setPrice(p.getPrice());
            item.setQuantity(qty);
            item.setImageUrl(p.getImageUrl());
            order.addItem(item);
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            // 扣减库存（防超卖）
            p.setStock(stock - qty);
            toRestock.add(p);
        }
        // 一件都没能下单（理论上不会，兜底保护）
        if (order.getItems().isEmpty()) {
            return "redirect:/cart?invalid=1";
        }
        for (Product p : toRestock) {
            productService.save(p);
        }
        // 总额以基准 USD 存储（便于 ERP/财务统一），展示时按币种换算
        order.setTotalAmount(total);
        orderService.save(order);
        cartService.clear(session);

        erpSyncService.syncOrder(order);

        return "redirect:/order-success?no=" + order.getOrderNo();
    }

    @GetMapping("/success")
    public String success() {
        return "redirect:/order-success";
    }

    private void buildCart(HttpSession session, Model model) {
        // 展示前先清掉已删除/缺货的条目，避免购物车里出现"幽灵商品"
        cartService.pruneInvalid(session);
        Map<Long, Integer> cart = cartService.getCart(session);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            int qty = Math.max(1, entry.getValue());
            lines.add(new CartLine(p, qty));
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        model.addAttribute("lines", lines);
        model.addAttribute("total", total);
        model.addAttribute("count", cartService.count(session));
        model.addAttribute("isEmpty", lines.isEmpty());
    }

    /**
     * 生成订单号：时间戳 + 3 位随机。
     * 只用时间戳时，同一秒内的两单会撞号（order_no 有唯一约束）导致第二单入库失败。
     */
    private String generateOrderNo() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = 100 + new java.security.SecureRandom().nextInt(900);
        return "SH" + stamp + rnd;
    }

    /** 支持的支付渠道白名单 */
    private static final java.util.Set<String> PAY_METHODS =
            java.util.Set.of("PAYPAL", "WECHAT", "ALIPAY", "MASTERCARD");

    /** 规范化支付渠道：仅接受白名单内的值，其余视为未选择（null） */
    private static String normalizePayment(String m) {
        if (m == null) return null;
        String u = m.trim().toUpperCase();
        return PAY_METHODS.contains(u) ? u : null;
    }
}
