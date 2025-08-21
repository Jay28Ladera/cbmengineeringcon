// src/main/java/com/asmsi/admin_system/controller/NtsManagerController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.NonTeachingStaff;
import com.asmsi.admin_system.service.NtsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

@Controller
public class NtsManagerController {

    private final NtsService ntsService;

    public NtsManagerController(NtsService ntsService) {
        this.ntsService = ntsService;
    }

    @GetMapping("/ntsmanager")
    public String nts(Model model, Principal principal){
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("activePage", "ntsmanager");

        List<NonTeachingStaff> ntsList = ntsService.list();
        model.addAttribute("ntsList", ntsList);

        return "ntsmanager"; // templates/ntsmanager.html
    }
}
