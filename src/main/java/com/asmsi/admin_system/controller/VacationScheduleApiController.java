// src/main/java/com/asmsi/admin_system/controller/VacationScheduleApiController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.VacationSchedule;
import com.asmsi.admin_system.repository.VacationScheduleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/vacation-schedule")
public class VacationScheduleApiController {

    private final VacationScheduleRepository scheduleRepository;

    public VacationScheduleApiController(VacationScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // GET all schedules (for the top table)
    @GetMapping
    public List<VacationSchedule> listAll() {
        return scheduleRepository.findAll(Sort.by("via").ascending());
    }

    // UPSERT by VIA (called when you click "Set" or edit)
    @PostMapping
    public VacationSchedule save(@RequestBody VacationSchedule payload) {
        String via = payload.getVia();
        if (via == null || via.isBlank()) {
            throw new IllegalArgumentException("VIA is required");
        }

        VacationSchedule entity = scheduleRepository
                .findByVia(via)
                .orElse(new VacationSchedule());

        entity.setVia(via);
        entity.setTotalStudents(payload.getTotalStudents());
        entity.setLoggedIn(payload.getLoggedIn());
        entity.setEstDateTime(payload.getEstDateTime());
        entity.setVenue(payload.getVenue());
        entity.setVehicle(payload.getVehicle());
        entity.setStatus(payload.getStatus());
        entity.setMessage(payload.getMessage());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            entity.setUpdatedBy(auth.getName());
        } else {
            entity.setUpdatedBy("system");
        }
        entity.setUpdatedAt(LocalDateTime.now());

        return scheduleRepository.save(entity);
    }

    // DELETE by VIA
    @DeleteMapping("/{via}")
    public ResponseEntity<Void> deleteByVia(@PathVariable("via") String via) {
        if (via == null || via.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String normalizedVia = via.trim();

        Optional<VacationSchedule> existingOpt =
                scheduleRepository.findByVia(normalizedVia);

        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        scheduleRepository.delete(existingOpt.get());
        return ResponseEntity.noContent().build();
    }
}
