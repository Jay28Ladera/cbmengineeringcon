package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.StaffActivation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface StaffActivationRepository extends JpaRepository<StaffActivation, Long> {
    Optional<StaffActivation> findByToken(String token);
    List<StaffActivation> findByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
