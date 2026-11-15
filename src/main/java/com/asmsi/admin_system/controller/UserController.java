package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.AccessModule;
import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.entity.AccessModule;
import com.asmsi.admin_system.entity.OrganizationNode;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.entity.UserAccess;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.StaffActivationRepository;
import com.asmsi.admin_system.repository.UserAccessRepository;
import com.asmsi.admin_system.entity.UserAccess;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.StaffActivationRepository;
import com.asmsi.admin_system.repository.UserAccessRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.AuthUtil;
import com.asmsi.admin_system.service.StaffAccountService;
import com.asmsi.admin_system.service.StaffAccountService;
import com.asmsi.admin_system.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.*;

/** User accounts, approval and access. Admin-only (see SecurityConfig). */
/** User accounts, approval and access. Admin-only (see SecurityConfig). */
@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.asmsi.admin_system.repository.QuoteApproverRepository quoteApprovers;
    @org.springframework.beans.factory.annotation.Autowired
    private com.asmsi.admin_system.inventory.CanvassSignerRepository canvassSigners;
    @org.springframework.beans.factory.annotation.Autowired
    private com.asmsi.admin_system.accounting.PaymentApproverRepository paymentApprovers;
    private final AuthUtil authUtil;
    private final AccessService accessService;
    private final UserAccessRepository userAccess;
    private final OrganizationNodeRepository nodes;
    private final StaffActivationRepository activations;
    private final StaffAccountService staffAccounts;
    private final ObjectMapper json;

    @Value("${app.base-url:}")
    private String baseUrl;

    public UserController(UserService userService, UserRepository userRepository, AuthUtil authUtil,
                          AccessService accessService, UserAccessRepository userAccess,
                          OrganizationNodeRepository nodes, StaffActivationRepository activations,
                          StaffAccountService staffAccounts, ObjectMapper json) {
    private final AccessService accessService;
    private final UserAccessRepository userAccess;
    private final OrganizationNodeRepository nodes;
    private final StaffActivationRepository activations;
    private final StaffAccountService staffAccounts;
    private final ObjectMapper json;

    @Value("${app.base-url:}")
    private String baseUrl;

    public UserController(UserService userService, UserRepository userRepository, AuthUtil authUtil,
                          AccessService accessService, UserAccessRepository userAccess,
                          OrganizationNodeRepository nodes, StaffActivationRepository activations,
                          StaffAccountService staffAccounts, ObjectMapper json) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authUtil = authUtil;
        this.accessService = accessService;
        this.userAccess = userAccess;
        this.nodes = nodes;
        this.activations = activations;
        this.staffAccounts = staffAccounts;
        this.json = json;
    }

    /** One row in the Active users table. */
    public record UserRow(User user, String department, String position, AccessService.Access access,
                          boolean followDepartment, boolean linked) {}

    /** Someone on the org chart, with or without an account. */
    public record StaffRow(OrganizationNode node, String state, String username) {}
        this.accessService = accessService;
        this.userAccess = userAccess;
        this.nodes = nodes;
        this.activations = activations;
        this.staffAccounts = staffAccounts;
        this.json = json;
    }

    /** One row in the Active users table. */
    public record UserRow(User user, String department, String position, AccessService.Access access,
                          boolean followDepartment, boolean linked) {}

    /** Someone on the org chart, with or without an account. */
    public record StaffRow(OrganizationNode node, String state, String username) {}

    @GetMapping
    public String viewUsers(Model model) throws JsonProcessingException {
        Long me = authUtil.getCurrentUserId();
        Map<Long, OrganizationNode> nodeByUser = new HashMap<>();
        List<OrganizationNode> allNodes = nodes.findAllByOrderBySortOrderAscIdAsc();
        for (OrganizationNode n : allNodes)
            if (n.getAccountUserId() != null && !n.isResigned()) nodeByUser.putIfAbsent(n.getAccountUserId(), n);

        Map<Long, UserAccess> accessByUser = new HashMap<>();
        userAccess.findAll().forEach(a -> accessByUser.put(a.getUserId(), a));

        List<UserRow> rows = new ArrayList<>();
        Map<String, Object> editData = new LinkedHashMap<>();
        java.util.Set<Long> approverIds = new java.util.HashSet<>();
        quoteApprovers.findAll().forEach(q -> approverIds.add(q.userId));
        model.addAttribute("approverIds", approverIds);
        java.util.Set<Long> payApproverIds = new java.util.HashSet<>();
        paymentApprovers.findAll().forEach(p -> payApproverIds.add(p.userId));
        model.addAttribute("payApproverIds", payApproverIds);
        java.util.Map<Long, java.util.Set<String>> canvassSteps = new java.util.HashMap<>();
        canvassSigners.findAll().forEach(cs -> canvassSteps.computeIfAbsent(cs.userId, k -> new java.util.HashSet<>()).add(cs.step));
        model.addAttribute("canvassSteps", canvassSteps);
        for (User u : userService.getApprovedUsers()) {
            OrganizationNode n = nodeByUser.get(u.getId());
            UserAccess ua = accessByUser.get(u.getId());
            AccessService.Access a = accessService.forUser(u);
            boolean follow = ua == null || ua.isFollowDepartment();
            rows.add(new UserRow(u, n == null ? null : n.getDepartment(), n == null ? null : n.getTitle(), a, follow, n != null));

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", u.getId());
            d.put("name", u.getName());
            d.put("email", u.getEmail());
            d.put("username", u.getUsername());
            d.put("admin", a.admin());
            d.put("quoteApprover", approverIds.contains(u.getId()));
            var cs = canvassSteps.getOrDefault(u.getId(), java.util.Set.of());
            d.put("canvassApprove", cs.contains("APPROVE"));
            d.put("canvassNote", cs.contains("NOTE"));
            d.put("canvassProcess", cs.contains("PROCESS"));
            d.put("paymentApprover", payApproverIds.contains(u.getId()));
            d.put("self", u.getId().equals(me));
            d.put("linked", n != null);
            d.put("position", n == null ? null : n.getTitle());
            d.put("department", n == null ? null : n.getDepartment());
            d.put("departmentModules", n == null || n.getDepartment() == null ? List.of()
                    : accessService.departmentModules(n.getDepartment()).stream().map(Enum::name).toList());
            d.put("followDepartment", follow);
            d.put("modules", ua == null ? List.of() : AccessService.parse(ua.getModules()).stream().map(Enum::name).toList());
            editData.put(String.valueOf(u.getId()), d);
        }

        // Sign-up requests (not people invited from the org chart, who appear on the Staff tab).
        List<User> requests = userService.getUnapprovedUsers().stream()
                .filter(u -> { UserAccess ua = accessByUser.get(u.getId()); return ua == null || !ua.isInvitePending(); })
                .toList();

        Map<Long, User> usersById = new HashMap<>();
        userRepository.findAll().forEach(u -> usersById.put(u.getId(), u));
        List<StaffRow> staff = new ArrayList<>();
        for (OrganizationNode n : allNodes) {
            if (n.isResigned() || n.getPersonName() == null || n.getPersonName().isBlank()) continue;
            User acc = n.getAccountUserId() == null ? null : usersById.get(n.getAccountUserId());
            StaffAccountService.Status st = staffAccounts.statusOf(n, acc);
            if ("active".equals(st.state())) continue;
            staff.add(new StaffRow(n, st.state(), st.username()));
        }

        model.addAttribute("activePage", "users");
        model.addAttribute("currentUserId", me);
        model.addAttribute("rows", rows);
        model.addAttribute("unapprovedUsers", requests);
        // Accounts that were active before and got disabled (e.g. marked resigned on the org chart).
        Set<Long> disabledIds = new HashSet<>();
        for (User u : requests) if (u.getDateApproved() != null || !nodes.findByAccountUserId(u.getId()).isEmpty()) disabledIds.add(u.getId());
        model.addAttribute("disabledIds", disabledIds);
        model.addAttribute("staff", staff);
        model.addAttribute("staffWithoutAccount", staff.stream().filter(s -> "none".equals(s.state())).count());
        model.addAttribute("modules", AccessModule.values());
        model.addAttribute("usersJson", json.writeValueAsString(editData).replace("<", "\\u003c"));
        model.addAttribute("currentUserId", me);
        model.addAttribute("rows", rows);
        model.addAttribute("unapprovedUsers", requests);
        // Accounts that were active before and got disabled (e.g. marked resigned on the org chart).
        Set<Long> disabledIds = new HashSet<>();
        for (User u : requests) if (u.getDateApproved() != null || !nodes.findByAccountUserId(u.getId()).isEmpty()) disabledIds.add(u.getId());
        model.addAttribute("disabledIds", disabledIds);
        model.addAttribute("staff", staff);
        model.addAttribute("staffWithoutAccount", staff.stream().filter(s -> "none".equals(s.state())).count());
        model.addAttribute("modules", AccessModule.values());
        model.addAttribute("usersJson", json.writeValueAsString(editData).replace("<", "\\u003c"));
        return "user-management";
    }

    @PostMapping("/approve")
    public String approveUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        userService.approveUser(id, authUtil.getCurrentUserId());
        accessService.changed();
        accessService.changed();
        redirect.addFlashAttribute("success", "User approved. They can now sign in.");
        return "redirect:/users";
    }

    @PostMapping("/reject")
    @Transactional
    @Transactional
    public String rejectUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        cleanUp(id);
        cleanUp(id);
        userService.deleteUser(id);
        redirect.addFlashAttribute("success", "Account request rejected.");
        return "redirect:/users#pending";
    }

    @PostMapping("/delete")
    @Transactional
    @Transactional
    public String deleteUser(@RequestParam("id") Long id, RedirectAttributes redirect) {
        if (id.equals(authUtil.getCurrentUserId())) {
            redirect.addFlashAttribute("error", "You cannot delete your own account while signed in.");
            return "redirect:/users";
        }
        User target = userRepository.findById(id).orElse(null);
        if (target != null && AccessService.isAdminRole(target.getRole()) && adminCount() <= 1) {
            redirect.addFlashAttribute("error", "You can't delete the last administrator.");
            return "redirect:/users";
        }
        cleanUp(id);
        User target = userRepository.findById(id).orElse(null);
        if (target != null && AccessService.isAdminRole(target.getRole()) && adminCount() <= 1) {
            redirect.addFlashAttribute("error", "You can't delete the last administrator.");
            return "redirect:/users";
        }
        cleanUp(id);
        userService.deleteUser(id);
        redirect.addFlashAttribute("success", "User deleted. Their box on the org chart stays, without a login account.");
        redirect.addFlashAttribute("success", "User deleted. Their box on the org chart stays, without a login account.");
        return "redirect:/users";
    }

    /** Unlink from the org chart and remove access settings and activation links. */
    private void cleanUp(Long userId) {
        for (OrganizationNode n : nodes.findByAccountUserId(userId)) { n.setAccountUserId(null); nodes.save(n); }
        userAccess.findById(userId).ifPresent(userAccess::delete);
        activations.deleteByUserId(userId);
        accessService.changed();
    }

    /** Unlink from the org chart and remove access settings and activation links. */
    private void cleanUp(Long userId) {
        for (OrganizationNode n : nodes.findByAccountUserId(userId)) { n.setAccountUserId(null); nodes.save(n); }
        userAccess.findById(userId).ifPresent(userAccess::delete);
        activations.deleteByUserId(userId);
        accessService.changed();
    }

    @PostMapping("/edit")
    @Transactional
    @Transactional
    public String updateUserFromModal(
            @RequestParam("id") Long id,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("username") String username,
            @RequestParam(defaultValue = "false") boolean admin,
            @RequestParam(defaultValue = "false") boolean followDepartment,
            @RequestParam(defaultValue = "false") boolean quoteApprover,
            @RequestParam(defaultValue = "false") boolean canvassApprove,
            @RequestParam(defaultValue = "false") boolean canvassNote,
            @RequestParam(defaultValue = "false") boolean canvassProcess,
            @RequestParam(defaultValue = "false") boolean paymentApprover,
            @RequestParam(required = false, name = "modules") List<String> modules,
            RedirectAttributes redirect) {

        User existing = userService.getUserById(id);
        if (!username.trim().equals(existing.getUsername()) && userRepository.existsByUsername(username.trim())) {
        if (!username.trim().equals(existing.getUsername()) && userRepository.existsByUsername(username.trim())) {
            redirect.addFlashAttribute("error", "That username is already taken.");
            return "redirect:/users";
        }
        if (!email.trim().equalsIgnoreCase(existing.getEmail()) && userRepository.existsByEmail(email.trim())) {
        if (!email.trim().equalsIgnoreCase(existing.getEmail()) && userRepository.existsByEmail(email.trim())) {
            redirect.addFlashAttribute("error", "That email address is already in use.");
            return "redirect:/users";
        }
        boolean wasAdmin = AccessService.isAdminRole(existing.getRole());
        if (wasAdmin && !admin) {
            if (id.equals(authUtil.getCurrentUserId())) {
                redirect.addFlashAttribute("error", "You can't remove your own admin rights. Ask another admin to do it.");
                return "redirect:/users";
            }
            if (adminCount() <= 1) {
                redirect.addFlashAttribute("error", "There must be at least one administrator.");
                return "redirect:/users";
            }
        }

        String role = admin ? "admin" : (wasAdmin ? "user" : existing.getRole());
        boolean wasAdmin = AccessService.isAdminRole(existing.getRole());
        if (wasAdmin && !admin) {
            if (id.equals(authUtil.getCurrentUserId())) {
                redirect.addFlashAttribute("error", "You can't remove your own admin rights. Ask another admin to do it.");
                return "redirect:/users";
            }
            if (adminCount() <= 1) {
                redirect.addFlashAttribute("error", "There must be at least one administrator.");
                return "redirect:/users";
            }
        }

        String role = admin ? "admin" : (wasAdmin ? "user" : existing.getRole());
        User updatedUser = new User();
        updatedUser.setName(name.trim());
        updatedUser.setEmail(email.trim());
        updatedUser.setUsername(username.trim());
        updatedUser.setRole(role == null || role.isBlank() ? "user" : role);
        updatedUser.setRole(role == null || role.isBlank() ? "user" : role);
        userService.updateUser(id, updatedUser);
        if (quoteApprover) { if (!quoteApprovers.existsById(id)) quoteApprovers.save(new com.asmsi.admin_system.entity.QuoteApprover(id)); }
        else if (quoteApprovers.existsById(id)) quoteApprovers.deleteById(id);
        setCanvassStep(id, "APPROVE", canvassApprove);
        setCanvassStep(id, "NOTE", canvassNote);
        setCanvassStep(id, "PROCESS", canvassProcess);
        if (paymentApprover) { if (!paymentApprovers.existsById(id)) paymentApprovers.save(new com.asmsi.admin_system.accounting.PaymentApprover(id)); }
        else if (paymentApprovers.existsById(id)) paymentApprovers.deleteById(id);

        UserAccess ua = userAccess.findById(id).orElse(new UserAccess(id));
        ua.setFollowDepartment(followDepartment);
        Set<AccessModule> chosen = EnumSet.noneOf(AccessModule.class);
        if (modules != null) for (String m : modules) { AccessModule parsed = AccessModule.parse(m); if (parsed != null) chosen.add(parsed); }
        if (!followDepartment) ua.setModules(AccessService.join(chosen));
        userAccess.save(ua);
        accessService.changed();

        redirect.addFlashAttribute("success", name.trim() + " saved." +
                (admin != wasAdmin
                        ? (admin ? " They are now an administrator." : " They are no longer an administrator.")
                          + " This applies on their next page load."
                        : (id.equals(authUtil.getCurrentUserId()) ? "" : " Access changes apply on their next page load.")));
        return "redirect:/users";
    }

    /** Create an account for someone on the org chart (Staff tab). */
    @PostMapping("/invite/{nodeId}")
    public String invite(@PathVariable Long nodeId, RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("invite", staffAccounts.invite(nodeId, authUtil.getCurrentUserId(), appUrl()));
        } catch (StaffAccountService.InviteException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users#staff";
    }

    private long adminCount() {
        return userRepository.findAll().stream()
                .filter(u -> u.isApproved() && AccessService.isAdminRole(u.getRole())).count();
    }

    private String appUrl() {
        return baseUrl != null && !baseUrl.isBlank() ? baseUrl
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private void setCanvassStep(Long userId, String step, boolean on) {
        boolean has = canvassSigners.existsByUserIdAndStep(userId, step);
        if (on && !has) canvassSigners.save(new com.asmsi.admin_system.inventory.CanvassSigner(userId, step));
        if (!on && has) canvassSigners.findByUserId(userId).stream().filter(x -> step.equals(x.step)).forEach(canvassSigners::delete);
    }
}
