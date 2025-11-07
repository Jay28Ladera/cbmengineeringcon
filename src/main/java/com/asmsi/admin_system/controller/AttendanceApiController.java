// src/main/java/com/asmsi/admin_system/controller/AttendanceApiController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.AttendanceLog;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.AttendanceLogRepository;
import com.asmsi.admin_system.repository.StudentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/api/attendance")
public class AttendanceApiController {

    private final AttendanceLogRepository attendanceLogRepository;
    private final StudentRepository studentRepository;

    public AttendanceApiController(AttendanceLogRepository attendanceLogRepository,
                                   StudentRepository studentRepository) {
        this.attendanceLogRepository = attendanceLogRepository;
        this.studentRepository = studentRepository;
    }

    // --- NEW: dropdown data ---
    @GetMapping("/accounts")
    @ResponseBody
    public List<String> accounts() {
        return attendanceLogRepository.findDistinctAccounts();
    }

    // ---------- existing endpoints (unchanged) ----------
    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<String> submitAttendance(@RequestBody AttendanceLog log) {
        String idNumber = log.getIdNumber();
        LocalDate today = LocalDate.now();

        Optional<AttendanceLog> latestLogOpt =
                attendanceLogRepository.findTopByIdNumberAndDateOrderByTimeInDesc(idNumber, today);

        if (latestLogOpt.isPresent() && latestLogOpt.get().getTimeOut() == null) {
            AttendanceLog latestLog = latestLogOpt.get();
            latestLog.setTimeOut(LocalTime.now());
            attendanceLogRepository.save(latestLog);
            return ResponseEntity.ok("Time Out recorded.");
        }

        if (log.getDate() == null) log.setDate(LocalDate.now());
        if (log.getTimeIn() == null) log.setTimeIn(LocalTime.now());

        attendanceLogRepository.save(log);
        return ResponseEntity.ok("Attendance recorded.");
    }

    @GetMapping("/logs/{idNumber}")
    @ResponseBody
    public List<AttendanceLog> getLogsByIdNumber(@PathVariable String idNumber) {
        return attendanceLogRepository.findByIdNumber(idNumber);
    }

    @GetMapping("/student/{idNumber}")
    @ResponseBody
    public ResponseEntity<Student> fetchStudentData(@PathVariable String idNumber) {
        return studentRepository.findByIdNumber(idNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/logs")
    @ResponseBody
    public ResponseEntity<List<AttendanceLog>> getAllLogs() {
        List<AttendanceLog> logs = attendanceLogRepository.findAll(
                Sort.by(Sort.Direction.DESC, "date", "timeIn")
        );
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/page")
    public String attendancePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            String role = auth.getAuthorities().stream().findFirst().map(Object::toString).orElse("ROLE_USER");
            model.addAttribute("username", username);
            model.addAttribute("role", role);
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }
        return "attendance";
    }
}
