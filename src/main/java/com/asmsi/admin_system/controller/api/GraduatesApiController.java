// src/main/java/com/asmsi/admin_system/controller/api/GraduatesApiController.java
package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.GraduateLogRow;
import com.asmsi.admin_system.dto.GraduateVisitRequest;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.AsmsiAlumniRepository;
import com.asmsi.admin_system.service.GraduateVisitorService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graduates")
public class GraduatesApiController {

    private final AsmsiAlumniRepository alumniRepo;
    private final GraduateVisitorService visitService;

    public GraduatesApiController(AsmsiAlumniRepository alumniRepo,
                                  GraduateVisitorService visitService) {
        this.alumniRepo = alumniRepo;
        this.visitService = visitService;
    }

    @GetMapping("/{idNumber}")
    public ResponseEntity<?> getGraduate(@PathVariable String idNumber) {
        return alumniRepo.findByIdNumber(idNumber)
            .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of(
                    "idNumber", a.getIdNumber(),
                    "batchNumber", a.getBatchNumber(),
                    "address", a.getAddress(),
                    "imageUrl", a.getImageUrl()
            )))
            .orElseGet(() -> ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Graduate not found")));
    }

    @PostMapping("/visit")
    public ResponseEntity<?> visit(@RequestBody GraduateVisitRequest req, Principal principal) {
        if (req == null || req.idNumber() == null || req.idNumber().isBlank()) {
            return ResponseEntity.badRequest().body("idNumber required");
        }

        VehicleType vt = null;
        if (req.vehicleType() != null && !req.vehicleType().isBlank()) {
            try { vt = VehicleType.valueOf(req.vehicleType().toUpperCase()); }
            catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body("invalid vehicleType"); }
        }

        int a = req.adultVisitor()==null ? 0 : req.adultVisitor();
        int k = req.kidVisitor()==null ? 0 : req.kidVisitor();
        int t = req.totalVisitors()==null ? (a+k) : req.totalVisitors();

        String recordedBy = principal != null ? principal.getName() : null;
        visitService.logVisit(req.idNumber(), a, k, t, req.contactNumber(), vt, recordedBy);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/logs")
    public List<GraduateLogRow> latestLogs() {
        return visitService.recentRows(8);
    }

    /* -------- Masterlist: returns [{name, idNumber}, ...] -------- */
    @GetMapping("/masterlist")
    public List<Map<String, String>> masterlist(
            @RequestParam(name = "query", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        int lim = Math.max(1, Math.min(limit, 500));
        return alumniRepo.searchTop(q, PageRequest.of(0, lim)).stream()
                .map(a -> Map.of(
                        "name", formatName(a.getLastName(), a.getFirstName(), a.getMiddleName()),
                        "idNumber", a.getIdNumber() == null ? "" : a.getIdNumber()
                ))
                .toList();
    }

    private static String formatName(String last, String first, String middle) {
        String l = safe(last), f = safe(first), m = safe(middle);
        StringBuilder sb = new StringBuilder();
        if (!l.isEmpty()) sb.append(l);
        if (!f.isEmpty() || !m.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            if (!f.isEmpty()) sb.append(f);
            if (!m.isEmpty()) sb.append(' ').append(m);
        }
        return sb.toString();
    }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
