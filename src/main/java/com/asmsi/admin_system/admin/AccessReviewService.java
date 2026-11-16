package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.accounting.PaymentApprover;
import com.asmsi.admin_system.accounting.PaymentApproverRepository;
import com.asmsi.admin_system.entity.AccessModule;
import com.asmsi.admin_system.entity.QuoteApprover;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.inventory.CanvassSigner;
import com.asmsi.admin_system.inventory.CanvassSignerRepository;
import com.asmsi.admin_system.repository.QuoteApproverRepository;
import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Who can open what and who approves what, with the things worth a second look before going live. */
@Service
public class AccessReviewService {

    public record Person(User user, AccessService.Access access, List<String> roles, LocalDateTime lastLogin, long failed7d, List<String> flags) {
        public boolean can(AccessModule m) { return access.can(m); }
        public boolean isAdmin() { return access.admin(); }
    }

    public record Issue(String tone, String text, String href) {}

    public record Review(List<Person> people, List<User> pending, List<Issue> issues, Map<String, Long> counts) {}

    private final UserRepository users;
    private final AccessService access;
    private final QuoteApproverRepository quoteApprovers;
    private final PaymentApproverRepository paymentApprovers;
    private final CanvassSignerRepository canvassSigners;
    private final JdbcTemplate jdbc;

