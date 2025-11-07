// src/main/java/com/asmsi/admin_system/controller/AttendanceReportController.java
package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AttendanceReportController {

    @GetMapping("/attendance-report")
    public String showAttendanceReportPage(Model model) {
        model.addAttribute("activePage", "attendance-report");
        model.addAttribute("attendanceExpanded", true);

        // optional fragment flags (set as you use them)
        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);

        return "attendance-report"; // templates/attendance-report.html
    }
}
