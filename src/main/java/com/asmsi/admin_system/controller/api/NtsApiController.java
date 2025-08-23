// src/main/java/com/asmsi/admin_system/controller/api/NtsApiController.java
package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.NtsLogRow;
import com.asmsi.admin_system.dto.NtsVisitRequest;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.NonTeachingStaffRepository;
import com.asmsi.admin_system.service.NtsVisitorService;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nts")
public class NtsApiController {

    private final NonTeachingStaffRepository ntsRepo;
    private final NtsVisitorService visitService;

    public NtsApiController(NonTeachingStaffRepository ntsRepo,
                            NtsVisitorService visitService) {
        this.ntsRepo = ntsRepo;
        this.visitService = visitService;
    }

    /** Used by the search box in the NTS tab to show the profile card. */
    @GetMapping("/{employeeId}")
    public ResponseEntity<?> getByEmployeeId(@PathVariable String employeeId) {
        return ntsRepo.findByEmployeeId(employeeId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "NTS not found")));
    }

    /** Create a visit row with adult/kid/total/contact/vehicle. */
    @PostMapping("/visit")
    public ResponseEntity<?> logVisit(@RequestBody NtsVisitRequest req) {
        if (req == null || req.employeeId() == null || req.employeeId().isBlank()) {
            return ResponseEntity.badRequest().body("employeeId required");
        }

        VehicleType vt = null;
        if (req.vehicleType() != null && !req.vehicleType().isBlank()) {
            try {
                vt = VehicleType.valueOf(req.vehicleType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("invalid vehicleType");
            }
        }

        Integer total = (req.totalVisitors() != null)
                ? req.totalVisitors()
                : ((req.adultVisitor() == null ? 0 : req.adultVisitor())
                   + (req.kidVisitor()   == null ? 0 : req.kidVisitor()));

        visitService.logVisit(
                req.employeeId(),
                req.adultVisitor(), req.kidVisitor(), total,
                req.contactNumber(), vt
        );

        return ResponseEntity.ok().build();
    }

    /** Latest rows for the NTS table. */
    @GetMapping("/logs")
    public List<NtsLogRow> logs() {
        return visitService.recentRows(8);
    }

    /** Masterlist (name/ID) for the right-side table. */
    @GetMapping("/masterlist")
    public List<Map<String, String>> masterlist(
            @RequestParam(name = "query", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        int lim = Math.max(1, Math.min(limit, 500));
        return ntsRepo.searchTop(q, PageRequest.of(0, lim)).stream()
                .map(n -> Map.of(
                        "name",       n.getName() == null ? "" : n.getName(),
                        "employeeId", n.getEmployeeId() == null ? "" : n.getEmployeeId()
                ))
                .toList();
    }
}
