package com.asmsi.admin_system.entity;

import java.util.List;

/**
 * Parts of the portal that can be switched on per department or per person.
 * Dashboard, My Profile and the Organizational Chart are open to everyone signed in;
 * Administration is for admins only.
 */
public enum AccessModule {
    INQUIRY("Inquiry", "Client inquiries and quotations", "bi-inboxes", List.of("/Clients", "/Quotations")),
    IMPLEMENTATION("Implementation", "Job orders and subcontractors", "bi-diagram-3", List.of("/JobOrders", "/Subcontractors")),
    PURCHASING("Purchasing", "Suppliers, canvasses, procurements", "bi-cart3", List.of("/Purchasing")),
    INVENTORY("Inventory", "Materials, stock, tools and equipment", "bi-boxes", List.of("/Inventory")),
    ACCOUNTING("Accounting", "Payments, billings, expenses", "bi-calculator", List.of("/Accounting")),
    HR("Human Resources", "Employees, time logs, payroll", "bi-people", List.of("/HR")),
    REPORTS("Reports", "All report listings", "bi-bar-chart-line", List.of("/Reports"));

    private final String label;
    private final String description;
    private final String icon;
    private final List<String> pathPrefixes;

    AccessModule(String label, String description, String icon, List<String> pathPrefixes) {
        this.label = label;
        this.description = description;
        this.icon = icon;
        this.pathPrefixes = pathPrefixes;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public String getKey() { return name(); }

    /** The module that guards a URL path, or null if the path is open to everyone signed in. */
    public static AccessModule forPath(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        for (AccessModule m : values()) {
            for (String prefix : m.pathPrefixes) {
                String p = prefix.toLowerCase();
                if (lower.equals(p) || lower.startsWith(p + "/")) return m;
            }
        }
        return null;
    }

    public static AccessModule parse(String value) {
        if (value == null) return null;
        try { return valueOf(value.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
