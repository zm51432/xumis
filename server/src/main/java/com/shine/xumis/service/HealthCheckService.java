package com.shine.xumis.service;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.SiteCheck;
import com.shine.xumis.repository.ProductRepository;
import com.shine.xumis.repository.SiteCheckRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 站点自动化检测。
 *
 * <p>每 15 分钟（可用 app.health-interval-ms 调整）对关键页面做一次端到端探测：
 * 真实发 HTTP 请求到自己，校验状态码 + 关键内容标记，并单独 check 数据库。
 * 结果落库 {@code site_check}，后台 /admin/health 可看当前状态与历史，异常项会写日志。</p>
 *
 * <p>为什么不用外部监控：这套检测跟着应用走，部署即生效；需要外部视角时，
 * 服务器上另有 systemd timer 定时 curl /api/health，双重保险。</p>
 */
@Service
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    /** 检测项：显示名 / 路径 / 必须出现的内容标记 */
    private record Target(String name, String path, String marker) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target("首页", "/", "shine"),
            new Target("商品列表", "/products", "product"),
            new Target("来图定制", "/custom", "custom"),
            new Target("询盘页", "/inquiry", "inquiry"),
            new Target("登录页", "/login", "login"),
            new Target("sitemap.xml", "/sitemap.xml", "<urlset"),
            new Target("robots.txt", "/robots.txt", "Sitemap"),
            new Target("样式表", "/css/style.css", "--"),
            new Target("健康接口", "/api/health", "\"ok\"")
    );

    private final SiteCheckRepository siteCheckRepository;
    private final ProductRepository productRepository;

    @Value("${server.port:8080}")
    private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile List<SiteCheck> lastResults = List.of();
    private volatile LocalDateTime lastRunAt;

    public HealthCheckService(SiteCheckRepository siteCheckRepository, ProductRepository productRepository) {
        this.siteCheckRepository = siteCheckRepository;
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void warmup() {
        // 启动时先给页面一个稳定期，再由定时任务接管
        lastRunAt = null;
    }

    @Scheduled(initialDelay = 90_000, fixedDelayString = "${app.health-interval-ms:900000}")
    public void scheduled() {
        try {
            run();
        } catch (Exception e) {
            log.warn("站点自检执行失败：{}", e.toString());
        }
    }

    /** 立即执行一轮检测并落库 */
    @Transactional
    public List<SiteCheck> run() {
        List<SiteCheck> results = new ArrayList<>();
        results.add(checkDatabase());
        for (Target t : TARGETS) {
            results.add(checkHttp(t));
        }
        results.add(checkErp());
        // 商品详情页要拿真实 id：没有商品时跳过（数据库项已经会报异常）
        Long pid = firstProductId();
        if (pid != null) {
            results.add(checkHttp(new Target("商品详情", "/product/" + pid, "product")));
        }
        try {
            siteCheckRepository.saveAll(results);
            siteCheckRepository.deleteByCheckedAtBefore(LocalDateTime.now().minusDays(30));
        } catch (Exception e) {
            log.warn("自检结果落库失败：{}", e.toString());
        }
        lastResults = results;
        lastRunAt = LocalDateTime.now();
        long failed = results.stream().filter(r -> !r.isOk()).count();
        if (failed > 0) {
            log.warn("站点自检：{} 项检测中 {} 项异常", results.size(), failed);
        } else {
            log.info("站点自检通过：{} 项全部正常", results.size());
        }
        return results;
    }

    /** ERP 独立服务探测（8096，不经过网站端口） */
    private SiteCheck checkErp() {
        long t0 = System.currentTimeMillis();
        String url = "http://127.0.0.1:8096/erp/api/health";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() == 200
                    && resp.body() != null && resp.body().contains("\"ok\":true");
            return new SiteCheck("ERP 服务", "/erp/api/health", ok, resp.statusCode(),
                    (int) (System.currentTimeMillis() - t0), ok ? "正常" : "ERP 未响应");
        } catch (Exception e) {
            return new SiteCheck("ERP 服务", "/erp/api/health", false, 0,
                    (int) (System.currentTimeMillis() - t0), "连接失败：" + e.getClass().getSimpleName());
        }
    }

    private SiteCheck checkDatabase() {
        long t0 = System.currentTimeMillis();
        try {
            long count = productRepository.count();
            int ms = (int) (System.currentTimeMillis() - t0);
            boolean ok = count > 0;
            return new SiteCheck("数据库", "product 表", ok, 200, ms,
                    ok ? "商品 " + count + " 件" : "商品表为空，前台会没有内容");
        } catch (Exception e) {
            return new SiteCheck("数据库", "product 表", false, 0,
                    (int) (System.currentTimeMillis() - t0), "连接失败：" + e.getMessage());
        }
    }

    private SiteCheck checkHttp(Target t) {
        long t0 = System.currentTimeMillis();
        String path = t.path();
        // 商品详情需要一个真实 id；取不到就跳过该项
        if (path.startsWith("/product/")) {
            path = path.replace("{id}", "");
        }
        String url = "http://127.0.0.1:" + port + path;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "shine-health-check")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int ms = (int) (System.currentTimeMillis() - t0);
            String body = resp.body() == null ? "" : resp.body();
            boolean ok = resp.statusCode() == 200
                    && (t.marker() == null || body.contains(t.marker()));
            String msg = ok ? "正常" : ("状态 " + resp.statusCode() + (body.contains(t.marker()) ? "" : " · 内容缺少标记"));
            return new SiteCheck(t.name(), path, ok, resp.statusCode(), ms, msg);
        } catch (Exception e) {
            int ms = (int) (System.currentTimeMillis() - t0);
            return new SiteCheck(t.name(), path, false, 0, ms, "请求失败：" + e.getClass().getSimpleName());
        }
    }

    /** 最近一轮检测结果：服务重启后内存为空，就取库里最新一轮（同一批次 15 秒内） */
    public List<SiteCheck> lastResults() {
        if (!lastResults.isEmpty()) return lastResults;
        List<SiteCheck> all = siteCheckRepository.findTop100ByOrderByIdDesc();
        if (all.isEmpty()) return List.of();
        java.time.LocalDateTime latest = all.get(0).getCheckedAt();
        List<SiteCheck> round = new ArrayList<>();
        for (SiteCheck c : all) {
            if (c.getCheckedAt() == null) continue;
            long diff = Math.abs(java.time.Duration.between(c.getCheckedAt(), latest).getSeconds());
            if (diff <= 15) round.add(c);
        }
        return round;
    }

    public LocalDateTime lastRunAt() {
        if (lastRunAt != null) return lastRunAt;
        List<SiteCheck> last = lastResults();
        return last.isEmpty() ? null : last.get(0).getCheckedAt();
    }

    public boolean healthy() {
        return !lastResults.isEmpty() && lastResults.stream().allMatch(SiteCheck::isOk);
    }

    public List<SiteCheck> history(int limit) {
        return siteCheckRepository.findTop100ByOrderByIdDesc().stream().limit(Math.max(limit, 1)).toList();
    }

    /** 首个商品 id（商品详情检测用） */
    public Long firstProductId() {
        try {
            return productRepository.findAll(PageRequest.of(0, 1)).getContent().stream()
                    .map(Product::getId).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
