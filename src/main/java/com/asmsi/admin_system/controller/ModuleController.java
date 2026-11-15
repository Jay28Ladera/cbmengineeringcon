package com.asmsi.admin_system.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * Sidebar modules that are planned but not built yet.
 *
 * Previously these links returned a 404 error page. Each now opens a "module in preparation"
 * page inside the app, so navigation never breaks. When you build a module, add its own
 * controller and delete its entry from {@link #MODULES}.
 */
@Controller
public class ModuleController {

    record Module(String group, String title, String activePage, String icon, String summary, List<String> planned) {}

    private static final Map<String, Module> MODULES = Map.ofEntries(
            entry("/Admin/Settings", "Administration", "Settings", "settings", "bi-gear",
                    "Company details and system preferences.",
                    "Company profile for quotations", "Numbering formats", "Default tax rate"),
            entry("/Admin/Trails", "Administration", "Audit Trail", "trails", "bi-shield-check",
                    "History of deleted and changed records.",
                    "Deleted-record log", "Who changed what and when"),
            entry("/Reports/InventorySummary", "Reports", "Inventory Summary", "report-inventory-summary", "bi-file-earmark-bar-graph",
                    "Current stock and value per material.", "Stock on hand and value"),
            entry("/Reports/InventoryMovement", "Reports", "Inventory Movement", "report-inventory-movement", "bi-file-earmark-bar-graph",
                    "Stock received, issued and adjusted over time.", "Movement by material and period"),
            entry("/Reports/ProjectProfitability", "Reports", "Project Profitability", "report-project-profitability", "bi-file-earmark-bar-graph",
                    "Contract amount against actual costs.", "Revenue, cost and margin per project"),
            entry("/Reports/ProjectExpenseBreakdown", "Reports", "Project Expense Breakdown", "report-project-expenses", "bi-file-earmark-bar-graph",
                    "Material, labor and subcontract costs per project.", "Cost by category"),
            entry("/Reports/ProjectMonthlySummary", "Reports", "Project Monthly Summary", "report-project-monthly", "bi-file-earmark-bar-graph",
                    "Month-by-month project activity.", "Monthly totals per project")
    );

    private static Map.Entry<String, Module> entry(String path, String group, String title, String activePage,
                                                   String icon, String summary, String... planned) {
        return Map.entry(path.toLowerCase(), new Module(group, title, activePage, icon, summary, List.of(planned)));
    }

    @GetMapping({
            "/Purchasing/*", "/Inventory/*", "/Accounting/*", "/HR/*",
            "/Admin/Settings", "/Admin/Trails", "/Reports/*"
    })
    public String module(HttpServletRequest request, Model model) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Module module = MODULES.get(path.replaceAll("/+$", "").toLowerCase());
        if (module == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        model.addAttribute("module", module);
        model.addAttribute("activePage", module.activePage());
        return "module-pending";
    }

    // Report shortcuts that already have real data behind them.
    @GetMapping("/Reports/QuotationListing")
    public String quotationListing() { return "redirect:/Quotations"; }

    @GetMapping("/Reports/JobOrderListing")
    public String jobOrderListing() { return "redirect:/JobOrders"; }

    @GetMapping({"/Admin/Users", "/admin/users"})
    public String adminUsers() { return "redirect:/users"; }

    @GetMapping("/settings")
    public String accountSettings() { return "redirect:/profile"; }
}
