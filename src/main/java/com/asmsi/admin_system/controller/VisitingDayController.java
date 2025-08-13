package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.VisitingDayRequest;
import com.asmsi.admin_system.entity.VisitingDayRecord;
import com.asmsi.admin_system.service.VisitingDayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/visitingday")
public class VisitingDayController {

    private final VisitingDayService svc;

    public VisitingDayController(VisitingDayService svc) {
        this.svc = svc;
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submit(@RequestBody VisitingDayRequest req) {
        svc.save(req);
        return ResponseEntity.ok("Visiting Day record saved.");
    }

    @GetMapping("/logs")
    public List<VisitingDayRecord> logs() {
        return svc.latest(50);
    }
}
