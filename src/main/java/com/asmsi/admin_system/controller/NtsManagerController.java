// NTS
package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;

@Controller
public class NtsManagerController {
    @GetMapping("/ntsmanager")
    public String nts(Model model, Principal principal){
        if (principal != null) model.addAttribute("username", principal.getName());
        model.addAttribute("activePage", "ntsmanager");
        return "ntsmanager"; // templates/ntsmanager.html
    }
}
