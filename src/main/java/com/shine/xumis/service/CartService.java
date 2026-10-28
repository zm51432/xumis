package com.shine.xumis.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 购物车服务：基于 HttpSession 的轻量购物车（无需登录即可下单，对标跨境独立站 guest checkout）。
 * 结构：Map<productId, quantity>
 */
@Service
public class CartService {

    public static final String SESSION_KEY = "SHINE_CART";

    @SuppressWarnings("unchecked")
    public Map<Long, Integer> getCart(HttpSession session) {
        Map<Long, Integer> cart = (Map<Long, Integer>) session.getAttribute(SESSION_KEY);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(SESSION_KEY, cart);
        }
        return cart;
    }

    public void add(HttpSession session, Long productId, int qty) {
        if (qty <= 0) return;
        Map<Long, Integer> cart = getCart(session);
        cart.put(productId, cart.getOrDefault(productId, 0) + qty);
    }

    public void setQty(HttpSession session, Long productId, int qty) {
        if (qty <= 0) {
            remove(session, productId);
            return;
        }
        getCart(session).put(productId, qty);
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
}
