package com.shine.xumis.service;

import com.shine.xumis.model.Product;
import com.shine.xumis.model.VisitEvent;
import com.shine.xumis.repository.ProductRepository;
import com.shine.xumis.repository.VisitEventRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** 轻量站内分析（P2-3）：浏览 / 加购 / 下单漏斗，不接第三方、不存原始 IP。 */
@Service
public class AnalyticsService {

    private final VisitEventRepository repo;
    private final ProductRepository productRepo;
    private final SettingsService settings;

    public AnalyticsService(VisitEventRepository repo, ProductRepository productRepo, SettingsService settings) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.settings = settings;
    }

    public static String hash(String raw) {
        if (raw == null) return "anon";
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) { return "anon"; }
    }

    public void record(String type, String path, Long productId, String sessionId) {
        if (!settings.getBool("analytics.enabled")) return;
        if (path == null) path = "";
        if (path.length() > 300) path = path.substring(0, 300);
        try {
            repo.save(new VisitEvent(type, path, productId, hash(sessionId)));
        } catch (Exception ignored) {
            // 埋点失败不得影响主流程
        }
    }

    public Map<String, Object> report(int days) {
        int d = Math.max(1, Math.min(days, 90));
        String from = LocalDate.now().minusDays(d - 1).toString();
        Map<String, Object> out = new LinkedHashMap<>();
        long pv = 0, uv = 0, add = 0, orders = 0, subs = 0;
        List<Map<String, Object>> daily = new ArrayList<>();
        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (Object[] row : repo.dailyStats(from)) {
            String day = String.valueOf(row[0]);
            String type = String.valueOf(row[1]);
            long cnt = ((Number) row[2]).longValue();
            long uniq = ((Number) row[3]).longValue();
            long[] v = byDay.computeIfAbsent(day, k -> new long[5]);
            switch (type) {
                case "VIEW" -> { v[0] += cnt; v[1] += uniq; pv += cnt; }
                case "ADD_CART" -> { v[2] += cnt; add += cnt; }
                case "ORDER" -> { v[3] += cnt; orders += cnt; }
                case "SUBSCRIBE" -> { v[4] += cnt; subs += cnt; }
                default -> {}
            }
        }
        // 单独算整段 UV（按天相加会重复计人）
        uv = 0;
        for (LocalDate date = LocalDate.now().minusDays(d - 1); !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            uv += repo.countDistinctVisitor(date.toString(), "VIEW");
        }
        byDay.forEach((day, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", day); m.put("pv", v[0]); m.put("uv", v[1]);
            m.put("cart", v[2]); m.put("orders", v[3]); m.put("subs", v[4]);
            daily.add(m);
        });
        daily.sort((a, b) -> String.valueOf(b.get("day")).compareTo(String.valueOf(a.get("day"))));
        out.put("days", d);
        out.put("pv", pv); out.put("uv", uv); out.put("addCart", add);
        out.put("orders", orders); out.put("subs", subs);
        out.put("cartRate", uv == 0 ? 0 : Math.round(add * 10000.0 / uv) / 100.0);
        out.put("orderRate", uv == 0 ? 0 : Math.round(orders * 10000.0 / uv) / 100.0);
        out.put("daily", daily);

        List<Map<String, Object>> topPaths = new ArrayList<>();
        for (Object[] r : repo.topPaths(from)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", r[0]); m.put("count", r[1]); topPaths.add(m);
        }
        out.put("topPaths", topPaths);

        List<Map<String, Object>> topProducts = new ArrayList<>();
        for (Object[] r : repo.topProducts(from)) {
            if (r[0] == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            long pid = ((Number) r[0]).longValue();
            m.put("id", pid);
            m.put("name", productRepo.findById(pid).map(Product::getName).orElse("已删除商品"));
            m.put("count", r[1]);
            topProducts.add(m);
        }
        out.put("topProducts", topProducts);
        return out;
    }
}
