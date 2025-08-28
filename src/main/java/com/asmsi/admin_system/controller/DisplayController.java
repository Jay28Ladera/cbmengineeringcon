package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DisplayController {

    @GetMapping("/display")
    public String displayPage(Model model) {
        // Keeps Family Day menu open in sidebar
        model.addAttribute("familyDayExpanded", true);

        // Highlight "Display" as the active link
        model.addAttribute("activePage", "display");

        // Return the template name (display.html)
        return "display";
    }
}
