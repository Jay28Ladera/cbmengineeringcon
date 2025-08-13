package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.VisitingDayRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VisitingDayRepository extends JpaRepository<VisitingDayRecord, UUID> {
    List<VisitingDayRecord> findTop50ByOrderByCreatedAtDesc();

    List<VisitingDayRecord> findByDate(LocalDate date);

    List<VisitingDayRecord> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);
}
