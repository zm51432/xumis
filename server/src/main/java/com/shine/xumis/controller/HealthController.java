package com.shine.xumis.controller;

import com.shine.xumis.model.SiteCheck;
import com.shine.xumis.service.HealthCheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开健康接口：给外部监控 / 系统自检 / 运维脚本用。
 * 只暴露「是否正常 + 各项概要」，不含任何敏感信息。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthCheckService health;

    public HealthController(HealthCheckService health) {
        this.health = health;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<SiteCheck> checks = new ArrayList<>(health.lastResults());
        Map<String, Object> body = new LinkedHashMap<>();
        boolean pending = checks.isEmpty();
        boolean ok = !pending && checks.stream().allMatch(SiteCheck::isOk);
        body.put("ok", ok);
        body.put("pending", pending);
        body.put("checkedAt", health.lastRunAt() == null ? "" : health.lastRunAt().toString());
        body.put("total", checks.size());
        body.put("passed", checks.stream().filter(SiteCheck::isOk).count());

        List<Map<String, Object>> items = new ArrayList<>();
        for (SiteCheck c : checks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("target", c.getTarget());
            m.put("ok", c.isOk());
            m.put("code", c.getStatusCode());
            m.put("ms", c.getCostMs());
            m.put("message", c.getMessage());
            items.add(m);
        }
        body.put("checks", items);
        return body;
    }
}
