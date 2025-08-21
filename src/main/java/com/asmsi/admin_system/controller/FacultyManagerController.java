// src/main/java/com/asmsi/admin_system/controller/FacultyManagerController.java
package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.service.FacultyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class FacultyManagerController {

    private final FacultyService facultyService;

    public FacultyManagerController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping("/facultymanager")
    public String faculty(Model model, Principal principal){
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("activePage", "facultymanager");

        // IMPORTANT: your template iterates "emp : ${ntsList}"
        model.addAttribute("ntsList", facultyService.list());

        return "facultymanager"; // templates/facultymanager.html
    }
}
