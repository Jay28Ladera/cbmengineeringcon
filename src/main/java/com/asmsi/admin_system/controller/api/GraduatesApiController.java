package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.GraduateVisitRow;
import com.asmsi.admin_system.entity.AsmsiAlumni;
import com.asmsi.admin_system.entity.AsmsiVisitor;
import com.asmsi.admin_system.repository.AsmsiAlumniRepository;
import com.asmsi.admin_system.repository.AsmsiVisitorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/graduates")
public class GraduatesApiController {

    private final AsmsiAlumniRepository alumniRepo;
    private final AsmsiVisitorRepository visitRepo;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm:ss");

    public GraduatesApiController(AsmsiAlumniRepository alumniRepo,
                                  AsmsiVisitorRepository visitRepo) {
        this.alumniRepo = alumniRepo;
        this.visitRepo = visitRepo;
    }

    // Card lookup: GET /api/graduates/{idNumber}
    @GetMapping("/{idNumber}")
    public ResponseEntity<Map<String, Object>> getGraduate(@PathVariable String idNumber) {
        AsmsiAlumni a = alumniRepo.findByIdNumber(idNumber)
                .orElseThrow(() -> new NoSuchElementException("Graduate not found"));

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("idNumber",    a.getIdNumber());
        dto.put("batchNumber", a.getBatchNumber());
        dto.put("address",     a.getAddress());
        dto.put("imageUrl",    a.getImageUrl());
        return ResponseEntity.ok(dto);
    }

    // Save a visit row: POST /api/graduates/visit   body: { "idNumber": "TAL2017-045" }
    @PostMapping("/visit")
    public ResponseEntity<?> visit(@RequestBody Map<String, String> body, Principal principal) {
        String idNumber = body.getOrDefault("idNumber", "").trim();
        if (idNumber.isEmpty()) return ResponseEntity.badRequest().body("idNumber required");

        AsmsiAlumni a = alumniRepo.findByIdNumber(idNumber)
                .orElseThrow(() -> new NoSuchElementException("Graduate not found"));

        AsmsiVisitor v = new AsmsiVisitor();
        v.setAlumni(a);
        v.setVisitedAt(Instant.now());
        if (principal != null) v.setRecordedBy(principal.getName());
        visitRepo.save(v);

        return ResponseEntity.ok().build();
    }

    // Right-table logs: latest 8 visits
    @GetMapping("/logs")
    public List<GraduateVisitRow> latestLogs() {
        ZoneId zone = ZoneId.systemDefault();
        return visitRepo.findRecent(PageRequest.of(0, 8)).stream()
                .map(v -> {
                    AsmsiAlumni a = v.getAlumni();
                    LocalDateTime dt = LocalDateTime.ofInstant(v.getVisitedAt(), zone);
                    return new GraduateVisitRow(
                            a.getImageUrl(),
                            a.getIdNumber(),
                            DF.format(dt),
                            TF.format(dt),
                            a.getAddress(),
                            a.getBatchNumber()
                    );
                })
                .toList();
    }

    // Make 404s clean
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    
}
