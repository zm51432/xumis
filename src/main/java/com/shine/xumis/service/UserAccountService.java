package com.shine.xumis.service;

import com.shine.xumis.model.UserAccount;
import com.shine.xumis.repository.UserAccountRepository;
import com.shine.xumis.util.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * C 端账号服务：注册、登录、资料维护。
 */
@Service
public class UserAccountService {

    public static final String SESSION_KEY = "USER";

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
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

    /** 注册：邮箱重复或口令过短返回空 */
    @Transactional
    public Optional<UserAccount> register(String email, String rawPassword, String name) {
        String mail = (email == null) ? "" : email.trim().toLowerCase();
        if (!mail.contains("@") || rawPassword == null || rawPassword.length() < 8) {
            return Optional.empty();
        }
        if (userAccountRepository.findByEmail(mail).isPresent()) {
            return Optional.empty();
        }
        UserAccount u = new UserAccount(mail, PasswordHasher.hash(rawPassword),
                (name == null || name.isBlank()) ? mail.substring(0, mail.indexOf('@')) : name.trim());
        return Optional.of(userAccountRepository.save(u));
    }

    @Transactional
    public Optional<UserAccount> login(String email, String rawPassword) {
        Optional<UserAccount> opt = findByEmail(email);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        UserAccount u = opt.get();
        if (!u.isEnabled() || rawPassword == null || !PasswordHasher.verify(rawPassword, u.getPasswordHash())) {
            return Optional.empty();
        }
        u.setLastLoginAt(LocalDateTime.now());
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
}
