package com.shine.xumis.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 邮件发送：邮箱验证 / 找回密码。
 * 若未配置 MAIL_HOST，则降级为仅把验证/重置链接打到服务端日志（便于无 SMTP 时联调），不真正发信。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String siteBaseUrl;
    private final boolean enabled;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${MAIL_FROM:}") String fromOverride,
                       @Value("${app.site-base-url:https://shine.xumois.cn}") String siteBaseUrl) {
        this.mailSender = mailSender;
        this.from = (fromOverride != null && !fromOverride.isBlank()) ? fromOverride
                : (username != null && !username.isBlank()) ? username : "no-reply@xumois.cn";
        this.siteBaseUrl = siteBaseUrl;
        this.enabled = host != null && !host.isBlank();
    }

    /** 发送邮箱验证邮件；返回拼接好的验证链接（便于降级时回显） */
    public String sendVerification(String email, String token) {
        String link = siteBaseUrl + "/verify?token=" + token;
        String subject = "请验证你的邮箱 - shine星心";
        String body = "你好，\n\n" +
                "感谢注册 shine星心。请点击下面的链接完成邮箱验证（24 小时内有效）：\n" +
                link + "\n\n" +
                "如果这不是你本人的操作，请忽略此邮件。\n\n" +
                "shine星心 团队";
        deliver(email, subject, body, "验证邮箱", link);
        return link;
    }

    /** 发送找回密码邮件；返回拼接好的重置链接 */
    public String sendPasswordReset(String email, String token) {
        String link = siteBaseUrl + "/reset-password?token=" + token;
        String subject = "重置你的密码 - shine星心";
        String body = "你好，\n\n" +
                "我们收到了你的密码重置请求。请点击下面的链接设置新密码（1 小时内有效）：\n" +
                link + "\n\n" +
                "如果这不是你本人的操作，请忽略此邮件，你的密码不会变更。\n\n" +
                "shine星心 团队";
        deliver(email, subject, body, "找回密码", link);
        return link;
    }

    /** 发送注册邮箱验证码；返回是否真正发出（false=降级仅日志）。验证码 10 分钟内有效。 */
    public boolean sendCode(String email, String code) {
        String subject = "你的注册验证码 - shine星心";
        String body = "你好，\n\n" +
                "你正在注册 shine星心，你的邮箱验证码是：" + code + "\n" +
                "验证码 10 分钟内有效，请勿泄露给他人。\n\n" +
                "如果这不是你本人的操作，请忽略此邮件。\n\n" +
                "shine星心 团队";
        deliver(email, subject, body, "邮箱验证码", code);
        return enabled;
    }

    /**
     * 发送 HTML 邮件（P0-2 订单通知 / P1-5 订阅欢迎信复用）。
     * 与验证码一样走线程池异步发送，失败只记日志，绝不影响下单主流程。
     */
    public boolean sendHtml(String to, String subject, String html, String kind) {
        if (!enabled) {
            log.warn("[MailService 降级] 未配置 MAIL_HOST，未发送 HTML 邮件：{} -> {}", kind, to);
            return false;
        }
        pool.submit(() -> {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(msg);
                log.info("HTML 邮件已发送：{} -> {}", kind, to);
            } catch (Exception e) {
                log.error("HTML 邮件发送失败：{} -> {}：{}", kind, to, e.getMessage());
            }
        });
        return true;
    }

    private void deliver(String to, String subject, String text, String kind, String link) {
        if (!enabled) {
            // 降级：不真正发信，仅把完整链接打到日志，方便无 SMTP 时联调
            log.warn("[MailService 降级] 未配置 MAIL_HOST，未真正发信。{} 收件人 {} 链接：{}", kind, to, link);
            return;
        }
        pool.submit(() -> {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text, false);
                mailSender.send(msg);
                log.info("邮件已发送：{} -> {}", kind, to);
            } catch (Exception e) {
                log.error("邮件发送失败：{} -> {}：{}", kind, to, e.getMessage());
            }
        });
    }
}
