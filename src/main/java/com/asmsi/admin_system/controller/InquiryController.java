package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Inquiry;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.repository.InquiryRepository;
import com.asmsi.admin_system.repository.QuotationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        // inquiry id -> quotation, so rows with a quotation link straight to it
        Map<Long, Quotation> quotesByInquiry = new HashMap<>();
        for (Quotation q : quotations.findAllWithInquiryId()) quotesByInquiry.put(q.getInquiry().getId(), q);
        model.addAttribute("quotesByInquiry", quotesByInquiry);
        if (!model.containsAttribute("inquiry")) {
            model.addAttribute("inquiry", new Inquiry());
        }
        model.addAttribute("activePage", "clients");
        return "inquiries";
    }

    /** Full inquiry for the details dialog on the Clients page. */
    @GetMapping("/{id}/details")
    @ResponseBody
    public Map<String, Object> details(@PathVariable Long id) {
        Inquiry i = inquiries.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d, yyyy");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", i.getId());
        result.put("fullName", i.getFullName());
        result.put("company", i.getCompany());
        result.put("email", i.getEmail());
        result.put("phone", i.getPhone());
        result.put("preferredContactMethod", i.getPreferredContactMethod());
        result.put("servicesNeeded", i.getServicesNeeded());
        result.put("projectName", i.getProjectName());
        result.put("projectLocation", i.getProjectLocation());
        result.put("propertyType", i.getPropertyType());
        result.put("workNeeded", i.getWorkNeeded());
        result.put("floorAreaOrQuantity", i.getFloorAreaOrQuantity());
        result.put("estimatedBudget", i.getEstimatedBudget());
        result.put("targetStartDate", i.getTargetStartDate() == null ? null : i.getTargetStartDate().format(day));
        result.put("targetCompletionDate", i.getTargetCompletionDate() == null ? null : i.getTargetCompletionDate().format(day));
        result.put("siteVisit", i.getSiteVisit());
        result.put("projectDetails", i.getProjectDetails());
        result.put("plansUrl", i.getPlansUrl());
        result.put("status", i.getStatus());
        result.put("createdAt", i.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")));
        result.put("quotationId", quotations.findByInquiryId(id).map(Quotation::getId).orElse(null));
        return result;
    }

    @GetMapping("/new")
    public String createForm() {
        return "redirect:/Clients?openInquiry=1";
    }

    /** Public website quote form: persist the request into the same Clients inquiry list. */
    @PostMapping("/public")
    public String createFromWebsite(
            @RequestParam("Name") String fullName,
            @RequestParam(name = "Company", required = false) String company,
            @RequestParam("Email") String email,
            @RequestParam("Phone") String phone,
            @RequestParam(name = "Preferred contact", required = false) String preferredContact,
            @RequestParam(name = "Services", required = false) List<String> services,
            @RequestParam("Project name") String projectName,
            @RequestParam("Location") String location,
            @RequestParam(name = "Property type", required = false) String propertyType,
            @RequestParam(name = "Work needed", required = false) String workNeeded,
            @RequestParam(name = "Area / quantity", required = false) String area,
            @RequestParam(name = "Budget", required = false) String budget,
            @RequestParam(name = "Target start", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetStart,
            @RequestParam(name = "Target completion", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetCompletion,
            @RequestParam(name = "Site visit", required = false) String siteVisit,
            @RequestParam("Project details") String projectDetails,
            @RequestParam(name = "Reference link", required = false) String referenceLink,
            @RequestParam(name = "website", required = false) String honeypot) {
        // Spam guard: "website" is a hidden field that people never see or fill in; bots usually do.
        if (honeypot != null && !honeypot.isBlank()) {
            return "redirect:/?inquirySubmitted#inquiry";
        }
        if (services == null || services.isEmpty()) {
            return "redirect:/?inquiryError#inquiry";
        }
        if (inquiries.existsByEmailIgnoreCaseAndProjectNameIgnoreCase(email.trim(), projectName.trim())) {
            return "redirect:/?duplicateInquiry#inquiry";
        }
        Inquiry inquiry = new Inquiry();
        inquiry.setFullName(fullName.trim());
        inquiry.setCompany(company);
        inquiry.setEmail(email.trim());
        inquiry.setPhone(phone.trim());
        inquiry.setPreferredContactMethod(preferredContact);
        inquiry.setServicesNeeded(String.join(", ", services));
        inquiry.setProjectName(projectName.trim());
        inquiry.setProjectLocation(location.trim());
        inquiry.setPropertyType(propertyType);
        inquiry.setWorkNeeded(workNeeded);
        inquiry.setFloorAreaOrQuantity(area);
        inquiry.setEstimatedBudget(budget);
        inquiry.setTargetStartDate(targetStart);
        inquiry.setTargetCompletionDate(targetCompletion);
        inquiry.setSiteVisit(siteVisit);
        inquiry.setProjectDetails(projectDetails.trim());
        inquiry.setPlansUrl(referenceLink);
        inquiry.setStatus("NEW");
        inquiries.save(inquiry);
        return "redirect:/?inquirySubmitted#inquiry";
    }

    @PostMapping
    public String create(@ModelAttribute Inquiry inquiry,
                         @RequestParam(name = "services", required = false) List<String> services,
                         RedirectAttributes redirect) {
        if (services == null || services.isEmpty()) {
            redirect.addFlashAttribute("error", "Select at least one service needed.");
            return "redirect:/Clients?openInquiry=1";
        }
        if (isDuplicate(inquiry)) {
            redirect.addFlashAttribute("error", "An inquiry from this email for this project already exists. Check the existing client row first.");
            return "redirect:/Clients";
        }
        inquiry.setServicesNeeded(String.join(", ", services));
        inquiry.setStatus("NEW");
        inquiries.save(inquiry);
        redirect.addFlashAttribute("success", "Inquiry saved.");
        return "redirect:/Clients";
    }

    @PostMapping("/ajax")
    @ResponseBody
    public Map<String, Object> createWithoutReload(
            @ModelAttribute Inquiry inquiry,
            @RequestParam(name = "services", required = false) List<String> services,
            HttpServletResponse response) {
        if (services == null || services.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return Map.of("error", "Select at least one service needed.");
        }
        if (isDuplicate(inquiry)) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return Map.of("error", "An inquiry from this email for this project already exists. Check the client list first.");
        }
        inquiry.setServicesNeeded(String.join(", ", services));
        inquiry.setStatus("NEW");
        Inquiry saved = inquiries.save(inquiry);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("fullName", saved.getFullName());
        result.put("email", saved.getEmail());
        result.put("phone", saved.getPhone());
        result.put("projectName", saved.getProjectName());
        result.put("projectLocation", saved.getProjectLocation());
        result.put("servicesNeeded", saved.getServicesNeeded());
        result.put("status", saved.getStatus());
        result.put("createdAt", saved.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        return result;
    }

    private boolean isDuplicate(Inquiry inquiry) {
        return inquiry.getEmail() != null && inquiry.getProjectName() != null
                && inquiries.existsByEmailIgnoreCaseAndProjectNameIgnoreCase(inquiry.getEmail().trim(), inquiry.getProjectName().trim());
    }

    @PostMapping("/{id}/quotation")
    @Transactional
    public String createQuotation(@PathVariable Long id, RedirectAttributes redirect) {
        Inquiry inquiry = inquiries.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
        Quotation quotation = getOrCreateQuotation(inquiry);
        inquiry.setStatus("QUOTATION_CREATED");
        inquiries.save(inquiry);
        redirect.addFlashAttribute("success", "Quotation draft created.");
        return "redirect:/Quotations/" + quotation.getId();
    }

    @PostMapping("/{id}/quotation/modal")
    @ResponseBody
    @Transactional
    public Map<String, Object> createQuotationForModal(@PathVariable Long id) {
        Inquiry inquiry = inquiries.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
        Quotation quotation = getOrCreateQuotation(inquiry);
        inquiry.setStatus("QUOTATION_CREATED");
        inquiries.save(inquiry);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", quotation.getId());
        result.put("quotationNumber", quotation.getQuotationNumber());
        result.put("status", quotation.getStatus());
        result.put("clientName", inquiry.getFullName());
        result.put("company", inquiry.getCompany());
        result.put("email", inquiry.getEmail());
        result.put("phone", inquiry.getPhone());
        result.put("projectName", inquiry.getProjectName());
        result.put("projectLocation", inquiry.getProjectLocation());
        result.put("servicesNeeded", inquiry.getServicesNeeded());
        result.put("projectDetails", inquiry.getProjectDetails());
        result.put("workNeeded", inquiry.getWorkNeeded());
        result.put("area", inquiry.getFloorAreaOrQuantity());
        result.put("budget", inquiry.getEstimatedBudget());
        result.put("siteVisit", inquiry.getSiteVisit());
        return result;
    }

    private Quotation getOrCreateQuotation(Inquiry inquiry) {
        return quotations.findByInquiryId(inquiry.getId()).orElseGet(() -> {
            Quotation draft = new Quotation();
            draft.setInquiry(inquiry);
            Quotation saved = quotations.saveAndFlush(draft);
            saved.setQuotationNumber("Q-%06d".formatted(saved.getId()));
            return quotations.save(saved);
        });
    }
}
