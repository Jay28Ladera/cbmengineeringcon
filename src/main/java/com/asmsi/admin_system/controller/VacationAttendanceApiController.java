// src/main/java/com/asmsi/admin_system/controller/VacationAttendanceApiController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.VacationAttendance;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.VacationAttendanceRepository;
import com.asmsi.admin_system.repository.StudentRepository;
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
import java.util.Optional;

@Controller
@RequestMapping("/api/vacationattendance")
public class VacationAttendanceApiController {

    private final VacationAttendanceRepository vacationAttendanceRepository;
    private final StudentRepository studentRepository;

    public VacationAttendanceApiController(VacationAttendanceRepository vacationAttendanceRepository,
                                           StudentRepository studentRepository) {
        this.vacationAttendanceRepository = vacationAttendanceRepository;
        this.studentRepository = studentRepository;
    }

    // --- dropdown data (accounts) ---
    @GetMapping("/accounts")
    @ResponseBody
    public List<String> accounts() {
        return vacationAttendanceRepository.findDistinctAccounts();
    }

    // ---------- submit vacation attendance ----------
    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<String> submitAttendance(@RequestBody VacationAttendance log) {
        String idNumber = log.getIdNumber();
        LocalDate today = LocalDate.now();

        // Check latest record for today
        Optional<VacationAttendance> latestLogOpt =
                vacationAttendanceRepository.findTopByIdNumberAndDateOrderByTimeInDesc(idNumber, today);

        // If last record has no timeOut → set timeOut (TIME-OUT mechanics)
        if (latestLogOpt.isPresent() && latestLogOpt.get().getTimeOut() == null) {
            VacationAttendance latestLog = latestLogOpt.get();
            latestLog.setTimeOut(LocalTime.now());
            vacationAttendanceRepository.save(latestLog);
            return ResponseEntity.ok("Time Out recorded.");
        }

        // Otherwise → create a new TIME-IN record
        if (log.getDate() == null) log.setDate(LocalDate.now());
        if (log.getTimeIn() == null) log.setTimeIn(LocalTime.now());

        vacationAttendanceRepository.save(log);
        return ResponseEntity.ok("Attendance recorded.");
    }

    // ---------- logs by ID ----------
    @GetMapping("/logs/{idNumber}")
    @ResponseBody
    public List<VacationAttendance> getLogsByIdNumber(@PathVariable String idNumber) {
        return vacationAttendanceRepository.findByIdNumber(idNumber);
    }

    // ---------- student lookup (same as old) ----------
    @GetMapping("/student/{idNumber}")
    @ResponseBody
    public ResponseEntity<Student> fetchStudentData(@PathVariable String idNumber) {
        return studentRepository.findByIdNumber(idNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ---------- all logs (sorted) ----------
    @GetMapping("/logs")
    @ResponseBody
    public ResponseEntity<List<VacationAttendance>> getAllLogs() {
        List<VacationAttendance> logs = vacationAttendanceRepository.findAll(
                Sort.by(Sort.Direction.DESC, "date", "timeIn")
        );
        return ResponseEntity.ok(logs);
    }

    // ---------- page renderer (vacationattendance.html) ----------
    @GetMapping("/page")
    public String vacationAttendancePage(Model model) {
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
        return "vacationattendance"; // renders vacationattendance.html
    }
}
