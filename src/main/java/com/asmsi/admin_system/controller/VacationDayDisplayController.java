package com.asmsi.admin_system.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VacationDayDisplayController {

    @GetMapping("/vacationdaydisplay")
    public String showVacationDayDisplay(Model model) {
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

        // for sidebar expansion + active state
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", false);
        model.addAttribute("familyDayExpanded", false);
        model.addAttribute("activePage", "vacation-display");

        // this must match src/main/resources/templates/vacationdaydisplay.html
        return "vacationdaydisplay";
    }
}
