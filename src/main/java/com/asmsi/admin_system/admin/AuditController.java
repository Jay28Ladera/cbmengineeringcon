package com.asmsi.admin_system.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Admin › Audit Trail: who created, changed or deleted what, and sign-ins. */
@Controller
public class AuditController {

    private final AuditService audit;
    private final SettingsService settings;

    public AuditController(AuditService audit, SettingsService settings) { this.audit = audit; this.settings = settings; }

    @GetMapping({"/Admin/Trails", "/Admin/AuditTrail"})
    public String trail(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                        @RequestParam(required = false) String user, @RequestParam(required = false) String module,
                        @RequestParam(required = false) String action, @RequestParam(required = false) String q,
                        @RequestParam(required = false) String entity, @RequestParam(required = false) String entityId,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        audit.purge(settings.app().getAuditKeepDays(), false);
        LocalDate f = date(from), t = date(to);
        boolean record = blank(entity) == null || blank(entityId) == null ? false : true;
        if (!record && f == null && t == null) { f = LocalDate.now().minusDays(30); }
        var filter = new AuditService.Filter(f, t, blank(user), blank(module), blank(action), blank(q),
                record ? entity.trim() : null, record ? entityId.trim() : null);
        var result = audit.search(filter, Math.max(0, page));
        model.addAttribute("result", result);
        model.addAttribute("f", filter);
        model.addAttribute("record", record);
        model.addAttribute("recordTitle", record ? AuditService.humanize(entity) + " #" + entityId : null);
        model.addAttribute("users", audit.users());
        model.addAttribute("modules", audit.modules());
        model.addAttribute("stats", audit.stats());
        model.addAttribute("keepDays", settings.app().getAuditKeepDays());
        model.addAttribute("activePage", "trails");
        return "admin-trail";
    }

    private static LocalDate date(String s) {
        try { return s == null || s.isBlank() ? null : LocalDate.parse(s.trim()); } catch (RuntimeException e) { return null; }
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
