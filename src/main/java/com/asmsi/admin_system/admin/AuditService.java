package com.asmsi.admin_system.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Reads the audit trail for Admin › Audit Trail. */
@Service
public class AuditService {

    public static final int PAGE = 100;

    public record Change(String field, String before, String after) {
        public String getLabel() { return humanize(field); }
    }

    public record Line(long id, LocalDateTime at, String username, String userName, String action, String module,
                       String entity, String entityId, String label, String changes, String ip, String tx) {
        public String getEntityLabel() { return humanize(entity); }
        public List<Change> getDiff() {
            List<Change> out = new ArrayList<>();
            if (changes == null || changes.isEmpty()) return out;
            for (String l : changes.split("\n")) {
                String[] p = l.split(String.valueOf(AuditListener.SEP), -1);
                if (p.length == 3) out.add(new Change(p[0], p[1], p[2]));
            }
            return out;
        }
        public boolean isSummary() { return entityId == null && !action.startsWith("LOG"); }
        public boolean isSignIn() { return action.startsWith("LOG"); }
        public String getWho() { return userName != null && !userName.isBlank() ? userName : username != null ? username : "—"; }
        public String getActionLabel() {
            return switch (action) {
                case "CREATE" -> "Created"; case "UPDATE" -> "Changed"; case "DELETE" -> "Deleted";
                case "LOGIN" -> "Signed in"; case "LOGOUT" -> "Signed out"; case "LOGIN_FAILED" -> "Sign-in failed";
                default -> action;
            };
        }
        public String getTone() {
            return switch (action) {
                case "CREATE" -> "status-approved"; case "DELETE", "LOGIN_FAILED" -> "status-rejected";
                case "UPDATE" -> "status-pending"; default -> "status-draft";
            };
        }
        /** Title shown in the list, e.g. "Payment voucher DV-2026-0012". */
        public String getTitle() {
            if (isSignIn()) return label;
            if (isSummary()) return humanize(entity) + " lines: " + label;
            String ref = label != null && !label.isBlank() ? label : "#" + entityId;
            return humanize(entity) + " " + ref;
        }
        /** Page of the record, when it still exists and has one. */
        public String getHref() {
            if (entityId == null || "DELETE".equals(action)) return null;
            String base = LINKS.get(entity);
            return base == null ? null : base.replace("{id}", entityId);
        }
    }

    /** One save: its lines, the main one first. */
    public record Group(Line main, List<Line> related) {
        public int getSize() { return 1 + related.size(); }
    }

    public record Filter(LocalDate from, LocalDate to, String user, String module, String action, String q, String entity, String entityId) {}

    public record Result(List<Group> groups, int page, boolean more, long total) {}

    static final Map<String, String> LINKS = Map.ofEntries(
            Map.entry("Quotation", "/Quotations/{id}"),
            Map.entry("JobOrder", "/JobOrders/{id}"),
            Map.entry("PurchaseOrder", "/Purchasing/Procurements/{id}"),
            Map.entry("Canvass", "/Purchasing/Canvasses/{id}"),
            Map.entry("Material", "/Inventory/Materials/{id}"),
            Map.entry("StockReceipt", "/Inventory/StockReceiving/{id}"),
            Map.entry("StockIssue", "/Inventory/StockIssuance/{id}"),
            Map.entry("Bill", "/Accounting/Payables/{id}"),
            Map.entry("PaymentVoucher", "/Accounting/Payments/{id}"),
            Map.entry("JournalEntry", "/Accounting/Journal/{id}"),
            Map.entry("Account", "/Accounting/Accounts/{id}"),
            Map.entry("ClientBilling", "/Accounting/Billing/{id}"),
            Map.entry("ClientCollection", "/Accounting/Collections/{id}"),
            Map.entry("SubconBilling", "/Accounting/SubconBillings/{id}"),
            Map.entry("SubconContract", "/Accounting/SubconBillings/contract/{id}"),
            Map.entry("CashAdvance", "/Accounting/PettyCash/advance/{id}"),
            Map.entry("BankRecon", "/Accounting/BankRecon/{id}"));

