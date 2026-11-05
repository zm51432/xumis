package com.shine.xumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码（注册真人校验）：内存存储，单实例部署足够。
 * <ul>
 *   <li>验证码 6 位，有效期 10 分钟；</li>
 *   <li>同一邮箱 60 秒内只能重发一次，防刷；</li>
 *   <li>最多尝试验证 5 次，超限强制失效，需重新获取；</li>
 *   <li>验证码一次性使用，校验成功即删除。</li>
 * </ul>
 */
@Service
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);

    private static final long CODE_TTL_SECONDS = 10 * 60;        // 验证码有效期 10 分钟
    private static final long RESEND_COOLDOWN_SECONDS = 60;      // 同一邮箱 60 秒重发冷却
    private static final int MAX_VERIFY_ATTEMPTS = 5;            // 最多验证尝试次数
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static final class Entry {
        final String code;
        final long expiryEpochSecond;
        final long lastSentEpochSecond;
        int attempts;

        Entry(String code, long now) {
            this.code = code;
            this.expiryEpochSecond = now + CODE_TTL_SECONDS;
            this.lastSentEpochSecond = now;
            this.attempts = 0;
        }
    }

    public enum SendResult { OK, INVALID_EMAIL, RATE_LIMITED }

    public static final class SendOutcome {
        public final SendResult result;
        public final String code; // 仅当 result==OK 时有值

        private SendOutcome(SendResult result, String code) {
            this.result = result;
            this.code = code;
        }
    }

    /** 生成并保存验证码；返回结果与验证码（OK 时供发信用）。 */
    public SendOutcome send(String email) {
        String mail = normalize(email);
        if (mail == null) {
            return new SendOutcome(SendResult.INVALID_EMAIL, null);
        }
        long now = Instant.now().getEpochSecond();
        Entry existing = store.get(mail);
        if (existing != null && (now - existing.lastSentEpochSecond) < RESEND_COOLDOWN_SECONDS) {
            return new SendOutcome(SendResult.RATE_LIMITED, null);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(mail, new Entry(code, now));
        log.info("[验证码] 已为 {} 生成验证码（重发冷却中，10 分钟内有效）", mail);
        return new SendOutcome(SendResult.OK, code);
    }

    /** 校验验证码；成功则消费（删除），失败累计尝试次数，超限强制失效。 */
    public boolean verify(String email, String code) {
        String mail = normalize(email);
        if (mail == null || code == null || code.isBlank()) {
            return false;
        }
        Entry e = store.get(mail);
        if (e == null) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        if (now > e.expiryEpochSecond) {
            store.remove(mail);
            return false;
        }
        e.attempts++;
        if (!e.code.equals(code.trim())) {
            if (e.attempts >= MAX_VERIFY_ATTEMPTS) {
                store.remove(mail);
            }
            return false;
        }
        store.remove(mail); // 一次性使用
        return true;
    }

    private static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String s = email.trim().toLowerCase();
        if (!s.contains("@") || s.length() > 200) {
            return null;
        }
        return s;
    }
}
