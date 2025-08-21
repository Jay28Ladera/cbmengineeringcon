// src/main/java/com/asmsi/admin_system/controller/AsmsiManagementController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.service.AsmsiAlumniService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AsmsiManagementController {

    private final AsmsiAlumniService asmsiService;

    public AsmsiManagementController(AsmsiAlumniService asmsiService) {
        this.asmsiService = asmsiService;
    }

    private void setCommon(Model model, Principal principal, String activePage) {
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("asmsiExpanded", true);
        model.addAttribute("activePage", activePage);
    }

    @GetMapping("/asmsimanager")
    public String masterlist(Model model, Principal principal) {
        setCommon(model, principal, "asmsi-masterlist");
        model.addAttribute("alumniList", asmsiService.list());
        return "asmsimanager";
    }

    @GetMapping("/asmsi-management")
    public String base() { return "redirect:/asmsimanager"; }

    @GetMapping("/asmsi-management/masterlist")
    public String legacyMasterlist() { return "redirect:/asmsimanager"; }
}
