package com.shine.xumis.repository;

import com.shine.xumis.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** 某账号的全部令牌（重置成功后统一作废） */
    void deleteByUserId(Long userId);

    /** 清理已过期的令牌（每次发新链接时顺手做） */
    void deleteByExpiresAtBefore(LocalDateTime time);
}
