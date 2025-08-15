// Faculty
package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;

@Controller
public class FacultyManagerController {
    @GetMapping("/facultymanager")
    public String faculty(Model model, Principal principal){
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("activePage", "facultymanager");
        return "facultymanager"; // templates/facultymanager.html
    }
}
