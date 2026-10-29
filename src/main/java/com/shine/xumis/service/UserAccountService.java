package com.shine.xumis.service;

import com.shine.xumis.model.UserAccount;
import com.shine.xumis.repository.UserAccountRepository;
import com.shine.xumis.util.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * C 端账号服务：注册、登录、资料维护、邮箱验证、找回密码。
 */
@Service
public class UserAccountService {

    public static final String SESSION_KEY = "USER";

    /** 验证令牌有效期：24 小时 */
    private static final int VERIFY_TOKEN_HOURS = 24;
    /** 重置令牌有效期：1 小时 */
    private static final int RESET_TOKEN_HOURS = 1;

    private final UserAccountRepository userAccountRepository;
    private final MailService mailService;

    public UserAccountService(UserAccountRepository userAccountRepository, MailService mailService) {
        this.userAccountRepository = userAccountRepository;
        this.mailService = mailService;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return userAccountRepository.findByEmail(email == null ? "" : email.trim().toLowerCase());
    }

    public List<UserAccount> findAll() {
        return userAccountRepository.findAll();
    }

    public long count() {
        return userAccountRepository.count();
    }

    /** 注册：邮箱重复或口令过短返回空。成功后生成验证令牌并发送验证邮件（未验证前仍可登录，仅标记未验证）。 */
    @Transactional
    public Optional<UserAccount> register(String email, String rawPassword, String name,
                                          String ip, String userAgent) {
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (!mail.contains("@") || rawPassword == null || rawPassword.length() < 8) {
            return Optional.empty();
        }
        if (userAccountRepository.findByEmail(mail).isPresent()) {
            return Optional.empty();
        }
        UserAccount u = new UserAccount(mail, PasswordHasher.hash(rawPassword),
                (name == null || name.isBlank()) ? mail.substring(0, mail.indexOf('@')) : name.trim());
        u.setEmailVerified(false);
        // 记录注册来源，并把「注册成功即登录」的这条登录也记下来
        u.setRegisterIp(ip);
        LocalDateTime now = LocalDateTime.now();
        u.setLastLoginAt(now);
        u.setLastLoginIp(ip);
        u.setLastLoginSource(com.shine.xumis.util.IpUtils.shortUserAgent(userAgent));
        String token = newToken();
        u.setVerifyToken(token);
        u.setVerifyTokenExpiry(LocalDateTime.now().plusHours(VERIFY_TOKEN_HOURS));
        UserAccount saved = userAccountRepository.save(u);
        mailService.sendVerification(saved.getEmail(), token);
        return Optional.of(saved);
    }

    /** 邮箱验证：令牌有效且未过期则置为已验证，并清除令牌。返回是否成功。 */
    @Transactional
    public boolean verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<UserAccount> opt = userAccountRepository.findByVerifyToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        if (u.getVerifyTokenExpiry() == null || u.getVerifyTokenExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }
        u.setEmailVerified(true);
        u.setVerifyToken(null);
        u.setVerifyTokenExpiry(null);
        userAccountRepository.save(u);
        return true;
    }

    /** 重新发送验证邮件（仅对未验证账号有效）。返回是否发送。 */
    @Transactional
    public boolean resendVerification(String email) {
        Optional<UserAccount> opt = findByEmail(email);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        if (u.isEmailVerified()) {
            return false;
        }
        String token = newToken();
        u.setVerifyToken(token);
        u.setVerifyTokenExpiry(LocalDateTime.now().plusHours(VERIFY_TOKEN_HOURS));
        userAccountRepository.save(u);
        mailService.sendVerification(u.getEmail(), token);
        return true;
    }

    /** 生成找回密码令牌并发送邮件。始终返回 true（不泄露账号是否存在）。 */
    @Transactional
    public boolean createPasswordResetToken(String email) {
        Optional<UserAccount> opt = findByEmail(email);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        String token = newToken();
        u.setResetToken(token);
        u.setResetTokenExpiry(LocalDateTime.now().plusHours(RESET_TOKEN_HOURS));
        userAccountRepository.save(u);
        mailService.sendPasswordReset(u.getEmail(), token);
        return true;
    }

    /** 用令牌重置密码。成功返回 true。 */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        Optional<UserAccount> opt = userAccountRepository.findByResetToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        if (u.getResetTokenExpiry() == null || u.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return false;
        }
        u.setPasswordHash(PasswordHasher.hash(newPassword));
        u.setResetToken(null);
        u.setResetTokenExpiry(null);
        u.setEmailVerified(true); // 能通过邮件重置即证明拥有该邮箱
        userAccountRepository.save(u);
        return true;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    /** 重置令牌是否有效（存在且未过期），用于展示重置表单前校验。 */
    public boolean isResetTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<UserAccount> opt = userAccountRepository.findByResetToken(token);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        return u.getResetTokenExpiry() != null && u.getResetTokenExpiry().isAfter(LocalDateTime.now());
    }

    /** 登录：成功时写入最后登录时间、IP 与来源（设备 · 浏览器） */
    @Transactional
    public Optional<UserAccount> login(String email, String rawPassword, String ip, String userAgent) {
        Optional<UserAccount> opt = findByEmail(email);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        UserAccount u = opt.get();
        if (!u.isEnabled() || rawPassword == null || !PasswordHasher.verify(rawPassword, u.getPasswordHash())) {
            return Optional.empty();
        }
        u.setLastLoginAt(LocalDateTime.now());
        u.setLastLoginIp(ip);
        u.setLastLoginSource(com.shine.xumis.util.IpUtils.shortUserAgent(userAgent));
        userAccountRepository.save(u);
        return Optional.of(u);
    }

    /** 更新收货资料（下单时会预填） */
    @Transactional
    public boolean updateProfile(Long id, String name, String phone, String country, String address) {
        Optional<UserAccount> opt = userAccountRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        u.setName(name);
        u.setPhone(phone);
        u.setCountry(country);
        u.setAddress(address);
        userAccountRepository.save(u);
        return true;
    }

    @Transactional
    public boolean changePassword(Long id, String oldPassword, String newPassword) {
        Optional<UserAccount> opt = userAccountRepository.findById(id);
        if (opt.isEmpty() || newPassword == null || newPassword.length() < 8) {
            return false;
        }
        UserAccount u = opt.get();
        if (oldPassword == null || !PasswordHasher.verify(oldPassword, u.getPasswordHash())) {
            return false;
        }
        u.setPasswordHash(PasswordHasher.hash(newPassword));
        userAccountRepository.save(u);
        return true;
    }

    /** 删除客户账号（仅后台站长可用）。订单按邮箱归属，不受账号删除影响。 */
    @Transactional
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        Optional<UserAccount> opt = userAccountRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        userAccountRepository.delete(opt.get());
        return true;
    }

    @Transactional
    public boolean setEnabled(Long id, boolean enabled) {
        Optional<UserAccount> opt = userAccountRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        UserAccount u = opt.get();
        u.setEnabled(enabled);
        userAccountRepository.save(u);
        return true;
    }

    public Optional<UserAccount> findById(Long id) {
        return (id == null) ? Optional.empty() : userAccountRepository.findById(id);
    }
}
