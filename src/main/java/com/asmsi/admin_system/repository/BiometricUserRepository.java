package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.BiometricUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BiometricUserRepository extends JpaRepository<BiometricUser, Long> {
    Optional<BiometricUser> findByPin(String pin);
}
