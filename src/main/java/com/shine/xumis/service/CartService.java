package com.shine.xumis.service;

import com.shine.xumis.model.Product;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 购物车服务：基于 HttpSession 的轻量购物车（无需登录即可下单，对标跨境独立站 guest checkout）。
 * 结构：Map<productId, quantity>
 */
@Service
public class CartService {

    private final ProductService productService;

    public CartService(ProductService productService) {
        this.productService = productService;
    }

    public static final String SESSION_KEY = "SHINE_CART";

    /** 单件商品的数量上限，避免手改参数塞进 absurd 数量 */
    private static final int MAX_QTY_PER_ITEM = 99;

    @SuppressWarnings("unchecked")
    public Map<Long, Integer> getCart(HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute(SESSION_KEY);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(SESSION_KEY, cart);
        }
        return cart;
    }

    /**
     * 加购：受库存约束（不超卖），并限制单品数量上限。
     * @return 实际加入后的数量；0 表示被拒绝（缺货 / 商品不存在）
     */
    public int add(HttpSession session, Long productId, int qty) {
        if (productId == null || qty <= 0) {
            return 0;
        }
        Product p = productService.findById(productId);
        if (p == null) {
            return 0;
        }
        int stock = (p.getStock() == null) ? 0 : p.getStock();
        if (stock <= 0) {
            return 0;
        }
        Map<Long, Integer> cart = getCart(session);
        int current = cart.getOrDefault(productId, 0);
        int next = Math.min(current + qty, Math.min(stock, MAX_QTY_PER_ITEM));
        if (next <= 0) {
            return 0;
        }
        cart.put(productId, next);
        return next;
    }

    /** 改数量：同样受库存约束 */
    public int setQty(HttpSession session, Long productId, int qty) {
        if (qty <= 0) {
            remove(session, productId);
            return 0;
        }
        Product p = productService.findById(productId);
        if (p == null) {
            remove(session, productId);
            return 0;
        }
        int stock = (p.getStock() == null) ? 0 : p.getStock();
        int next = Math.min(qty, Math.min(stock <= 0 ? qty : stock, MAX_QTY_PER_ITEM));
        getCart(session).put(productId, next);
        return next;
    }

    public void remove(HttpSession session, Long productId) {
        getCart(session).remove(productId);
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    public int count(HttpSession session) {
        return getCart(session).values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isEmpty(HttpSession session) {
        return getCart(session).isEmpty();
    }

    /**
     * 清理失效条目（商品已删除 / 已下架缺货），返回被移除的数量。
     * 结算前调用，避免"购物车里有东西却下出空单"。
     */
    public int pruneInvalid(HttpSession session) {
        Map<Long, Integer> cart = getCart(session);
        int removed = 0;
        for (Long id : new ArrayList<>(cart.keySet())) {
            Product p = productService.findById(id);
            if (p == null || (p.getStock() != null && p.getStock() <= 0)) {
                cart.remove(id);
                removed++;
            }
        }
        return removed;
    }
}
