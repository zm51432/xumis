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
        return "checkout";
    }

    @PostMapping("/place")
    public String place(@RequestParam String customerName,
                        @RequestParam String email,
                        @RequestParam(required = false) String phone,
                        @RequestParam(required = false) String country,
                        @RequestParam(required = false) String address,
                        @RequestParam(required = false) String note,
                        @RequestParam String currency,
                        HttpSession session,
                        Model model) {
        if (cartService.isEmpty(session)) {
            return "redirect:/cart";
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
        order.setStatus("PENDING");

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductNameEn(p.getNameEn());
            item.setPrice(p.getPrice());
            item.setQuantity(entry.getValue());
            item.setImageUrl(p.getImageUrl());
            order.addItem(item);
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
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
        Map<Long, Integer> cart = cartService.getCart(session);
        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : cart.entrySet()) {
            Product p = productService.findById(entry.getKey());
            if (p == null) continue;
            lines.add(new CartLine(p, entry.getValue()));
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        model.addAttribute("lines", lines);
        model.addAttribute("total", total);
        model.addAttribute("count", cartService.count(session));
        model.addAttribute("isEmpty", lines.isEmpty());
    }

    private String generateOrderNo() {
        return "SH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
