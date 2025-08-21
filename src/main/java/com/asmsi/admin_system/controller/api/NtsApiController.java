// src/main/java/com/asmsi/admin_system/controller/api/NtsApiController.java
package com.asmsi.admin_system.controller.api;

import com.asmsi.admin_system.dto.NtsLogRow;
import com.asmsi.admin_system.repository.NonTeachingStaffRepository;
import com.asmsi.admin_system.service.NtsVisitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * NTS API:
 *  - GET  /api/nts/{employeeId}  -> return NTS profile for the search card
 *  - POST /api/nts/visit         -> append a row to ntsvisitor (called after a successful search)
 *  - GET  /api/nts/logs          -> latest visit rows for the table (reads from ntsvisitor)
 */
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
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Called after a successful search to create a visit row in ntsvisitor. */
    @PostMapping("/visit")
    public ResponseEntity<NtsLogRow> logVisit(@RequestBody Map<String, String> body) {
        String employeeId = Optional.ofNullable(body.get("employeeId"))
                .map(String::trim).orElse("");
        if (employeeId.isEmpty()) return ResponseEntity.badRequest().build();

        visitService.logVisit(employeeId);

        // return the most recent row (optional, but handy for debugging/UI)
        List<NtsLogRow> latest = visitService.recentRows(1);
        return latest.isEmpty() ? ResponseEntity.ok().build() : ResponseEntity.ok(latest.get(0));
    }

    /** Populates the "Latest records" table in the NTS tab (reads from ntsvisitor). */
    @GetMapping("/logs")
    public List<NtsLogRow> logs() {
        return visitService.recentRows(8); // show top 8
    }
}
