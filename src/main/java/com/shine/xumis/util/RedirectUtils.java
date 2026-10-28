package com.shine.xumis.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 工具：安全地解析跳转目标，避免"开放重定向"（Open Redirect）风险。
 * 只允许站内相对路径（以 / 开头且不以 // 开头，防止被解析为外部域名）。
 */
public final class RedirectUtils {

    private RedirectUtils() {
    }

    /**
     * @param redirect 用户传入的跳转参数（可能为 null / 空白 / 外部地址）
     * @param request  当前请求，用于回退到 Referer
     * @return 安全的站内相对路径，兜底为 "/"
     */
    public static String safe(String redirect, HttpServletRequest request) {
        String back = null;
        if (redirect != null && !redirect.isBlank() && isRelative(redirect)) {
            back = redirect;
        } else if (request != null) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                // Referer 形如 http://host/path，只取 path 之后的部分
                String path = referer.replaceFirst("^\\w+://[^/]+", "");
                if (isRelative(path)) {
                    back = path;
                }
            }
        }
        return (back != null) ? back : "/";
    }

    private static boolean isRelative(String p) {
        return p.startsWith("/") && !p.startsWith("//") && !p.startsWith("/\\");
    }
}
