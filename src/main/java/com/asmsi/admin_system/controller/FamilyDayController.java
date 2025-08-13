package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FamilyDayController {

    @GetMapping("/visitingday")
    public String visitingDayPage() {
        return "visitingday"; // This loads visitingday.html from templates
    }
}