    public AccessReviewService(UserRepository users, AccessService access, QuoteApproverRepository quoteApprovers,
                               PaymentApproverRepository paymentApprovers, CanvassSignerRepository canvassSigners, JdbcTemplate jdbc) {
        this.users = users; this.access = access; this.quoteApprovers = quoteApprovers; this.paymentApprovers = paymentApprovers;
        this.canvassSigners = canvassSigners; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Review review() {
        Set<Long> quote = new HashSet<>(), pay = new HashSet<>();
        for (QuoteApprover q : quoteApprovers.findAll()) quote.add(q.userId);
        for (PaymentApprover p : paymentApprovers.findAll()) pay.add(p.userId);
        Map<Long, List<String>> steps = new HashMap<>();
        for (CanvassSigner s : canvassSigners.findAll()) steps.computeIfAbsent(s.userId, k -> new ArrayList<>()).add(s.step);
        Map<String, LocalDateTime> last = new HashMap<>();
        Map<String, Long> failed = new HashMap<>();
        jdbc.query("select username, max(at) from sys_audit where action = 'LOGIN' and username is not null group by 1", rs -> { last.put(rs.getString(1).toLowerCase(), rs.getTimestamp(2).toLocalDateTime()); });
        jdbc.query("select username, count(*) from sys_audit where action = 'LOGIN_FAILED' and at >= now() - interval '7 days' and username is not null group by 1",
                rs -> { failed.put(rs.getString(1).toLowerCase(), rs.getLong(2)); });
        boolean trailNew = Optional.ofNullable(jdbc.queryForObject("select min(at) from sys_audit", java.sql.Timestamp.class))
                .map(t -> t.toLocalDateTime().isAfter(LocalDateTime.now().minusDays(30))).orElse(true);

        List<Person> people = new ArrayList<>();
        int admins = 0;
        for (User u : users.findByIsApprovedTrueOrderByNameAsc()) {
            AccessService.Access a = access.forUser(u);
            List<String> roles = new ArrayList<>();
            if (quote.contains(u.getId())) roles.add("Quotations");
            if (pay.contains(u.getId())) roles.add("Payment vouchers & client billings");
            for (String st : steps.getOrDefault(u.getId(), List.of()))
                roles.add("Canvass: " + switch (st) { case "APPROVE" -> "approve"; case "NOTE" -> "noted by"; case "PROCESS" -> "process (Finance)"; default -> st.toLowerCase(); });
            List<String> flags = new ArrayList<>();
            if (a.admin()) admins++;
            if (pay.contains(u.getId()) && !a.can(AccessModule.ACCOUNTING)) flags.add("Approves payments but can't open Accounting");
            if (quote.contains(u.getId()) && !a.can(AccessModule.INQUIRY)) flags.add("Approves quotations but can't open Inquiry");
            if (steps.containsKey(u.getId()) && !a.can(AccessModule.PURCHASING)) flags.add("Signs canvasses but can't open Purchasing");
            if (!a.admin() && a.modules().isEmpty()) flags.add("Can't open any module");
            LocalDateTime ll = last.get(u.getUsername().toLowerCase());
            if (!trailNew && (ll == null || ll.isBefore(LocalDateTime.now().minusDays(90)))) flags.add(ll == null ? "Hasn't signed in since the trail started" : "No sign-in for 90+ days");
            long f = failed.getOrDefault(u.getUsername().toLowerCase(), 0L);
            if (f >= 5) flags.add(f + " failed sign-ins this week");
            people.add(new Person(u, a, roles, ll, f, flags));
        }
        List<User> pending = users.findByIsApprovedFalse();

        List<Issue> issues = new ArrayList<>();
        if (admins > 3) issues.add(new Issue("warn", admins + " people have admin rights (full access to everything, including pay and backups). Keep it to the few who need it.", "/users"));
        if (admins == 1) issues.add(new Issue("info", "Only one admin. If that account is locked out, nobody can manage users; consider a second.", "/users"));
        if (pay.isEmpty()) issues.add(new Issue("warn", "Nobody is set to approve payment vouchers and client billings, so any admin can. Tick the approvers in Users.", "/users"));
        if (quote.isEmpty()) issues.add(new Issue("warn", "Nobody is set to approve quotations, so any admin can. Set them in Users.", "/users"));
        for (String st : List.of("APPROVE", "NOTE", "PROCESS"))
            if (steps.values().stream().noneMatch(l -> l.contains(st)))
                issues.add(new Issue("info", "No canvass signer for the \"" + switch (st) { case "APPROVE" -> "approve"; case "NOTE" -> "noted by"; default -> "process (Finance)"; } + "\" step.", "/users"));
        if (!pending.isEmpty()) issues.add(new Issue("info", pending.size() + " sign-up(s) waiting for approval.", "/users"));
        long flagged = people.stream().filter(p -> !p.flags().isEmpty()).count();
        if (flagged > 0) issues.add(new Issue("warn", flagged + " account(s) flagged below.", null));
        Long fails = jdbc.queryForObject("select count(*) from sys_audit where action = 'LOGIN_FAILED' and at >= now() - interval '7 days'", Long.class);
        if (fails != null && fails >= 10) issues.add(new Issue("warn", fails + " failed sign-ins in the last 7 days.", "/Admin/Trails?action=LOGIN_FAILED"));
        if (issues.isEmpty()) issues.add(new Issue("ok", "Nothing stands out.", null));

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", (long) people.size());
        counts.put("admins", (long) admins);
        counts.put("approvers", people.stream().filter(p -> !p.roles().isEmpty()).count());
        counts.put("pending", (long) pending.size());
        return new Review(people, pending, issues, counts);
    }

    public String csv(Review r) {
        StringBuilder sb = new StringBuilder("Name,Username,Email,Role,Department,Access from");
        for (AccessModule m : AccessModule.values()) sb.append(',').append(m.getLabel());
        sb.append(",Approves,Last sign-in,Flags\n");
        for (Person p : r.people()) {
            sb.append(c(p.user().getName())).append(',').append(c(p.user().getUsername())).append(',').append(c(p.user().getEmail())).append(',')
              .append(c(p.user().getRole())).append(',').append(c(p.access().department())).append(',').append(c(p.access().source()));
            for (AccessModule m : AccessModule.values()) sb.append(',').append(p.can(m) ? "Yes" : "");
            sb.append(',').append(c(String.join("; ", p.roles()))).append(',').append(p.lastLogin() == null ? "" : p.lastLogin().withNano(0).toString().replace('T', ' '))
              .append(',').append(c(String.join("; ", p.flags()))).append('\n');
        }
        return sb.toString();
    }

    private static String c(String v) { if (v == null) return ""; return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v; }
}
