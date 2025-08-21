// src/main/java/com/asmsi/admin_system/controller/AsmsiStatisticsController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.service.AsmsiAlumniService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList;

@Controller
public class AsmsiStatisticsController {

    private final AsmsiAlumniService asmsiService;

    public AsmsiStatisticsController(AsmsiAlumniService asmsiService) {
        this.asmsiService = asmsiService;
    }

    private void setCommon(Model model, Principal principal) {
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("asmsiExpanded", true);
        model.addAttribute("activePage", "alumni-stats");
    }

    /** Canonical route for the separate page */
    @GetMapping("/asmsistatistics")
    public String statistics(Model model, Principal principal) {
        setCommon(model, principal);

        var byBatch = asmsiService.countByBatch();     // Map<String, Long>
        var stats   = asmsiService.employmentStats();  // EmploymentStats

        model.addAttribute("batchLabels", new ArrayList<>(byBatch.keySet()));
        model.addAttribute("batchCounts", new ArrayList<>(byBatch.values()));
        model.addAttribute("empStats", stats);

        return "asmsistatistics"; // templates/asmsistatistics.html
    }

    /** Back-compat: if anything still hits the old path, redirect to the new page */
    @GetMapping("/asmsi-management/alumni-stats")
    public String legacyRedirect() {
        return "redirect:/asmsistatistics";
    }
}
