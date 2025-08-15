// ASMSI Management (menus open + pages resolve)
package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AsmsiManagementController {

    // ---- Helpers -----------------------------------------------------------
    private void setCommon(Model model, Principal principal, String activePage) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        model.addAttribute("asmsiExpanded", true);   // open ASMSI section in sidebar
        model.addAttribute("activePage", activePage);
    }

    // ---- Alumni Statistics -------------------------------------------------
    @GetMapping("/asmsi-management/alumni-stats")
    public String alumniStats(Model model, Principal principal) {
        setCommon(model, principal, "alumni-stats");
        return "asmsi/alumni-stats";   // templates/asmsi/alumni-stats.html
    }

    // ---- Masterlist (NEW canonical route) ---------------------------------
    @GetMapping("/asmsimanager")
    public String asmsiManager(Model model, Principal principal) {
        setCommon(model, principal, "asmsi-masterlist");
        return "asmsimanager";         // templates/asmsimanager.html
    }

    // Backward-compat: old path → redirect to new canonical route
    @GetMapping("/asmsi-management/masterlist")
    public String legacyMasterlist() {
        return "redirect:/asmsimanager";
    }

    // Optional: base path redirects to masterlist (nice default)
    @GetMapping("/asmsi-management")
    public String asmsiBase() {
        return "redirect:/asmsimanager";
    }
}
