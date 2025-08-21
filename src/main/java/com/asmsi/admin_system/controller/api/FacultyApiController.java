package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.FacultyVisitRow;
import com.asmsi.admin_system.entity.Faculty;
import com.asmsi.admin_system.entity.FacultyVisitor;
import com.asmsi.admin_system.repository.FacultyRepository;
import com.asmsi.admin_system.repository.FacultyVisitorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/faculty")
public class FacultyApiController {

    private final FacultyRepository facultyRepo;
    private final FacultyVisitorRepository visitorRepo;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public FacultyApiController(FacultyRepository facultyRepo,
                                FacultyVisitorRepository visitorRepo) {
        this.facultyRepo = facultyRepo;
        this.visitorRepo = visitorRepo;
    }

    // Used by the search box: /api/faculty/{employeeId}
    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getFaculty(@PathVariable String employeeId) {
        Faculty f = facultyRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Faculty not found"));

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("employeeId", f.getEmployeeId());
        dto.put("name",       f.getName());
        dto.put("position",   f.getPosition());
        dto.put("department", f.getDepartment());
        dto.put("imageUrl",   f.getImageUrl());
        return ResponseEntity.ok(dto);
    }

    // NEW: write a visit row (your UI calls this after a successful lookup)
    @PostMapping("/visit")
    public ResponseEntity<?> visit(@RequestBody Map<String, String> body, Principal principal) {
        String employeeId = body.getOrDefault("employeeId", "").trim();
        if (employeeId.isEmpty()) return ResponseEntity.badRequest().body("employeeId required");

        Faculty f = facultyRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Faculty not found"));

        FacultyVisitor v = new FacultyVisitor();
        v.setFaculty(f);
        v.setVisitedAt(Instant.now());
        if (principal != null) v.setRecordedBy(principal.getName());
        visitorRepo.save(v);

        return ResponseEntity.ok().build();
    }

    // Fills the “Latest records” table (last 8 visits)
    @GetMapping("/logs")
    public List<FacultyVisitRow> latestLogs() {
        ZoneId zone = ZoneId.systemDefault();
        return visitorRepo.findRecent(PageRequest.of(0, 8)).stream().map(v -> {
            Faculty f = v.getFaculty();
            LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
            return new FacultyVisitRow(
                    f.getImageUrl(),
                    f.getName(),
                    f.getEmployeeId(),
                    DF.format(dt),
                    TF.format(dt),
                    f.getPosition(),
                    f.getDepartment()
            );
        }).toList();
    }
}
