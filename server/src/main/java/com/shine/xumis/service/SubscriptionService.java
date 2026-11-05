package com.shine.xumis.service;

import com.shine.xumis.model.Subscription;
import com.shine.xumis.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 邮件订阅（P1-5）：欢迎邮件 + 一次性令牌退订 + 重新订阅。 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final MailService mail;
    private final SettingsService settings;

    @Value("${app.site-base-url:https://shine.xumois.cn}")
    private String siteBaseUrl;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               MailService mail, SettingsService settings) {
        this.subscriptionRepository = subscriptionRepository;
        this.mail = mail;
        this.settings = settings;
    }

    /** 订阅邮箱；已订阅且未退订返回 false；退订过则重新激活并再发一封欢迎信 */
    public boolean subscribe(String email) {
        if (email == null || email.isBlank()) return false;
        String addr = email.trim().toLowerCase();
        Subscription s = subscriptionRepository.findByEmail(addr).orElse(null);
        if (s != null) {
            if ("ACTIVE".equalsIgnoreCase(s.getStatus())) return false;
            s.setStatus("ACTIVE");
            s.setUnsubscribedAt(null);
            s.setUnsubscribeToken(java.util.UUID.randomUUID().toString().replace("-", "")
                    + java.util.UUID.randomUUID().toString().replace("-", ""));
            subscriptionRepository.save(s);
        } else {
            s = subscriptionRepository.save(new Subscription(addr));
        }
        sendWelcome(s);
        return true;
    }

    private void sendWelcome(Subscription s) {
        String store = settings.getOr(SettingsService.SITE_NAME, "shine星心");
        String unsub = siteBaseUrl + "/unsubscribe?token=" + s.getUnsubscribeToken();
        String html = "<div style=\"font-family:Arial,'Microsoft YaHei',sans-serif;max-width:600px;margin:auto;color:#33281f;line-height:1.8\">"
                + "<div style=\"background:#FFF3C4;border:1px solid #F0D98A;border-radius:12px;padding:20px 24px\">"
                + "<h2 style=\"margin:0;color:#B71C1C\">欢迎订阅 " + esc(store) + "</h2></div>"
                + "<p>你好，感谢订阅！我们会不定期寄送新品上架、手作小故事与订阅专属优惠，不会频繁打扰。</p>"
                + "<p>本店铺只展示真实售出量，不展示用户评价，也不会把你的邮箱交给第三方。</p>"
                + "<p><a href=\"" + siteBaseUrl + "/products\" style=\"color:#B71C1C\">去看看新品 →</a></p>"
                + "<p style=\"color:#999;font-size:12px;border-top:1px solid #eee;padding-top:12px\">"
                + "如不想再收到这类邮件，可随时 <a href=\"" + unsub + "\" style=\"color:#B71C1C\">一键退订</a>。"
                + "此链接为你的专属退订入口，请勿转发。</p></div>";
        mail.sendHtml(s.getEmail(), store + " · 订阅成功", html, "订阅欢迎信");
    }

    public Subscription findByToken(String token) {
        if (token == null || token.isBlank()) return null;
        return subscriptionRepository.findAll().stream()
                .filter(s -> token.equals(s.getUnsubscribeToken()) && "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .findFirst().orElse(null);
    }

    public boolean unsubscribe(String token) {
        Subscription s = findByToken(token);
        if (s == null) return false;
        s.setStatus("UNSUBSCRIBED");
        s.setUnsubscribedAt(LocalDateTime.now());
        subscriptionRepository.save(s);
        return true;
    }

    public long countAll() { return subscriptionRepository.count(); }

    public long countActive() {
        return subscriptionRepository.findAll().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).count();
    }

    public List<Subscription> findAll() { return subscriptionRepository.findAll(); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
