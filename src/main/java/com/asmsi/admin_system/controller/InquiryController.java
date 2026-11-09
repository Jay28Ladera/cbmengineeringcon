package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Inquiry;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.repository.InquiryRepository;
import com.asmsi.admin_system.repository.QuotationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping({"/Clients", "/inquiries"})
public class InquiryController {
    private final InquiryRepository inquiries;
    private final QuotationRepository quotations;

    public InquiryController(InquiryRepository inquiries, QuotationRepository quotations) {
        this.inquiries = inquiries;
        this.quotations = quotations;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("inquiries", inquiries.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "clients");
        return "inquiries";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("inquiry", new Inquiry());
        model.addAttribute("activePage", "clients");
        return "inquiry-form";
    }

    @PostMapping
    public String create(@ModelAttribute Inquiry inquiry,
                         @RequestParam(name = "services", required = false) List<String> services,
                         RedirectAttributes redirect) {
        if (services == null || services.isEmpty()) {
            redirect.addFlashAttribute("error", "Select at least one service needed.");
            return "redirect:/Clients/new";
        }
        inquiry.setServicesNeeded(String.join(", ", services));
        inquiry.setStatus("NEW");
        inquiries.save(inquiry);
        redirect.addFlashAttribute("success", "Inquiry saved.");
        return "redirect:/Clients";
    }

    @PostMapping("/{id}/quotation")
    @Transactional
    public String createQuotation(@PathVariable Long id, RedirectAttributes redirect) {
        Inquiry inquiry = inquiries.findById(id).orElseThrow();
        Quotation quotation = quotations.findByInquiryId(id).orElseGet(() -> {
            Quotation draft = new Quotation();
            draft.setInquiry(inquiry);
            Quotation saved = quotations.saveAndFlush(draft);
            saved.setQuotationNumber("Q-%06d".formatted(saved.getId()));
            return quotations.save(saved);
        });
        inquiry.setStatus("QUOTATION_CREATED");
        inquiries.save(inquiry);
        redirect.addFlashAttribute("success", "Quotation draft created.");
        return "redirect:/Quotations/" + quotation.getId();
    }
}
