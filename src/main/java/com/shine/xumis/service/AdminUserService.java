package com.shine.xumis.service;

import com.shine.xumis.model.AdminRole;
import com.shine.xumis.model.AdminUser;
import com.shine.xumis.repository.AdminUserRepository;
import com.shine.xumis.util.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 管理员账号服务：密码 BCrypt 加密、登录校验、账号增删与角色管理。
 */
@Service
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    public long count() {
        return adminUserRepository.count();
    }

    public List<AdminUser> findAll() {
        return adminUserRepository.findAll();
    }

    public Optional<AdminUser> findByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    /**
     * 登录校验：用户名存在、账号启用、密码匹配才通过；成功时更新最后登录时间。
     */
    @Transactional
    public Optional<AdminUser> login(String username, String rawPassword) {
        Optional<AdminUser> opt = adminUserRepository.findByUsername(username == null ? "" : username.trim());
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        AdminUser u = opt.get();
        if (!u.isEnabled()) {
            return Optional.empty();
        }
        if (rawPassword == null || !PasswordHasher.verify(rawPassword, u.getPasswordHash())) {
            return Optional.empty();
        }
        u.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(u);
        return Optional.of(u);
    }

    /** 创建管理员（用户名重复时返回空） */
    @Transactional
    public Optional<AdminUser> create(String username, String rawPassword, String displayName, AdminRole role) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || adminUserRepository.findByUsername(name).isPresent()) {
            return Optional.empty();
        }
        AdminUser u = new AdminUser(name, PasswordHasher.hash(rawPassword),
                (displayName == null || displayName.isBlank()) ? name : displayName.trim(),
                role == null ? AdminRole.OPERATOR : role);
        return Optional.of(adminUserRepository.save(u));
    }

    /** 修改密码 */
    @Transactional
    public boolean changePassword(Long id, String rawPassword) {
        Optional<AdminUser> opt = adminUserRepository.findById(id);
        if (opt.isEmpty() || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        AdminUser u = opt.get();
        u.setPasswordHash(PasswordHasher.hash(rawPassword));
        adminUserRepository.save(u);
        return true;
    }

    @Transactional
    public boolean updateRole(Long id, AdminRole role) {
        Optional<AdminUser> opt = adminUserRepository.findById(id);
        if (opt.isEmpty() || role == null) {
            return false;
        }
        AdminUser u = opt.get();
        u.setRole(role);
        adminUserRepository.save(u);
        return true;
    }

    @Transactional
    public boolean setEnabled(Long id, boolean enabled) {
        Optional<AdminUser> opt = adminUserRepository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        AdminUser u = opt.get();
        u.setEnabled(enabled);
        adminUserRepository.save(u);
        return true;
    }

    /**
     * 首次启动：没有任何管理员时创建默认站长账号（口令来自配置，默认 Admin@12345）。
     */
    @Transactional
    public void ensureDefaultAdmin(String username, String rawPassword, String displayName) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        create(username, rawPassword, displayName, AdminRole.OWNER);
    }
}
