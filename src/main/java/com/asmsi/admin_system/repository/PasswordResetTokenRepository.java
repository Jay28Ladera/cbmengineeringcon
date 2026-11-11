package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    /** Each user can hold one reset token, so clear the old one before issuing a new one. */
    @Modifying
    @Transactional
    @Query("delete from PasswordResetToken t where t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