    private final JdbcTemplate jdbc;
    private volatile LocalDate lastPurge;

    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Result search(Filter f, int page) {
        List<Object> args = new ArrayList<>();
        StringBuilder w = new StringBuilder(" where 1=1");
        if (f.entity() != null && f.entityId() != null) {
            w.append(" and entity = ? and entity_id = ?"); args.add(f.entity()); args.add(f.entityId());
        } else {
            if (f.from() != null) { w.append(" and at >= ?"); args.add(Timestamp.valueOf(f.from().atStartOfDay())); }
            if (f.to() != null) { w.append(" and at < ?"); args.add(Timestamp.valueOf(f.to().plusDays(1).atStartOfDay())); }
        }
        if (f.user() != null) { w.append(" and username = ?"); args.add(f.user()); }
        if (f.module() != null) { w.append(" and module = ?"); args.add(f.module()); }
        if (f.action() != null) {
            if ("SIGNIN".equals(f.action())) w.append(" and action like 'LOG%'");
            else if ("CHANGES".equals(f.action())) w.append(" and action not like 'LOG%'");
            else { w.append(" and action = ?"); args.add(f.action()); }
        }
        if (f.q() != null) {
            w.append(" and (label ilike ? or entity ilike ? or entity_id = ? or user_name ilike ? or changes ilike ?)");
            String like = "%" + f.q().replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like); args.add("%" + f.q().replace(" ", "") + "%"); args.add(f.q()); args.add(like); args.add(like);
        }
        // Page by saves (tx), so a save's lines stay together. Lines with no tx are their own save.
        String key = "coalesce(tx, 'id' || id)";
        List<Object> pa = new ArrayList<>(args);
        pa.add(PAGE + 1); pa.add(page * PAGE);
        List<String> keys = jdbc.queryForList("select k from (select " + key + " k, max(id) m from sys_audit" + w
                + " group by 1) s order by m desc limit ? offset ?", String.class, pa.toArray());
        boolean more = keys.size() > PAGE;
        if (more) keys = keys.subList(0, PAGE);
        List<Group> groups = new ArrayList<>();
        if (!keys.isEmpty()) {
            List<Object> la = new ArrayList<>(args);
            la.addAll(keys);
            String in = String.join(",", Collections.nCopies(keys.size(), "?"));
            List<Line> lines = jdbc.query("select id, at, username, user_name, action, module, entity, entity_id, label, changes, ip, tx from sys_audit"
                    + w + " and " + key + " in (" + in + ") order by id", (rs, i) -> new Line(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                    rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                    rs.getString(10), rs.getString(11), rs.getString(12)), la.toArray());
            Map<String, List<Line>> byKey = new HashMap<>();
            for (Line l : lines) byKey.computeIfAbsent(l.tx() != null ? l.tx() : "id" + l.id(), k -> new ArrayList<>()).add(l);
            for (String k : keys) {
                List<Line> g = byKey.get(k);
                if (g == null || g.isEmpty()) continue;
                Line main = g.stream().filter(l -> !l.isSummary() && !isChild(l.entity())).findFirst()
                        .orElse(g.stream().filter(l -> !l.isSummary()).findFirst().orElse(g.get(0)));
                List<Line> rest = new ArrayList<>(g);
                rest.remove(main);
                groups.add(new Group(main, rest));
            }
        }
        Long total = jdbc.queryForObject("select count(*) from sys_audit" + w, Long.class, args.toArray());
        return new Result(groups, page, more, total == null ? 0 : total);
    }

    static boolean isChild(String entity) {
        return entity != null && (entity.endsWith("Line") || entity.endsWith("Item") || entity.endsWith("Signoff")
                || entity.endsWith("Quote") || entity.endsWith("Supplier") && entity.startsWith("Canvass"));
    }

    public List<String> users() {
        return jdbc.queryForList("select distinct username from sys_audit where username is not null order by 1", String.class);
    }

    public List<String> modules() {
        return jdbc.queryForList("select distinct module from sys_audit where module is not null order by 1", String.class);
    }

    public Map<String, Object> stats() {
        return jdbc.queryForMap("select count(*) total, min(at) since,"
                + " count(*) filter (where at >= now() - interval '1 day') today,"
                + " count(*) filter (where action = 'DELETE' and at >= now() - interval '30 days') deletes,"
                + " count(*) filter (where action = 'LOGIN_FAILED' and at >= now() - interval '7 days') failed from sys_audit");
    }

    /** Removes lines older than the setting, at most once a day. */
    public int purge(int keepDays, boolean force) {
        if (keepDays <= 0) return 0;
        LocalDate today = LocalDate.now();
        if (!force && today.equals(lastPurge)) return 0;
        lastPurge = today;
        return jdbc.update("delete from sys_audit where at < ?", Timestamp.valueOf(today.minusDays(keepDays).atStartOfDay()));
    }

    static String humanize(String s) {
        if (s == null || s.isEmpty()) return "";
        String spaced = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        spaced = spaced.toLowerCase().replaceAll(" id$", " ID");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
