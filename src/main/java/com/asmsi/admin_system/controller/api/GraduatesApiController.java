package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.GraduateLogRow;
import com.asmsi.admin_system.dto.GraduateVisitRequest;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.AsmsiAlumniRepository;
import com.asmsi.admin_system.service.GraduateVisitorService;
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

    /** Card lookup for search box. */
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

    /** Save a visit row including Adult/Kid/Total/Contact/Vehicle. */
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

        var recordedBy = (principal != null ? principal.getName() : null);

        visitService.logVisit(req.idNumber(), a, k, t, req.contactNumber(), vt, recordedBy);
        return ResponseEntity.ok().build();
    }

    /** Latest rows for the right table. */
    @GetMapping("/logs")
    public List<GraduateLogRow> latestLogs() {
        return visitService.recentRows(8);
    }
}
