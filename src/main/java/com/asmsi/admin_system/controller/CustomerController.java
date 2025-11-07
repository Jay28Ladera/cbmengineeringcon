package com.asmsi.admin_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CustomerController {

    @GetMapping("/customers")
    public String customerDisplay(Model model) {
        // Optional: load customer data from DB and add to model
        // model.addAttribute("customers", customerService.findAll());

        return "customerdisplay"; // this resolves to src/main/resources/templates/customerdisplay.html
    }
}
