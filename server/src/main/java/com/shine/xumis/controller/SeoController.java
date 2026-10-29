package com.shine.xumis.controller;

import com.shine.xumis.model.Product;
import com.shine.xumis.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.util.List;

/**
 * SEO 端点：sitemap.xml 与 robots.txt（主流独立站标配，利于搜索引擎收录）。
 */
@Controller
public class SeoController {

    private final ProductService productService;

    /** 后台隐藏入口（见 AdminPathFilter）；未启用时为 /admin */
    @Value("${app.admin-path:}")
    private String adminPath;

    public SeoController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public void sitemap(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String base = baseUrl(request);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        String[] staticPages = {"/", "/products", "/custom", "/inquiry"};
        for (String p : staticPages) {
            sb.append("  <url><loc>").append(base).append(p).append("</loc>")
              .append("<changefreq>weekly</changefreq></url>\n");
        }
        List<Product> products = productService.findAll();
        for (Product p : products) {
            sb.append("  <url><loc>").append(base).append("/product/").append(p.getId())
              .append("</loc><changefreq>weekly</changefreq></url>\n");
        }
        sb.append("</urlset>");
        write(response, "application/xml", sb.toString());
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain")
    public void robots(HttpServletResponse response) throws IOException {
        String p = (adminPath == null) ? "" : adminPath.trim();
        String adminDisallow = (p.isEmpty() || p.equals("admin") || p.equals("/admin"))
                ? "/admin" : (p.startsWith("/") ? p : "/" + p);
        String txt = "User-agent: *\nAllow: /\nDisallow: " + adminDisallow
                + "\nDisallow: /h2-console\nDisallow: /cart/checkout\nSitemap: /sitemap.xml\n";
        write(response, "text/plain", txt);
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String base = scheme + "://" + host;
        if (!((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
            base += ":" + port;
        }
        return base;
    }

    private void write(HttpServletResponse response, String contentType, String body) throws IOException {
        response.setContentType(contentType + "; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
