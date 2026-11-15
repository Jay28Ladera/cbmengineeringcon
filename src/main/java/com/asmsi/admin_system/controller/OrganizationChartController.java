package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.entity.OrganizationReporting;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.OrganizationReportingRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.PhotoStorageService;
import com.asmsi.admin_system.service.StaffAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Organizational chart (Human Resources → Organizational Chart).
 *
 * Everyone signed in can view published positions. Admins can add, edit, publish, delete and
 * arrange positions; the arrangement they drag is saved for everyone.
 */
@Controller
@RequestMapping("/organization-chart")
public class OrganizationChartController {

    private static final String BACK = "redirect:/organization-chart";

    private final OrganizationNodeRepository nodes;
    private final OrganizationReportingRepository reporting;
    private final UserRepository users;
    private final PhotoStorageService photos;
    private final ObjectMapper json;
    private final StaffAccountService staffAccounts;
    private final AccessService accessService;

    @Value("${app.base-url:}")
    private String baseUrl;

    public OrganizationChartController(OrganizationNodeRepository nodes, OrganizationReportingRepository reporting,
                                       UserRepository users, PhotoStorageService photos, ObjectMapper json,
                                       StaffAccountService staffAccounts, AccessService accessService) {
        this.nodes = nodes;
        this.reporting = reporting;
        this.users = users;
        this.photos = photos;
        this.json = json;
        this.staffAccounts = staffAccounts;
        this.accessService = accessService;
    }

    // ------------------------------------------------------------------ view

    @GetMapping
    public String view(Model model) throws JsonProcessingException {
        boolean admin = isAdmin();
        List<OrganizationNode> all = nodes.findAllByOrderBySortOrderAscIdAsc();
        List<OrganizationNode> visible = all.stream()
                .filter(n -> !n.isResigned())
                .filter(n -> admin || n.isPublished())
                .toList();
        Set<Long> visibleIds = new HashSet<>();
        visible.forEach(n -> visibleIds.add(n.getId()));

        Map<Long, User> accounts = new HashMap<>();
        users.findAll().forEach(u -> accounts.put(u.getId(), u));

        Map<Long, List<Long>> extraSupervisors = new HashMap<>();
        for (OrganizationReporting r : reporting.findAll()) {
            if (visibleIds.contains(r.getMemberId()) && visibleIds.contains(r.getSupervisorId())) {
                extraSupervisors.computeIfAbsent(r.getMemberId(), k -> new ArrayList<>()).add(r.getSupervisorId());
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (OrganizationNode n : visible) {
            // A supervisor hidden from this viewer (draft/resigned) makes the node a top-level box.
            Long parent = n.getParentId() != null && visibleIds.contains(n.getParentId()) ? n.getParentId() : null;
            data.add(toJson(n, parent, accounts.get(n.getAccountUserId()),
                    extraSupervisors.getOrDefault(n.getId(), List.of()), admin));
        }

        model.addAttribute("activePage", "organization-chart");
        model.addAttribute("adminMode", admin);
        model.addAttribute("chartJson", safeJson(data));
        model.addAttribute("positionCount", visible.size());
        model.addAttribute("draftCount", visible.stream().filter(n -> !n.isPublished()).count());

        if (admin) {
            List<Map<String, Object>> accountOptions = new ArrayList<>();
            users.findByIsApprovedTrueOrderByNameAsc().forEach(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("label", (u.getName() == null ? u.getUsername() : u.getName()) + " (@" + u.getUsername() + ")");
                accountOptions.add(m);
            });
            model.addAttribute("accountsJson", safeJson(accountOptions));
            model.addAttribute("resigned", all.stream().filter(OrganizationNode::isResigned).toList());
        } else {
            model.addAttribute("accountsJson", "[]");
            model.addAttribute("resigned", List.of());
        }
        return "organization-chart";
    }

    private Map<String, Object> toJson(OrganizationNode n, Long parent, User account, List<Long> also, boolean admin) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d, yyyy");
        String photo = notBlank(n.getPhotoUrl()) ? n.getPhotoUrl()
                : account != null && notBlank(account.getProfileImageUrl()) ? account.getProfileImageUrl() : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("parentId", parent);
        m.put("also", also);
        m.put("title", n.getTitle());
        m.put("name", n.getPersonName());
        m.put("department", n.getDepartment());
        m.put("site", n.getSiteAssignment());
        m.put("photo", photo);
        m.put("ownPhoto", notBlank(n.getPhotoUrl()));
        m.put("email", n.getEmailAddress());
        m.put("phone", n.getContactNumber());
        m.put("profession", n.getProfession());
        m.put("license", n.getLicenseNumber());
        m.put("licenseExpiry", n.getLicenseExpiry() == null ? null : n.getLicenseExpiry().toString());
        m.put("licenseExpiryLabel", n.getLicenseExpiry() == null ? null : n.getLicenseExpiry().format(day));
        m.put("licenseExpired", n.getLicenseExpiry() != null && n.getLicenseExpiry().isBefore(LocalDate.now()));
        m.put("dateHired", n.getDateHired() == null ? null : n.getDateHired().toString());
        m.put("dateHiredLabel", n.getDateHired() == null ? null : n.getDateHired().format(day));
        m.put("sortOrder", n.getSortOrder());
        m.put("published", n.isPublished());
        m.put("dx", n.getLayoutOffsetX() == null ? 0 : n.getLayoutOffsetX());
        m.put("dy", n.getLayoutOffsetY() == null ? 0 : n.getLayoutOffsetY());
        if (admin) {
            m.put("employeeId", n.getEmployeeId());
            m.put("accountUserId", account == null ? null : n.getAccountUserId());
            m.put("accountLabel", account == null ? null : account.getName());
            StaffAccountService.Status st = staffAccounts.statusOf(n, account);
            m.put("accountState", st.state());
            m.put("accountUsername", st.username());
            if (account != null && "active".equals(st.state())) {
                AccessService.Access a = accessService.forUser(account);
                m.put("accountAccess", a.summary());
                m.put("accountAdmin", a.admin());
            }
        }
        return m;
    }

