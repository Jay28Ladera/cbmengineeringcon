package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.QuotationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping({"/Quotations", "/quotations"})
public class QuotationController {
    private final QuotationRepository quotations;
    public QuotationController(QuotationRepository quotations) { this.quotations = quotations; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("quotations", quotations.findAll());
        model.addAttribute("activePage", "quotations");
        return "quotations";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("quotation", quotations.findById(id).orElseThrow());
        model.addAttribute("activePage", "quotations");
        return "quotation-detail";
    }
}
