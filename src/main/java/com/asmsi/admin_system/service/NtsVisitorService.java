package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.NtsLogRow;
import com.asmsi.admin_system.entity.NtsVisitor;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.NonTeachingStaffRepository;
import com.asmsi.admin_system.repository.NtsVisitorRepository;
import jakarta.transaction.Transactional;
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

  /** Simple visit (no extra fields). */
  public NtsVisitor logVisit(String employeeId) {
    var nts = ntsRepo.findByEmployeeId(employeeId)
        .orElseThrow(() -> new IllegalArgumentException("NTS not found"));

    var v = new NtsVisitor();
    v.setEmployeeId(nts.getEmployeeId());
    v.setName(nts.getName());
    v.setPosition(nts.getPosition());
    v.setDepartment(nts.getDepartment());
    v.setImageUrl(nts.getImageUrl());
    v.setVisitedAt(Instant.now());
    return visitRepo.save(v);
  }

  /** Visit with modal extras. */
  public NtsVisitor logVisit(String employeeId,
                             Integer adult, Integer kid, Integer total,
                             String contact, VehicleType vehicleType) {
    var nts = ntsRepo.findByEmployeeId(employeeId)
        .orElseThrow(() -> new IllegalArgumentException("NTS not found"));

    var v = new NtsVisitor();
    v.setEmployeeId(nts.getEmployeeId());
    v.setName(nts.getName());
    v.setPosition(nts.getPosition());
    v.setDepartment(nts.getDepartment());
    v.setImageUrl(nts.getImageUrl());
    v.setVisitedAt(Instant.now());
    v.setAdultVisitor(adult);
    v.setKidVisitor(kid);
    v.setTotalVisitors(total);
    v.setContactNumber(contact);
    v.setVehicleType(vehicleType);
    return visitRepo.save(v);
  }

  /** Last N rows for the UI table. */
  public List<NtsLogRow> recentRows(int limit) {
    ZoneId zone = ZoneId.systemDefault();
    return visitRepo.findRecent(limit).stream().map(v -> {
      LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
      return new NtsLogRow(
          v.getImageUrl(),
          v.getName(),
          v.getEmployeeId(),
          DF.format(dt),
          TF.format(dt),
          v.getPosition(),
          v.getDepartment(),
          v.getAdultVisitor(),
          v.getKidVisitor(),
          v.getTotalVisitors(),
          v.getContactNumber(),
          v.getVehicleType() == null ? null : v.getVehicleType().name()
      );
    }).toList();
  }
}
