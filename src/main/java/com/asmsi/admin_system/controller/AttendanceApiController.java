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

    // ---------- APIs (JSON) ----------

    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<String> submitAttendance(@RequestBody AttendanceLog log) {
        String idNumber = log.getIdNumber();
        LocalDate today = LocalDate.now();

        Optional<AttendanceLog> latestLogOpt =
                attendanceLogRepository.findTopByIdNumberAndDateOrderByTimeInDesc(idNumber, today);

        // If there is an existing log today with no timeout -> record timeout
        if (latestLogOpt.isPresent() && latestLogOpt.get().getTimeOut() == null) {
            AttendanceLog latestLog = latestLogOpt.get();
            latestLog.setTimeOut(LocalTime.now());
            attendanceLogRepository.save(latestLog);
            return ResponseEntity.ok("Time Out recorded.");
        }

        // Else: time-in
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

    // Masterlist for the right-side table (name + idNumber)
    // Final path: /api/attendance/students
    @GetMapping("/students")
    @ResponseBody
    public List<Map<String, String>> studentsMasterlist(
            @RequestParam(name = "query", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        int lim = Math.max(1, Math.min(limit, 200)); // clamp 1..200

        return studentRepository.searchTop(q, PageRequest.of(0, lim))
                .stream()
                .map(s -> Map.of(
                        "name",     s.getName() == null ? "" : s.getName(),
                        "idNumber", s.getIdNumber() == null ? "" : s.getIdNumber()
                ))
                .toList();
    }

    // ---------- (Optional) view endpoint ----------
    // If you want to serve the attendance page from this controller,
    // its URL will be /api/attendance/page. If you prefer plain /attendance,
    // move this method into a separate @Controller without the class-level prefix.
    @GetMapping("/page")
    public String attendancePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            String role = auth.getAuthorities().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("ROLE_USER");
            model.addAttribute("username", username);
            model.addAttribute("role", role);
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }
        return "attendance"; // templates/attendance.html
    }
}
