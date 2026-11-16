package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.accounting.CompanyInfo;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.service.EmailService;
import com.asmsi.admin_system.service.PhotoStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Admin › Settings: company profile, document defaults, numbering, email and system status. */
@Controller
public class SettingsController {

    private final SettingsService settings;
    private final AuditService audit;
    private final EmailService email;
    private final PhotoStorageService photos;
    private final JdbcTemplate jdbc;

    public SettingsController(SettingsService settings, AuditService audit, EmailService email, PhotoStorageService photos, JdbcTemplate jdbc) {
        this.settings = settings; this.audit = audit; this.email = email; this.photos = photos; this.jdbc = jdbc;
    }

    @GetMapping("/Admin/Settings")
    public String page(@RequestParam(required = false) String tab, Model model) {
        settings.evict(); // show what is saved, even if changed on another server
        model.addAttribute("co", settings.company());
        model.addAttribute("app", settings.app());
        model.addAttribute("series", settings.series());
        model.addAttribute("tab", tab == null ? "company" : tab);
        model.addAttribute("mailOn", email.isConfigured());
        model.addAttribute("mailFrom", email.getFromAddress());
        model.addAttribute("quoteFrom", email.getQuoteFromAddress());
        model.addAttribute("baseUrl", email.getBaseUrl());
        model.addAttribute("cloudOn", photos.isCloudConfigured());
        model.addAttribute("me", me());
        model.addAttribute("status", status());
        model.addAttribute("activePage", "settings");
        return "admin-settings";
    }

    @PostMapping("/Admin/Settings/company")
    public String company(@RequestParam String name, @RequestParam(required = false) String address, @RequestParam(required = false) String zipCode,
                          @RequestParam(required = false) String tin, @RequestParam(required = false) String rdoCode,
                          @RequestParam(required = false) String phone, @RequestParam(required = false) String emailAddress,
                          @RequestParam(required = false) String website, @RequestParam(required = false) String payableTo,
                          @RequestParam(required = false) String signatory, @RequestParam(required = false) String signatoryTitle,
                          @RequestParam(required = false) String signatoryTin, RedirectAttributes ra) {
        if (name == null || name.isBlank()) { ra.addFlashAttribute("error", "Enter the company name."); return "redirect:/Admin/Settings"; }
        settings.evict();
        CompanyInfo c = settings.company();
        c.name = cut(name, 160); c.address = cut(address, 300); c.zipCode = cut(zipCode, 10); c.tin = cut(tin, 40); c.rdoCode = cut(rdoCode, 10);
        c.phone = cut(phone, 60); c.email = cut(emailAddress, 160); c.website = cut(website, 160); c.payableTo = cut(payableTo, 160);
        c.signatory = cut(signatory, 160); c.signatoryTitle = cut(signatoryTitle, 120); c.signatoryTin = cut(signatoryTin, 40);
        settings.saveCompany(c);
        ra.addFlashAttribute("success", "Company profile saved. It now shows on quotations, POs, vouchers, billings and BIR forms.");
        return "redirect:/Admin/Settings";
    }

    @PostMapping("/Admin/Settings/defaults")
    public String defaults(@RequestParam(required = false) String quoteTaxRate, @RequestParam(required = false) String quoteTerms,
                           @RequestParam(required = false) String quoteValidDays, RedirectAttributes ra) {
        AppSettings a = settings.app();
        try {
            BigDecimal t = quoteTaxRate == null || quoteTaxRate.isBlank() ? BigDecimal.ZERO : new BigDecimal(quoteTaxRate.trim().replace("%", ""));
            if (t.signum() < 0 || t.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("The tax rate must be from 0 to 100%.");
            int d = quoteValidDays == null || quoteValidDays.isBlank() ? 30 : Integer.parseInt(quoteValidDays.trim());
            if (d < 1 || d > 365) throw new IllegalArgumentException("Validity must be from 1 to 365 days.");
            a.quoteTaxRate = t; a.quoteValidDays = d; a.quoteTerms = cut(quoteTerms, 4000);
            settings.saveApp(a, who());
            ra.addFlashAttribute("success", "Quotation defaults saved. They apply to new quotations.");
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("error", "Enter numbers for the tax rate and validity.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/Admin/Settings?tab=defaults";
    }

    @PostMapping("/Admin/Settings/numbering")
    public String numbering(@RequestParam String prefix, @RequestParam String next, RedirectAttributes ra) {
        try {
            String n = settings.setNext(prefix, Long.parseLong(next.trim()));
            ra.addFlashAttribute("success", "The next one will be " + n + ".");
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("error", "Enter a whole number.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/Admin/Settings?tab=numbering";
    }

    @PostMapping("/Admin/Settings/test-email")
    public String testEmail(@RequestParam String to, RedirectAttributes ra) {
        String name = settings.company().name;
        String why = email.trySendHtml(to, null, null, "Test email from the " + name + " portal",
                "<p>This is a test from <b>Admin › Settings</b>. If you can read it, email from the portal works.</p><p>" + esc(name) + "</p>",
                "This is a test from Admin › Settings. If you can read it, email from the portal works.\n\n" + name);
        if (why == null) ra.addFlashAttribute("success", "Test email sent to " + to + ". Check the inbox (and spam).");
        else ra.addFlashAttribute("error", "Not sent: " + why);
        return "redirect:/Admin/Settings?tab=system";
    }

    @PostMapping("/Admin/Settings/audit")
    public String auditKeep(@RequestParam(required = false) String keepDays, RedirectAttributes ra) {
        try {
            int d = keepDays == null || keepDays.isBlank() ? 0 : Integer.parseInt(keepDays.trim());
            if (d != 0 && d < 90) throw new IllegalArgumentException("Keep at least 90 days (or 0 to keep everything).");
            AppSettings a = settings.app();
            a.auditKeepDays = d;
            settings.saveApp(a, who());
            int removed = audit.purge(d, true);
            ra.addFlashAttribute("success", d == 0 ? "The audit trail keeps everything." : "The audit trail keeps " + d + " days." + (removed > 0 ? " " + removed + " older lines removed." : ""));
        } catch (NumberFormatException e) {
            ra.addFlashAttribute("error", "Enter a whole number of days.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/Admin/Settings?tab=system";
    }

    // ----------------------------------------------------------------

    private Map<String, String> status() {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            Map<String, Object> db = jdbc.queryForMap("select current_database() db, pg_size_pretty(pg_database_size(current_database())) size,"
                    + " split_part(version(), ' ', 2) ver, (select count(*) from accounts) users, (select count(*) from sys_audit) audit");
            m.put("Database", db.get("db") + " · PostgreSQL " + db.get("ver") + " · " + db.get("size"));
            m.put("User accounts", String.valueOf(db.get("users")));
            m.put("Audit trail lines", String.valueOf(db.get("audit")));
        } catch (RuntimeException e) {
            m.put("Database", "Could not read: " + e.getMessage());
        }
        Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        m.put("Running for", up.toDays() > 0 ? up.toDays() + " d " + up.toHoursPart() + " h" : up.toHours() + " h " + up.toMinutesPart() + " min");
        m.put("Java", System.getProperty("java.version"));
        Runtime rt = Runtime.getRuntime();
        m.put("Memory", (rt.totalMemory() - rt.freeMemory()) / 1048576 + " MB used of " + rt.maxMemory() / 1048576 + " MB");
        return m;
    }

    private static User me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof User u ? u : null;
    }

    private static String who() { User u = me(); return u == null ? "?" : u.getName(); }

    private static String cut(String s, int n) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
