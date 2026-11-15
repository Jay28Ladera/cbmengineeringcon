package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.AccessModule;
import com.asmsi.admin_system.entity.DepartmentAccess;
import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.repository.DepartmentAccessRepository;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.service.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/**
 * Administration → Department Access: which modules each department gets by default.
 * Departments come from the boxes on the Organizational Chart. Admin-only (/Admin/** in SecurityConfig).
 */
@Controller
public class DepartmentAccessController {

    private final DepartmentAccessRepository departments;
    private final OrganizationNodeRepository nodes;
    private final AccessService accessService;

    public DepartmentAccessController(DepartmentAccessRepository departments, OrganizationNodeRepository nodes,
                                      AccessService accessService) {
        this.departments = departments;
        this.nodes = nodes;
        this.accessService = accessService;
    }

    public record Row(String name, Set<String> modules, boolean saved, long people, long accounts, boolean onChart) {}

    @GetMapping("/Admin/DepartmentAccess")
    public String page(Model model) {
        Map<String, String> names = new TreeMap<>();
        Map<String, long[]> counts = new HashMap<>();
        for (OrganizationNode n : nodes.findAll()) {
            if (n.isResigned() || n.getDepartment() == null || n.getDepartment().isBlank()) continue;
            String key = DepartmentAccess.keyOf(n.getDepartment());
            names.putIfAbsent(key, n.getDepartment().trim().replaceAll("\\s+", " "));
            long[] c = counts.computeIfAbsent(key, k -> new long[2]);
            if (n.getPersonName() != null && !n.getPersonName().isBlank()) c[0]++;
            if (n.getAccountUserId() != null) c[1]++;
        }
        Map<String, DepartmentAccess> saved = new HashMap<>();
        departments.findAll().forEach(d -> saved.put(d.getDepartmentKey(), d));
        saved.forEach((k, d) -> names.putIfAbsent(k, d.getDepartmentName()));

        List<Row> rows = new ArrayList<>();
        names.forEach((key, name) -> {
            DepartmentAccess d = saved.get(key);
            Set<AccessModule> mods = d != null ? AccessService.parse(d.getModules()) : AccessService.suggested(name);
            Set<String> keys = new HashSet<>();
            mods.forEach(m -> keys.add(m.name()));
            long[] c = counts.getOrDefault(key, new long[2]);
            rows.add(new Row(name, keys, d != null, c[0], c[1], counts.containsKey(key)));
        });

        model.addAttribute("activePage", "department-access");
        model.addAttribute("rows", rows);
        model.addAttribute("modules", AccessModule.values());
        model.addAttribute("unsaved", rows.stream().filter(r -> !r.saved()).count());
        return "department-access";
    }

    @PostMapping("/Admin/DepartmentAccess")
    @Transactional
    public String save(HttpServletRequest request, RedirectAttributes flash) {
        int count = 0;
        for (int i = 0; i < 500; i++) {
            String name = request.getParameter("dept_" + i);
            if (name == null) break;
            String key = DepartmentAccess.keyOf(name);
            if (key.isEmpty()) continue;
            Set<AccessModule> mods = EnumSet.noneOf(AccessModule.class);
            String[] values = request.getParameterValues("mods_" + i);
            if (values != null) for (String v : values) { AccessModule m = AccessModule.parse(v); if (m != null) mods.add(m); }
            DepartmentAccess d = departments.findByDepartmentKey(key).orElseGet(DepartmentAccess::new);
            d.setDepartmentKey(key);
            d.setDepartmentName(name.trim());
            d.setModules(AccessService.join(mods));
            departments.save(d);
            count++;
        }
        accessService.changed();
        flash.addFlashAttribute("success", "Saved access for " + count + (count == 1 ? " department" : " departments")
                + ". People who follow their department get the new access on their next page load.");
        return "redirect:/Admin/DepartmentAccess";
    }
}
