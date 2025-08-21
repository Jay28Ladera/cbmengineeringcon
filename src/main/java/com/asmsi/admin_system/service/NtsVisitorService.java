// src/main/java/com/asmsi/admin_system/service/NtsVisitorService.java
package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.NtsLogRow;
import com.asmsi.admin_system.entity.NonTeachingStaff;
import com.asmsi.admin_system.entity.NtsVisitor;
import com.asmsi.admin_system.repository.NonTeachingStaffRepository;
import com.asmsi.admin_system.repository.NtsVisitorRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class NtsVisitorService {

    private final NonTeachingStaffRepository ntsRepo;
    private final NtsVisitorRepository visitRepo;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public NtsVisitorService(NonTeachingStaffRepository ntsRepo, NtsVisitorRepository visitRepo) {
        this.ntsRepo = ntsRepo;
        this.visitRepo = visitRepo;
    }

    /** Create a visit record from an employeeId (copies current profile fields). */
    public NtsVisitor logVisit(String employeeId) {
        NonTeachingStaff nts = ntsRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("NTS not found"));
        NtsVisitor v = new NtsVisitor();
        v.setEmployeeId(nts.getEmployeeId());
        v.setName(nts.getName());
        v.setPosition(nts.getPosition());
        v.setDepartment(nts.getDepartment());
        v.setImageUrl(nts.getImageUrl());
        v.setVisitedAt(Instant.now());
        return visitRepo.save(v);
    }

    /** Last N rows formatted for the UI table. */
    public List<NtsLogRow> recentRows(int limit) {
        ZoneId zone = ZoneId.systemDefault();
        return visitRepo.findRecent(PageRequest.of(0, limit)).stream().map(v -> {
            LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
            return new NtsLogRow(
                    v.getImageUrl(),
                    v.getName(),
                    v.getEmployeeId(),
                    DF.format(dt),
                    TF.format(dt),
                    v.getPosition(),
                    v.getDepartment()
            );
        }).toList();
    }
}
