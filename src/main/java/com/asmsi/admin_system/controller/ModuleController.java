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

    // Every planned module is built now. Add an entry here for a future one that is not ready yet.
    private static final Map<String, Module> MODULES = Map.of();

    private static Map.Entry<String, Module> entry(String path, String group, String title, String activePage,
                                                   String icon, String summary, String... planned) {
        return Map.entry(path.toLowerCase(), new Module(group, title, activePage, icon, summary, List.of(planned)));
    }

    @GetMapping({
            "/Purchasing/*", "/Inventory/*", "/Accounting/*", "/HR/*", "/Reports/*"
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