    /** JSON that is safe inside a &lt;script&gt; element. */
    private String safeJson(Object value) throws JsonProcessingException {
        return json.writeValueAsString(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    // ------------------------------------------------------------------ edit

    @PostMapping("/save")
    @Transactional
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String title,
                       @RequestParam(required = false) String personName,
                       @RequestParam(required = false) String department,
                       @RequestParam(required = false) String siteAssignment,
                       @RequestParam(required = false) String emailAddress,
                       @RequestParam(required = false) String contactNumber,
                       @RequestParam(required = false) String employeeId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateHired,
                       @RequestParam(required = false) String profession,
                       @RequestParam(required = false) String licenseNumber,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate licenseExpiry,
                       @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false, name = "alsoReportsTo") List<Long> alsoReportsTo,
                       @RequestParam(required = false) Long accountUserId,
                       @RequestParam(defaultValue = "0") Integer sortOrder,
                       @RequestParam(defaultValue = "false") boolean published,
                       @RequestParam(defaultValue = "false") boolean resigned,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateResigned,
                       @RequestParam(defaultValue = "false") boolean removePhoto,
                       @RequestParam(required = false) MultipartFile photo,
                       RedirectAttributes flash) {
        requireAdmin();
        if (title == null || title.isBlank()) return fail(flash, "Position title is required.");

        OrganizationNode node = id == null ? new OrganizationNode()
                : nodes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Supervisor checks: must exist and must not create a loop.
        if (parentId != null) {
            if (parentId.equals(id)) return fail(flash, "A position cannot report to itself.");
            if (!nodes.existsById(parentId)) return fail(flash, "The selected supervisor no longer exists.");
            if (id != null && reaches(parentId, id, new HashSet<>()))
                return fail(flash, "That supervisor already reports to this position, which would create a loop.");
        }
        Set<Long> extra = new LinkedHashSet<>(alsoReportsTo == null ? List.of() : alsoReportsTo);
        extra.remove(null);
        extra.remove(parentId);
        if (id != null) extra.remove(id);
        for (Long sup : extra) {
            if (!nodes.existsById(sup)) return fail(flash, "One of the additional supervisors no longer exists.");
            if (id != null && reaches(sup, id, new HashSet<>()))
                return fail(flash, "An additional supervisor already reports to this position, which would create a loop.");
        }

        String empId = clean(employeeId);
        if (empId != null && (id == null ? nodes.existsByEmployeeIdIgnoreCase(empId)
                : nodes.existsByEmployeeIdIgnoreCaseAndIdNot(empId, id)))
            return fail(flash, "Employee ID " + empId + " is already used by another person.");

        if (accountUserId != null && !users.existsById(accountUserId))
            return fail(flash, "The selected login account no longer exists.");

        if (photo != null && !photo.isEmpty()) {
            try {
                node.setPhotoUrl(photos.store(photo, "org"));
            } catch (PhotoStorageService.PhotoException e) {
                return fail(flash, e.getMessage());
            }
        } else if (removePhoto) {
            node.setPhotoUrl(null);
        }

        node.setTitle(title.trim());
        node.setPersonName(clean(personName));
        node.setDepartment(clean(department));
        node.setSiteAssignment(clean(siteAssignment));
        node.setEmailAddress(clean(emailAddress));
        node.setContactNumber(clean(contactNumber));
        node.setEmployeeId(empId);
        node.setDateHired(dateHired);
        node.setProfession(clean(profession));
        node.setLicenseNumber(clean(licenseNumber));
        node.setLicenseExpiry(licenseExpiry);
        node.setAccountUserId(accountUserId);
        // Moving to a new supervisor resets the manual position so the box lands in its new branch.
        if (!Objects.equals(node.getParentId(), parentId)) {
            node.setLayoutOffsetX(null);
            node.setLayoutOffsetY(null);
        }
        node.setParentId(parentId);
        node.setSortOrder(sortOrder == null ? 0 : sortOrder);
        node.setPublished(published);
        node.setResigned(resigned);
        node.setDateResigned(resigned ? (dateResigned == null ? LocalDate.now() : dateResigned) : null);
        nodes.save(node);

        // Someone who left can no longer sign in (an admin can re-enable them from Users).
        boolean disabledLogin = false;
        if (resigned && node.getAccountUserId() != null) {
            User account = users.findById(node.getAccountUserId()).orElse(null);
            boolean isMe = account != null && auth() != null && account.getId().equals(auth().getId());
            boolean lastAdmin = account != null && AccessService.isAdminRole(account.getRole())
                    && users.findAll().stream().filter(x -> x.isApproved() && AccessService.isAdminRole(x.getRole())).count() <= 1;
            if (account != null && account.isApproved() && !isMe && !lastAdmin) {
                account.setApproved(false);
                users.save(account);
                disabledLogin = true;
            }
        }
        accessService.changed(); // department or link may have changed

        reporting.deleteByMemberId(node.getId());
        for (Long sup : extra) reporting.save(new OrganizationReporting(sup, node.getId()));

        String who = node.getPersonName() == null ? node.getTitle() : node.getPersonName();
        flash.addFlashAttribute("success", resigned
                ? who + " was marked as resigned and removed from the chart." + (disabledLogin ? " Their login account was disabled." : "")
                : (id == null ? "Added " : "Saved ") + who + (published ? "." : " as a draft (only admins can see it)."));
        return BACK;
    }

