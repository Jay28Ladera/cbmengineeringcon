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
            entry("/Subcontractors", "Implementation", "Subcontractors", "subcontractors", "bi-person-gear",
                    "Accredited subcontractors, trade scopes and assignments per job order.",
                    "Subcontractor directory and accreditation", "Trade and scope tagging", "Assignment to job orders"),
            entry("/Purchasing/Suppliers", "Purchasing", "Suppliers", "suppliers", "bi-truck",
                    "Supplier records, contacts and payment terms.",
                    "Supplier directory", "Payment terms and contacts", "Supplier performance notes"),
            entry("/Purchasing/Canvasses", "Purchasing", "Canvasses", "canvasses", "bi-clipboard-data",
                    "Price canvassing across suppliers before a purchase is approved.",
                    "Multi-supplier price comparison", "Recommended supplier per item", "Approval routing"),
            entry("/Purchasing/Procurements", "Purchasing", "Procurements", "procurements", "bi-bag-check",
                    "Purchase requests and orders linked to job orders.",
                    "Purchase requests per job order", "Purchase orders", "Delivery tracking"),
            entry("/Purchasing/MarketPrices", "Purchasing", "Market Prices", "market-prices", "bi-graph-up-arrow",
                    "Reference prices for common materials, used when pricing quotations.",
                    "Material price history", "Price reference for quotations"),
            entry("/Inventory/Categories", "Inventory", "Categories", "inventory-categories", "bi-tags",
                    "Material and equipment categories.",
                    "Category list", "Units of measure"),
            entry("/Inventory/Materials", "Inventory", "Materials", "materials", "bi-bricks",
                    "Construction materials on hand and their stock levels.",
                    "Material master list", "Stock on hand", "Reorder levels"),
            entry("/Inventory/StockReceiving", "Inventory", "Stock Receiving", "stock-receiving", "bi-box-arrow-in-down",
                    "Recording deliveries into the warehouse.",
                    "Receiving against purchase orders", "Delivery receipts"),
            entry("/Inventory/StockAdjustment", "Inventory", "Stock Adjustment", "stock-adjustment", "bi-sliders",
                    "Corrections for damaged, lost or recounted stock.",
                    "Adjustment entries with reasons", "Audit history"),
            entry("/Inventory/ToolsAndEquipments", "Inventory", "Tools & Equipment", "tools-equipment", "bi-tools",
                    "Tool and equipment issuance and returns.",
                    "Equipment register", "Issuance to sites and crews", "Maintenance schedule"),
            entry("/Accounting/Payments", "Accounting", "Payments", "payments", "bi-cash-coin",
                    "Client payments received against job orders.",
                    "Progress billing collections", "Official receipts", "Outstanding balances"),
            entry("/Accounting/SubconBillings", "Accounting", "Subcon Billings", "subcon-billings", "bi-receipt",
                    "Billings received from subcontractors.",
                    "Subcontractor billing entries", "Retention and deductions"),
            entry("/Accounting/AdminExpenses", "Accounting", "Admin Expenses", "admin-expenses", "bi-wallet2",
                    "Office and administrative expenses.",
                    "Expense entries by category", "Monthly totals"),
            entry("/HR/Employees", "Human Resources", "Employees", "employees", "bi-person-badge",
                    "Employee records, positions and rates.",
                    "Employee master file", "Positions and daily rates", "Site assignments"),
            entry("/HR/Holidays", "Human Resources", "Holidays", "holidays", "bi-calendar-event",
                    "Regular and special holidays used in payroll.",
                    "Holiday calendar", "Holiday pay rules"),
            entry("/HR/BiometricDevice", "Human Resources", "Biometric Device", "biometric-device", "bi-fingerprint",
                    "Connection settings for attendance devices.",
                    "Device registration", "Attendance log import"),
            entry("/HR/TimeLogs", "Human Resources", "Time Logs", "time-logs", "bi-clock-history",
                    "Raw time-in and time-out records.",
                    "Daily time records", "Missing log alerts"),
            entry("/HR/PayrollPeriod", "Human Resources", "Payroll Periods", "payroll-periods", "bi-calendar-range",
                    "Payroll cut-off periods.",
                    "Period setup", "Locking closed periods"),
            entry("/HR/TimeSheets", "Human Resources", "Time Sheets", "timesheets", "bi-table",
                    "Computed hours per employee per period.",
                    "Regular, overtime and holiday hours", "Project cost allocation"),
            entry("/HR/PaySheets", "Human Resources", "Pay Sheets", "paysheets", "bi-file-earmark-spreadsheet",
                    "Payroll computation and payslips.",
                    "Gross-to-net computation", "Government deductions", "Payslips"),
            entry("/Admin/Settings", "Administration", "Settings", "settings", "bi-gear",
                    "Company details and system preferences.",
                    "Company profile for quotations", "Numbering formats", "Default tax rate"),
            entry("/Admin/Trails", "Administration", "Audit Trail", "trails", "bi-shield-check",
                    "History of deleted and changed records.",
                    "Deleted-record log", "Who changed what and when"),
            entry("/Reports/PaymentListing", "Reports", "Payment Listing", "report-payments", "bi-file-earmark-bar-graph",
                    "All client payments for a date range.", "Filter by date and client", "Export to Excel"),
            entry("/Reports/SubconBillingListing", "Reports", "Subcon Billing Listing", "report-subcon-billings", "bi-file-earmark-bar-graph",
                    "Subcontractor billings for a date range.", "Filter by subcontractor", "Export to Excel"),
            entry("/Reports/AdminExpenseListing", "Reports", "Admin Expense Listing", "report-admin-expenses", "bi-file-earmark-bar-graph",
                    "Administrative expenses for a date range.", "Filter by category", "Export to Excel"),
            entry("/Reports/InventorySummary", "Reports", "Inventory Summary", "report-inventory-summary", "bi-file-earmark-bar-graph",
                    "Current stock and value per material.", "Stock on hand and value"),
            entry("/Reports/InventoryMovement", "Reports", "Inventory Movement", "report-inventory-movement", "bi-file-earmark-bar-graph",
                    "Stock received, issued and adjusted over time.", "Movement by material and period"),
            entry("/Reports/ProjectSalaryCost", "Reports", "Project Salary Cost", "report-project-salary-cost", "bi-file-earmark-bar-graph",
                    "Labor cost charged to each project.", "Labor cost per job order"),
            entry("/Reports/BillingMonitoring", "Reports", "Billing Monitoring", "report-billing-monitoring", "bi-file-earmark-bar-graph",
                    "Billed versus collected per project.", "Billed, collected and balance"),
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
            "/Subcontractors", "/Purchasing/*", "/Inventory/*", "/Accounting/*", "/HR/*",
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
