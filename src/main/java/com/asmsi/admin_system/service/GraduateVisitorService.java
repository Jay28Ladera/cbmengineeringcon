package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.GraduateLogRow;
import com.asmsi.admin_system.entity.GraduateVisitor;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.AsmsiAlumniRepository;
import com.asmsi.admin_system.repository.GraduateVisitorRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional
public class GraduateVisitorService {

  private final AsmsiAlumniRepository alumniRepo;      // expects findByIdNumber(...)
  private final GraduateVisitorRepository visitRepo;

  private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

  public GraduateVisitorService(AsmsiAlumniRepository alumniRepo,
                                GraduateVisitorRepository visitRepo) {
    this.alumniRepo = alumniRepo;
    this.visitRepo = visitRepo;
  }

  public GraduateVisitor logVisitSimple(String idNumber) {
    var a = alumniRepo.findByIdNumber(idNumber)
        .orElseThrow(() -> new IllegalArgumentException("Graduate not found"));
    var v = new GraduateVisitor();
    v.setIdNumber(a.getIdNumber());
    v.setBatchNumber(a.getBatchNumber());
    v.setAddress(a.getAddress());
    v.setImageUrl(a.getImageUrl());
    v.setVisitedAt(Instant.now());
    return visitRepo.save(v);
  }

  public GraduateVisitor logVisit(String idNumber,
                                  Integer adult, Integer kid, Integer total,
                                  String contact, VehicleType vehicleType,
                                  String recordedBy) {
    var a = alumniRepo.findByIdNumber(idNumber)
        .orElseThrow(() -> new IllegalArgumentException("Graduate not found"));
    var v = new GraduateVisitor();
    v.setIdNumber(a.getIdNumber());
    v.setBatchNumber(a.getBatchNumber());
    v.setAddress(a.getAddress());
    v.setImageUrl(a.getImageUrl());
    v.setVisitedAt(Instant.now());
    v.setAdultVisitor(adult);
    v.setKidVisitor(kid);
    v.setTotalVisitors(total);
    v.setContactNumber(contact);
    v.setVehicleType(vehicleType);
    v.setRecordedBy(recordedBy);
    return visitRepo.save(v);
  }

  public List<GraduateLogRow> recentRows(int limit) {
    ZoneId zone = ZoneId.systemDefault();
    return visitRepo.findRecent(limit).stream().map(v -> {
      LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
      return new GraduateLogRow(
          v.getImageUrl(),
          v.getIdNumber(),
          DF.format(dt),
          TF.format(dt),
          v.getAddress(),
          v.getBatchNumber(),
          v.getAdultVisitor(),
          v.getKidVisitor(),
          v.getTotalVisitors(),
          v.getContactNumber(),
          v.getVehicleType() == null ? null : v.getVehicleType().name()
      );
    }).toList();
  }
}
