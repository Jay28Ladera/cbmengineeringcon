package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.repository.DepartmentAccessRepository;
import com.asmsi.admin_system.repository.OrganizationNodeRepository;
import com.asmsi.admin_system.repository.UserAccessRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Works out which modules a person can open.
 *
 *  1. Admins (role admin / ceo): everything.
 *  2. Custom access set by an admin on the Users page: exactly those modules.
 *  3. Otherwise, the department of their box on the Organizational Chart, using the table on
 *     Administration → Department Access (or suggested defaults if that department hasn't been saved yet).
 *  4. Accounts not linked to anyone on the chart keep full staff access, so existing accounts
 *     aren't locked out; the Users page flags them so an admin can link them or set access.
 *
 * The result is cached in the session and recalculated after any access change.
 */
@Service
public class AccessService {

    public static final Set<AccessModule> ALL = Collections.unmodifiableSet(EnumSet.allOf(AccessModule.class));
    private static final String SESSION_KEY = "cbm.access";

    /** Bumped on every access change so cached session values are recalculated. */
    private final AtomicLong version = new AtomicLong(1);

    private final UserAccessRepository userAccess;
    private final DepartmentAccessRepository departmentAccess;
    private final OrganizationNodeRepository nodes;

    public AccessService(UserAccessRepository userAccess, DepartmentAccessRepository departmentAccess,
                         OrganizationNodeRepository nodes) {
        this.userAccess = userAccess;
        this.departmentAccess = departmentAccess;
        this.nodes = nodes;
    }

    /** What a person can open, and why (shown on the Users page). */
    public record Access(boolean admin, Set<AccessModule> modules, String source, String department) implements java.io.Serializable {
        public boolean can(AccessModule m) { return admin || modules.contains(m); }
        public Set<String> keys() { return modules.stream().map(Enum::name).collect(Collectors.toSet()); }
        public String summary() {
            if (admin) return "Full access (admin)";
            if (modules.isEmpty()) return "Dashboard and org chart only";
            if (modules.size() == ALL.size()) return "All modules";
            return modules.stream().map(AccessModule::getLabel).collect(Collectors.joining(", "));
        }
    }

    private record Cached(long version, Long userId, Access access) implements java.io.Serializable {}

    public void changed() { version.incrementAndGet(); }

    public long version() { return version.get(); }

    /** Access for the signed-in user, cached in the session. */
    public Access current(User user, HttpSession session) {
        if (user == null) return new Access(false, Set.of(), "Not signed in", null);
        if (session != null) {
            Object c = session.getAttribute(SESSION_KEY);
            if (c instanceof Cached cached && cached.version == version.get() && Objects.equals(cached.userId, user.getId())) {
                return cached.access;
            }
        }
        Access access = forUser(user);
        if (session != null) session.setAttribute(SESSION_KEY, new Cached(version.get(), user.getId(), access));
        return access;
    }

    public Access forUser(User user) {
        if (isAdminRole(user.getRole())) return new Access(true, ALL, "Admin", departmentOf(user.getId()));
        UserAccess ua = userAccess.findById(user.getId()).orElse(null);
        String dept = departmentOf(user.getId());
        if (ua != null && !ua.isFollowDepartment()) {
            return new Access(false, parse(ua.getModules()), "Custom", dept);
        }
        if (dept != null) {
            return new Access(false, departmentModules(dept), "Department", dept);
        }
        if (isLinked(user.getId())) {
            // On the chart but no department written on their box.
            return new Access(false, Set.of(), "No department", null);
        }
        return new Access(false, ALL, "Not on org chart", null);
    }

    /** Department written on the person's (non-resigned) box on the org chart, if any. */
    public String departmentOf(Long userId) {
        if (userId == null) return null;
        return nodes.findByAccountUserId(userId).stream()
                .filter(n -> !n.isResigned())
                .map(OrganizationNode::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .findFirst().orElse(null);
    }

    public boolean isLinked(Long userId) {
        return userId != null && nodes.findByAccountUserId(userId).stream().anyMatch(n -> !n.isResigned());
    }

    /** Saved modules for a department, or suggested defaults if it hasn't been saved yet. */
    public Set<AccessModule> departmentModules(String department) {
        return departmentAccess.findByDepartmentKey(DepartmentAccess.keyOf(department))
                .map(d -> parse(d.getModules()))
                .orElseGet(() -> suggested(department));
    }

    public boolean isSaved(String department) {
        return departmentAccess.findByDepartmentKey(DepartmentAccess.keyOf(department)).isPresent();
    }

    /** Starting suggestion for a department, based on words in its name. Admins can change it. */
    public static Set<AccessModule> suggested(String department) {
        String d = DepartmentAccess.keyOf(department);
        EnumSet<AccessModule> s = EnumSet.noneOf(AccessModule.class);
        if (d.isEmpty()) return s;
        if (has(d, "management", "executive", "general manager", "owner", "president")) return EnumSet.allOf(AccessModule.class);
        if (has(d, "engineer", "project", "operation", "implement", "technical", "site", "construction", "hvac", "electrical", "mechanical", "safety", "quality", "qa")) {
            s.add(AccessModule.INQUIRY); s.add(AccessModule.IMPLEMENTATION);
        }
        if (has(d, "sales", "marketing", "business", "estimat")) s.add(AccessModule.INQUIRY);
        if (has(d, "purchas", "procure", "supply")) { s.add(AccessModule.PURCHASING); s.add(AccessModule.INVENTORY); }
        if (has(d, "warehouse", "inventory", "logistic", "stock")) s.add(AccessModule.INVENTORY);
        if (has(d, "financ", "account", "billing", "treasur")) { s.add(AccessModule.ACCOUNTING); s.add(AccessModule.REPORTS); }
        if (has(d, "admin")) { s.add(AccessModule.PURCHASING); s.add(AccessModule.ACCOUNTING); }
        if (has(d, "human", "hr", "personnel", "payroll")) s.add(AccessModule.HR);
        return s;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (w.length() <= 3 ? Arrays.asList(text.split("[^a-z]+")).contains(w) : text.contains(w)) return true;
        }
        return false;
    }

    public static Set<AccessModule> parse(String csv) {
        EnumSet<AccessModule> set = EnumSet.noneOf(AccessModule.class);
        if (csv == null) return set;
        for (String part : csv.split(",")) {
            AccessModule m = AccessModule.parse(part);
            if (m != null) set.add(m);
        }
        return set;
    }

    public static String join(Collection<AccessModule> modules) {
        return modules.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    public static boolean isAdminRole(String role) {
        if (role == null) return false;
        String r = role.replace("ROLE_", "");
        return r.equalsIgnoreCase("admin") || r.equalsIgnoreCase("ceo");
    }
}