    @PostMapping("/delete/{id}")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        requireAdmin();
        OrganizationNode node = nodes.findById(id).orElse(null);
        if (node == null) return fail(flash, "That position no longer exists.");
        if (nodes.existsByParentId(id) || reporting.existsBySupervisorId(id))
            return fail(flash, "Move or delete the positions that report to " + node.getTitle() + " first.");
        reporting.deleteByMemberId(id);
        nodes.delete(node);
        accessService.changed();
        flash.addFlashAttribute("success", "Deleted " + node.getTitle() + ".");
        return BACK;
    }

    @PostMapping("/restore/{id}")
    public String restore(@PathVariable Long id, RedirectAttributes flash) {
        requireAdmin();
        OrganizationNode node = nodes.findById(id).orElse(null);
        if (node == null) return fail(flash, "That record no longer exists.");
        node.setResigned(false);
        node.setDateResigned(null);
        if (node.getParentId() != null && nodes.findById(node.getParentId()).map(OrganizationNode::isResigned).orElse(true)) {
            node.setParentId(null); // former supervisor is gone or resigned
        }
        nodes.save(node);
        flash.addFlashAttribute("success", "Restored " + (node.getPersonName() == null ? node.getTitle() : node.getPersonName()) + " to the chart.");
        return BACK;
    }

    // ------------------------------------------------------------------ login accounts

    /** Creates a login account for the person in this box (or re-sends their activation link). */
    @PostMapping("/{id}/invite")
    public String invite(@PathVariable Long id, RedirectAttributes flash) {
        requireAdmin();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long adminId = auth != null && auth.getPrincipal() instanceof User u ? u.getId() : null;
        try {
            StaffAccountService.InviteResult r = staffAccounts.invite(id, adminId, appUrl());
            flash.addFlashAttribute("invite", r);
        } catch (StaffAccountService.InviteException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }

    private String appUrl() {
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    // ------------------------------------------------------------------ layout

    public record Offset(Long id, Double dx, Double dy) {}

    /** Saves dragged positions (admins only). */
    @PostMapping("/layout")
    @ResponseBody
    @Transactional
    public Map<String, Object> saveLayout(@RequestBody List<Offset> offsets) {
        requireAdmin();
        int saved = 0;
        for (Offset o : offsets) {
            if (o.id() == null) continue;
            OrganizationNode node = nodes.findById(o.id()).orElse(null);
            if (node == null) continue;
            node.setLayoutOffsetX(round(o.dx()));
            node.setLayoutOffsetY(round(o.dy()));
            saved++;
        }
        return Map.of("saved", saved);
    }

    @PostMapping("/layout/reset")
    @ResponseBody
    @Transactional
    public Map<String, Object> resetLayout() {
        requireAdmin();
        List<OrganizationNode> all = nodes.findAll();
        all.forEach(n -> { n.setLayoutOffsetX(null); n.setLayoutOffsetY(null); });
        return Map.of("reset", all.size());
    }

    // ------------------------------------------------------------------ helpers

    private boolean reaches(Long from, Long target, Set<Long> visited) {
        if (from.equals(target)) return true;
        if (!visited.add(from)) return false;
        OrganizationNode n = nodes.findById(from).orElse(null);
        if (n != null && n.getParentId() != null && reaches(n.getParentId(), target, visited)) return true;
        for (OrganizationReporting r : reporting.findByMemberId(from))
            if (reaches(r.getSupervisorId(), target, visited)) return true;
        return false;
    }

    private static Double round(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return null;
        double clamped = Math.max(-20000, Math.min(20000, v));
        return Math.abs(clamped) < 0.5 ? null : Math.round(clamped * 10) / 10.0;
    }

    private static User auth() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof User u ? u : null;
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User u) || u.getRole() == null) return false;
        String role = u.getRole().replace("ROLE_", "");
        return role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("ceo");
    }

    private static void requireAdmin() {
        if (!isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can change the organizational chart.");
    }

    private static String fail(RedirectAttributes flash, String message) {
        flash.addFlashAttribute("error", message);
        return BACK;
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
