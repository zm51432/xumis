package com.shine.xumis.config;

import com.shine.xumis.util.Csrf;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * CSRF 防护：所有写请求（POST / PUT / DELETE）必须携带与会话一致的令牌。
 * 放行 /api/**：接口走 API Key 鉴权，不是浏览器表单发起，不适用 CSRF 模型。
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private static final String SAFE_METHOD = "GET,HEAD,OPTIONS,TRACE";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        String path = request.getRequestURI();

        // 接口类请求放行
        if (path != null && path.startsWith("/api/")) {
            return true;
        }

        // 安全方法：只需保证令牌已生成，便于页面渲染
        if (method != null && SAFE_METHOD.contains(method.toUpperCase())) {
            Csrf.token(request.getSession(true));
            return true;
        }

        HttpSession session = request.getSession(false);
        String expected = (session == null) ? null : (String) session.getAttribute(Csrf.SESSION_ATTR);
        String actual = request.getParameter(Csrf.PARAM);
        if (actual == null || actual.isBlank()) {
            actual = request.getHeader(Csrf.HEADER);
        }

        if (expected == null || !Csrf.matches(expected, actual)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<!doctype html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                            + "<title>403 请求被拒绝</title></head><body style=\"font-family:system-ui;padding:60px;text-align:center\">"
                            + "<h2>403 · 请求已过期</h2><p>表单安全校验未通过，请返回上一页刷新后重试。</p>"
                            + "<a href=\"/\">返回首页</a></body></html>");
            return false;
        }
        return true;
    }
}
