package com.shine.xumis.util;

import jakarta.servlet.http.HttpSession;

import java.security.MessageDigest;
import java.util.UUID;

/**
 * CSRF 令牌：每个会话一个随机 token，写操作（POST/PUT/DELETE）必须携带。
 * 页面通过 <meta name="_csrf"> 暴露给前端，表单由 JS 自动注入隐藏域，
 * fetch 请求通过 X-CSRF-Token 头携带。
 */
public final class Csrf {

    public static final String SESSION_ATTR = "CSRF_TOKEN";
    public static final String PARAM = "_csrf";
    public static final String HEADER = "X-CSRF-Token";

    private Csrf() {
    }

    /** 取（必要时生成）当前会话的令牌 */
    public static String token(HttpSession session) {
        if (session == null) {
            return "";
        }
        String t = (String) session.getAttribute(SESSION_ATTR);
        if (t == null || t.isBlank()) {
            t = UUID.randomUUID().toString().replace("-", "");
            session.setAttribute(SESSION_ATTR, t);
        }
        return t;
    }

    /** 常量时间比较，避免时序侧信道 */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
