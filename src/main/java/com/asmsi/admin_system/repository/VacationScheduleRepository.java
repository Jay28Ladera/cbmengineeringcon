// src/main/java/com/asmsi/admin_system/repository/VacationScheduleRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.VacationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VacationScheduleRepository extends JpaRepository<VacationSchedule, Long> {
    Optional<VacationSchedule> findByVia(String via);
}
