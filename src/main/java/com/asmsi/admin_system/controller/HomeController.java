package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.InquiryRepository;
import com.asmsi.admin_system.repository.JobOrderRepository;
import com.asmsi.admin_system.repository.QuotationRepository;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final InquiryRepository inquiries;
    private final QuotationRepository quotations;
    private final JobOrderRepository jobOrders;
    private final UserRepository users;

    public HomeController(InquiryRepository inquiries, QuotationRepository quotations,
                          JobOrderRepository jobOrders, UserRepository users) {
        this.inquiries = inquiries;
        this.quotations = quotations;
        this.jobOrders = jobOrders;
        this.users = users;
    }

    @GetMapping("/home")
    public String home(Model model) {
        Map<String, Long> quoteCounts = toMap(quotations.countByStatusGrouped());
        Map<String, Long> jobCounts = toMap(jobOrders.countByStatusGrouped());

        long activeJobs = jobCounts.getOrDefault("PLANNED", 0L)
                + jobCounts.getOrDefault("IN_PROGRESS", 0L)
                + jobCounts.getOrDefault("ON_HOLD", 0L);

        model.addAttribute("newInquiries", inquiries.countByStatus("NEW"));
        model.addAttribute("draftQuotations", quoteCounts.getOrDefault("DRAFT", 0L));
        model.addAttribute("awaitingDecision", quoteCounts.getOrDefault("SENT", 0L));
        model.addAttribute("acceptedQuotations", quoteCounts.getOrDefault("ACCEPTED", 0L));
        model.addAttribute("activeJobOrders", activeJobs);
        model.addAttribute("inProgressJobs", jobCounts.getOrDefault("IN_PROGRESS", 0L));
        model.addAttribute("completedJobs", jobCounts.getOrDefault("COMPLETED", 0L));
        model.addAttribute("pendingApprovals", users.countByIsApprovedFalse());
        model.addAttribute("recentInquiries", inquiries.findTop5ByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "home");
        return "home";
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        return map;
    }
}
