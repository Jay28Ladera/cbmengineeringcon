package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.JobOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Controller
@RequestMapping({"/JobOrders", "/Implementation/JobOrders"})
public class JobOrderController {
    private final JobOrderRepository jobOrders;
    public JobOrderController(JobOrderRepository jobOrders) { this.jobOrders = jobOrders; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("jobOrders", jobOrders.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "job-orders");
        return "job-orders";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        var order = jobOrders.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job order not found"));
        model.addAttribute("jobOrder", order);
        model.addAttribute("nextStatuses", allowedNext(order.getStatus()));
        model.addAttribute("activePage", "job-orders");
        return "job-order-detail";
    }

    /** Status changes the workflow allows from the current status (shown as buttons on the page). */
    static List<String> allowedNext(String current) {
        return switch (current == null ? "" : current) {
            case "PLANNED" -> List.of("IN_PROGRESS", "ON_HOLD");
            case "IN_PROGRESS" -> List.of("ON_HOLD", "COMPLETED");
            case "ON_HOLD" -> List.of("PLANNED", "IN_PROGRESS");
            default -> List.of();
        };
    }

    @PostMapping("/{id}/status")
    @Transactional
    public String updateStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes redirect) {
        var order = jobOrders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job order not found"));
        String next = status == null ? "" : status.toUpperCase();
        boolean allowed = allowedNext(order.getStatus()).contains(next);
        if (allowed) {
            order.setStatus(next);
            jobOrders.save(order);
            redirect.addFlashAttribute("success", "Job order status updated.");
        } else {
            redirect.addFlashAttribute("error", "That job order status change is not allowed.");
        }
        return "redirect:/JobOrders/" + id;
    }
}
