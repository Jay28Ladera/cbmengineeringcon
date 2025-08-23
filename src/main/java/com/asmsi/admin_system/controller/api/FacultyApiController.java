package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.FacultyLogRow;
import com.asmsi.admin_system.dto.FacultyVisitRequest;
import com.asmsi.admin_system.entity.VehicleType;
import com.asmsi.admin_system.repository.FacultyRepository;
import com.asmsi.admin_system.service.FacultyVisitorService;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/faculty")
public class FacultyApiController {

    private final FacultyRepository facultyRepo;
    private final FacultyVisitorService visitService;

    public FacultyApiController(FacultyRepository facultyRepo,
                                FacultyVisitorService visitService) {
        this.facultyRepo = facultyRepo;
        this.visitService = visitService;
    }

    /** Card lookup for the search box. */
    @GetMapping("/{employeeId}")
    public ResponseEntity<?> getByEmployeeId(@PathVariable String employeeId) {
        return facultyRepo.findByEmployeeId(employeeId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Faculty not found")));
    }

    /** Save a visit with Adult/Kid/Total/Contact/Vehicle. */
    @PostMapping("/visit")
    public ResponseEntity<?> logVisit(@RequestBody FacultyVisitRequest req) {
        if (req == null || req.employeeId() == null || req.employeeId().isBlank()) {
            return ResponseEntity.badRequest().body("employeeId required");
        }

        VehicleType vt = null;
        if (req.vehicleType() != null && !req.vehicleType().isBlank()) {
            try { vt = VehicleType.valueOf(req.vehicleType().toUpperCase()); }
            catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body("invalid vehicleType"); }
        }

        Integer total = (req.totalVisitors() != null)
                ? req.totalVisitors()
                : ((req.adultVisitor()==null?0:req.adultVisitor()) + (req.kidVisitor()==null?0:req.kidVisitor()));

        visitService.logVisit(
            req.employeeId(),
            req.adultVisitor(), req.kidVisitor(), total,
            req.contactNumber(), vt
        );

        return ResponseEntity.ok().build();
    }

    /** Latest log rows for the table. */
    @GetMapping("/logs")
    public List<FacultyLogRow> logs() {
        return visitService.recentRows(8);
    }



    // ... your existing endpoints: GET /{employeeId}, POST /visit, GET /logs

    /** Masterlist (name/ID) for the right-side table on Faculty tab. */
    @GetMapping("/masterlist")
    public List<Map<String,String>> masterlist(
            @RequestParam(name = "query", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        int lim = Math.max(1, Math.min(limit, 500));
        return facultyRepo.searchTop(q, PageRequest.of(0, lim)).stream()
                .map(f -> Map.of(
                        "name",       f.getName() == null ? "" : f.getName(),
                        "employeeId", f.getEmployeeId() == null ? "" : f.getEmployeeId()
                ))
                .toList();
    }

}
