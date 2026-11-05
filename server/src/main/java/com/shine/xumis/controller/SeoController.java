package com.shine.xumis.controller;

import com.shine.xumis.model.Product;
import com.shine.xumis.service.ContentPageService;
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
    private final ContentPageService contentPages;

    /** 后台隐藏入口（见 AdminPathFilter）；未启用时为 /admin */
    @Value("${app.admin-path:}")
    private String adminPath;

    public SeoController(ProductService productService, ContentPageService contentPages) {
        this.productService = productService;
        this.contentPages = contentPages;
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public void sitemap(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String base = baseUrl(request);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        String today = java.time.LocalDate.now().toString();
        List<String> staticPages = new java.util.ArrayList<>(List.of("/", "/products", "/custom", "/inquiry"));
        // 内容页（P2-5）与英文独立 URL（P2-2）一并进 sitemap
        for (com.shine.xumis.model.ContentPage cp : contentPages.published()) {
            staticPages.add("/p/" + cp.getSlug());
        }
        for (String p : staticPages) {
            sb.append("  <url><loc>").append(base).append(p).append("</loc>")
              .append("<lastmod>").append(today).append("</lastmod>")
              .append("<changefreq>weekly</changefreq>")
              .append("<priority>").append("/".equals(p) ? "1.0" : "0.8").append("</priority>")
              .append("</url>\n");
            sb.append("  <url><loc>").append(base).append("/en").append(p).append("</loc>")
              .append("<lastmod>").append(today).append("</lastmod>")
              .append("<changefreq>weekly</changefreq><priority>0.6</priority></url>\n");
        }
        List<Product> products = productService.findAll();
        for (Product p : products) {
            sb.append("  <url><loc>").append(base).append("/product/").append(p.getId())
              .append("</loc><lastmod>").append(today).append("</lastmod>")
              .append("<changefreq>weekly</changefreq><priority>0.7</priority>");
            // 图片进 sitemap：Google 图片搜索可收录商品主图
            String img = p.getImageUrl();
            if (img != null && !img.isBlank()) {
                String abs = img.startsWith("http") ? img : base + (img.startsWith("/") ? img : "/" + img);
                sb.append("<image:image xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">")
                  .append("<image:loc>").append(abs).append("</image:loc>")
                  .append("<image:title>").append(escapeXml(p.getName())).append("</image:title>")
                  .append("</image:image>");
            }
            sb.append("</url>\n");
            sb.append("  <url><loc>").append(base).append("/en/product/").append(p.getId())
              .append("</loc><lastmod>").append(today).append("</lastmod>")
              .append("<changefreq>weekly</changefreq><priority>0.6</priority></url>\n");
        }
        sb.append("</urlset>");
        write(response, "application/xml", sb.toString());
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain")
    public void robots(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String p = (adminPath == null) ? "" : adminPath.trim();
        String adminDisallow = (p.isEmpty() || p.equals("admin") || p.equals("/admin"))
                ? "/admin" : (p.startsWith("/") ? p : "/" + p);
        String txt = "User-agent: *\nAllow: /\nDisallow: " + adminDisallow
                + "\nDisallow: /h2-console\nDisallow: /cart/checkout"
                + "\n\nSitemap: " + baseUrl(request) + "/sitemap.xml\n";
        write(response, "text/plain", txt);
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
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
