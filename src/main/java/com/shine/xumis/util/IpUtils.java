package com.shine.xumis.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端地址与来源解析。
 *
 * <p>站点在 Nginx 后面，直接取 {@code getRemoteAddr()} 拿到的是 127.0.0.1，
 * 因此优先读取反代写入的 X-Forwarded-For / X-Real-IP。</p>
 */
public final class IpUtils {

    private IpUtils() {
    }

    /** 取客户端真实 IP（兼容反向代理） */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF 可能是 "ip1, ip2, ip3"，第一个为原始客户端
            return xff.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        String ip = request.getRemoteAddr();
        return ip == null ? "" : ip;
    }

    /** 把 User-Agent 压缩成「设备 · 浏览器」的短串，便于后台列表展示 */
    public static String shortUserAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return "-";
        }
        String device = "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad")) {
            device = lower.contains("ipad") ? "iPad" : "iPhone";
        } else if (lower.contains("android")) {
            device = "Android";
        } else if (lower.contains("mac os x") || lower.contains("macintosh")) {
            device = "Mac";
        } else if (lower.contains("windows")) {
            device = "Windows";
        } else if (lower.contains("linux")) {
            device = "Linux";
        }

        String browser = "浏览器";
        if (lower.contains("edg/") || lower.contains("edge/")) {
            browser = "Edge";
        } else if (lower.contains("chrome/") && !lower.contains("chromium")) {
            browser = "Chrome";
        } else if (lower.contains("firefox/")) {
            browser = "Firefox";
        } else if (lower.contains("safari/") && !lower.contains("chrome")) {
            browser = "Safari";
        }
        return device + " · " + browser;
    }
}
