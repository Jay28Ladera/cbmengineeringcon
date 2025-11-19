package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class StudentStatisticsController {

    @Autowired
    private StudentRepository studentRepository;

    @GetMapping("/student-statistics")
    public String showStudentStatisticsPage(Model model) {
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

        // 🔹 Load ALL students for statistics
        List<Student> students = studentRepository.findAll();
        model.addAttribute("students", students);

        // 🔹 Sidebar state flags (adjust names to match your other controllers)
        model.addAttribute("studentInfoExpanded", true);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);

        return "student-statistics";  // student-statistics.html / .thymeleaf
    }
}
