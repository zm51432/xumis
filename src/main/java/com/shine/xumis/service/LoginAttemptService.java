package com.shine.xumis.service;

import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一账号连续失败 N 次后锁定一段时间，抵御口令爆破。
 * 单机内存实现（单实例部署够用）；多实例部署时应换成 Redis 计数。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 10 * 60 * 1000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 5 * 60 * 1000L;

    private static final class Attempt {
        int failures;
        long lockedUntil;
        long lastTouch;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    public boolean isLocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        return a.lockedUntil > System.currentTimeMillis();
    }

    /** 剩余锁定秒数（未锁定返回 0） */
    public long remainingSeconds(String key) {
        Attempt a = attempts.get(key);
        if (a == null || a.lockedUntil <= System.currentTimeMillis()) {
            return 0;
        }
        return (a.lockedUntil - System.currentTimeMillis()) / 1000 + 1;
    }

    public void recordFailure(String key) {
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        a.failures++;
        a.lastTouch = System.currentTimeMillis();
        if (a.failures >= MAX_FAILURES) {
            a.lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
        }
        maybeCleanup();
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    /** 清理长时间未活动的记录，避免内存无限增长 */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCleanup = now;
        Iterator<Map.Entry<String, Attempt>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Attempt> e = it.next();
            Attempt a = e.getValue();
            boolean expiredLock = a.lockedUntil > 0 && a.lockedUntil < now;
            boolean stale = now - a.lastTouch > LOCK_MILLIS * 2;
            if (expiredLock || stale) {
                it.remove();
            }
        }
    }
}
