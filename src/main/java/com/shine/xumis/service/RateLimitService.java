package com.shine.xumis.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易提交频率限制（按 IP + 动作维度统计时间窗内的次数）。
 * 用于评价、订阅、询盘、来图定制这类公开写入接口，防止被脚本刷屏。
 * 单机内存实现；多实例部署时换成 Redis。
 */
@Service
public class RateLimitService {

    private static final long CLEANUP_INTERVAL_MILLIS = 10 * 60 * 1000L;

    private final Map<String, List<Long>> hits = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    /**
     * @param key      维度键（如 "review:1.2.3.4"）
     * @param max      时间窗内允许的次数
     * @param windowMs 时间窗长度（毫秒）
     * @return true = 允许本次提交
     */
    public synchronized boolean allow(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        List<Long> list = hits.computeIfAbsent(key, k -> new ArrayList<>());
        list.removeIf(t -> now - t > windowMs);
        if (list.size() >= max) {
            maybeCleanup(now);
            return false;
        }
        list.add(now);
        maybeCleanup(now);
        return true;
    }

    /** 距离可以再次提交还剩多少秒 */
    public long retryAfterSeconds(String key, int max, long windowMs) {
        List<Long> list = hits.get(key);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long oldest = list.stream().mapToLong(Long::longValue).min().orElse(now);
        return Math.max(1, (windowMs - (now - oldest)) / 1000 + 1);
    }

    private void maybeCleanup(long now) {
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCleanup = now;
        Iterator<Map.Entry<String, List<Long>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<Long>> e = it.next();
            e.getValue().removeIf(t -> now - t > 60 * 60 * 1000L);
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }
}
