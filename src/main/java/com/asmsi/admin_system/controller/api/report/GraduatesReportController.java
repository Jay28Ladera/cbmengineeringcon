package com.asmsi.admin_system.controller.api.report;

import com.asmsi.admin_system.dto.report.GradVisitRow;
import com.asmsi.admin_system.dto.report.SimpleListSummary;
import com.asmsi.admin_system.repository.GraduateVisitorRepository;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/graduates/report")
public class GraduatesReportController {
  private final GraduateVisitorRepository repo;
  private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

  public GraduatesReportController(GraduateVisitorRepository repo) { this.repo = repo; }

  @GetMapping("/summary")
  public SimpleListSummary<GradVisitRow> summary(@RequestParam String start, @RequestParam String end) {
    ZoneId tz = ZoneId.systemDefault();
    Instant from = LocalDate.parse(start).atStartOfDay(tz).toInstant();
    Instant to   = LocalDate.parse(end).plusDays(1).atStartOfDay(tz).toInstant();

    var rows = repo.findInRange(from, to).stream().map(v -> {
      var dt = LocalDateTime.ofInstant(v.getVisitedAt(), tz);
      return new GradVisitRow(
          v.getImageUrl(),
          v.getIdNumber(),
          v.getBatchNumber(),
          v.getAddress(),
          DF.format(dt),
          TF.format(dt),
          // NEW: pass-through the counts and vehicle
          v.getAdultVisitor(),
          v.getKidVisitor(),
          v.getTotalVisitors(),
          v.getVehicleType() == null ? null : v.getVehicleType().toString()
      );
    }).toList();

    return new SimpleListSummary<>(rows.size(), rows);
  }
}
