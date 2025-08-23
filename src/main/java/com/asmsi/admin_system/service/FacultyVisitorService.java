package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.FacultyLogRow;
import com.asmsi.admin_system.entity.FacultyVisitor;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.FacultyRepository;        // <-- your profile repo
import com.asmsi.admin_system.repository.FacultyVisitorRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class FacultyVisitorService {

  private final FacultyRepository facultyRepo;                  // expects findByEmployeeId(...)
  private final FacultyVisitorRepository visitRepo;

  private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

  public FacultyVisitorService(FacultyRepository facultyRepo,
                               FacultyVisitorRepository visitRepo) {
    this.facultyRepo = facultyRepo;
    this.visitRepo = visitRepo;
  }

  public FacultyVisitor logVisitSimple(String employeeId) {
    var f = facultyRepo.findByEmployeeId(employeeId)
        .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
    var v = new FacultyVisitor();
    v.setEmployeeId(f.getEmployeeId());
    v.setName(f.getName());
    v.setPosition(f.getPosition());
    v.setDepartment(f.getDepartment());
    v.setImageUrl(f.getImageUrl());
    v.setVisitedAt(Instant.now());
    return visitRepo.save(v);
  }

  public FacultyVisitor logVisit(String employeeId,
                                 Integer adult, Integer kid, Integer total,
                                 String contact, VehicleType vehicleType) {
    var f = facultyRepo.findByEmployeeId(employeeId)
        .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
    var v = new FacultyVisitor();
    v.setEmployeeId(f.getEmployeeId());
    v.setName(f.getName());
    v.setPosition(f.getPosition());
    v.setDepartment(f.getDepartment());
    v.setImageUrl(f.getImageUrl());
    v.setVisitedAt(Instant.now());
    v.setAdultVisitor(adult);
    v.setKidVisitor(kid);
    v.setTotalVisitors(total);
    v.setContactNumber(contact);
    v.setVehicleType(vehicleType);
    return visitRepo.save(v);
  }

  public List<FacultyLogRow> recentRows(int limit) {
    ZoneId zone = ZoneId.systemDefault();
    return visitRepo.findRecent(limit).stream().map(v -> {
      LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
      return new FacultyLogRow(
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
