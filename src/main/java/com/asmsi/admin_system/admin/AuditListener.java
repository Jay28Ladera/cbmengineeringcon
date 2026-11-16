package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.entity.User;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.action.spi.AfterTransactionCompletionProcess;
import org.hibernate.action.spi.BeforeTransactionCompletionProcess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Writes the audit trail: every record created, changed or deleted through the app, with who
 * did it and what changed (old → new).
 *
 * Changes are collected during the transaction and written in one batch just before it
 * commits, on the same connection: so a save that fails leaves no audit line, and a save that
 * succeeds always has one. If writing the trail fails, the save still goes through (logged).
 * System-generated detail (time sheet days, journal lines, stock movements…) is summarised as
 * one line per save, e.g. "312 created".
 */
@Component
public class AuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);
    static final char SEP = '\u001f';

    /** Never logged. */
    private static final Set<String> SKIP = Set.of("AuditEntry", "PasswordResetToken", "StaffActivation");
    /** Logged as a count per save. */
    private static final Set<String> SUMMARY = Set.of("TimeEntry", "TimesheetRow", "PayrollResult", "JournalLine",
            "StockMovement", "AttendanceLog", "MarketPrice", "BiometricUser", "StatementLine", "PayLine", "DepreciationLine");
    /** Fields whose value is never shown. */
    private static final Set<String> SECRET = Set.of("password", "resetToken", "token", "tokenHash", "apiKey", "secret", "signToken", "publicToken");
    /** Bookkeeping fields that change on their own. */
    private static final Set<String> NOISE = Set.of("updatedAt", "lastSeen", "lastLogin", "version", "lastSeenAt");
    private static final List<String> LABEL_FIELDS = List.of("number", "quotationNumber", "jobOrderNumber", "code", "username",
            "name", "title", "fullName", "projectName", "description", "reference", "memo", "kind");

    private final EntityManagerFactory emf;
    private final ThreadLocal<IdentityHashMap<Object, Batch>> batches = ThreadLocal.withInitial(IdentityHashMap::new);

    public AuditListener(EntityManagerFactory emf) { this.emf = emf; }

    @PostConstruct
    void register() {
        EventListenerRegistry r = emf.unwrap(SessionFactoryImplementor.class).getServiceRegistry().getService(EventListenerRegistry.class);
        r.appendListeners(EventType.POST_INSERT, this);
        r.appendListeners(EventType.POST_UPDATE, this);
        r.appendListeners(EventType.POST_DELETE, this);
    }

    @Override public boolean requiresPostCommitHandling(EntityPersister persister) { return false; }

    // ---------------------------------------------------------------- events

    @Override
    public void onPostInsert(PostInsertEvent e) {
        String name = e.getEntity().getClass().getSimpleName();
        if (SKIP.contains(name)) return;
        Batch b = batch(e.getSession());
        if (SUMMARY.contains(name)) { b.count(e.getEntity(), 0); return; }
        b.add(row("CREATE", e.getEntity(), e.getId(), e.getPersister(), e.getState(), describe(e.getPersister(), null, e.getState(), null, e.getSession())));
    }

    @Override
    public void onPostUpdate(PostUpdateEvent e) {
        String name = e.getEntity().getClass().getSimpleName();
        if (SKIP.contains(name)) return;
        String diff;
        if (e.getOldState() == null) diff = "";
        else {
            diff = describe(e.getPersister(), e.getOldState(), e.getState(), e.getDirtyProperties(), e.getSession());
            if (diff.isEmpty()) return; // only bookkeeping fields changed
        }
        Batch b = batch(e.getSession());
        if (SUMMARY.contains(name)) { b.count(e.getEntity(), 1); return; }
        b.add(row("UPDATE", e.getEntity(), e.getId(), e.getPersister(), e.getState(), diff));
    }

    @Override
    public void onPostDelete(PostDeleteEvent e) {
        String name = e.getEntity().getClass().getSimpleName();
        if (SKIP.contains(name)) return;
        Batch b = batch(e.getSession());
        if (SUMMARY.contains(name)) { b.count(e.getEntity(), 2); return; }
        b.add(row("DELETE", e.getEntity(), e.getId(), e.getPersister(), e.getDeletedState(), describe(e.getPersister(), null, e.getDeletedState(), null, e.getSession())));
    }

    // ---------------------------------------------------------------- rows

    record Row(LocalDateTime at, String username, String userName, String action, String module, String entity,
               String entityId, String label, String changes, String ip) {}

    private Row row(String action, Object entity, Object id, EntityPersister p, Object[] state, String changes) {
        String[] who = who();
        Class<?> c = entity.getClass();
        return new Row(LocalDateTime.now(), who[0], who[1], action, module(c), c.getSimpleName(),
                id == null ? null : cut(String.valueOf(id), 60), cut(label(p, state), 200), changes, who[2]);
    }

    /** "field SEP old SEP new" per line. With old == null every non-empty field is listed (create / delete). */
    private String describe(EntityPersister p, Object[] old, Object[] now, int[] dirty, SharedSessionContractImplementor s) {
        String[] names = p.getPropertyNames();
        Type[] types = p.getPropertyTypes();
        StringBuilder sb = new StringBuilder();
        int[] idx;
        if (old != null && dirty != null) idx = dirty;
        else { idx = new int[names.length]; for (int i = 0; i < idx.length; i++) idx[i] = i; }
        for (int i : idx) {
            String f = names[i];
            if (types[i].isCollectionType() || NOISE.contains(f)) continue;
            String nv = text(types[i], now == null ? null : now[i], s);
            String ov = old == null ? "" : text(types[i], old[i], s);
            if (old != null && Objects.equals(ov, nv)) continue;
            if (old == null && nv.isEmpty()) continue;
            if (SECRET.contains(f)) { ov = ov.isEmpty() ? "" : "••••"; nv = nv.isEmpty() ? "" : "••••"; if (old != null) nv = "(changed)"; }
            if (sb.length() > 0) sb.append('\n');
            sb.append(f).append(SEP).append(clean(ov)).append(SEP).append(clean(nv));
        }
        return sb.toString();
    }

    private static String text(Type t, Object v, SharedSessionContractImplementor s) {
        if (v == null) return "";
        if (t.isEntityType()) {
            try { Object id = s.getFactory().getPersistenceUnitUtil().getIdentifier(v); return id == null ? "" : "#" + id; }
            catch (RuntimeException ex) { return "#?"; }
        }
        if (v instanceof byte[] b) return "(" + b.length + " bytes)";
        if (v instanceof BigDecimal d) return d.signum() == 0 ? "0" : d.stripTrailingZeros().toPlainString();
        if (v instanceof LocalDateTime d) return d.withNano(0).toString().replace('T', ' ');
        String x = String.valueOf(v);
        if (x.startsWith("data:") && x.length() > 80) return "(image)";
        return x.length() > 500 ? x.substring(0, 500) + "…" : x;
    }

    private static String clean(String v) { return v.replace(SEP, ' ').replace('\n', ' ').replace('\r', ' '); }

    private static String label(EntityPersister p, Object[] state) {
        if (state == null) return null;
        String[] names = p.getPropertyNames();
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < names.length; i++) m.put(names[i], state[i]);
        Object first = m.get("firstName"), last = m.get("lastName");
        if (first != null || last != null) return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        for (String f : LABEL_FIELDS) {
            Object v = m.get(f);
            if (v instanceof String str && !str.isBlank()) return str.trim();
        }
        return null;
    }

    static String module(Class<?> c) {
        String n = c.getSimpleName(), pkg = c.getPackageName();
        if (n.equals("CompanyInfo")) return "Administration";
        if (pkg.endsWith(".accounting")) return "Accounting";
        if (pkg.endsWith(".admin")) return "Administration";
        if (pkg.endsWith(".inventory"))
            return n.startsWith("Canvass") || n.startsWith("Purchase") || n.equals("Supplier") || n.equals("MarketPrice") ? "Purchasing" : "Inventory";
        if (n.startsWith("Quot") || n.startsWith("Quote") || n.equals("Inquiry")) return "Sales";
        if (n.equals("JobOrder")) return "Projects";
        if (n.startsWith("User") || n.equals("DepartmentAccess") || n.equals("StaffActivation")) return "Administration";
        return "HR";
    }

    private static String[] who() {
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest r = sra.getRequest();
            ip = r.getRemoteAddr();
        }
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof User u) return new String[]{u.getUsername(), u.getName(), ip};
        if (ip != null) return new String[]{null, "Public link / not signed in", ip};
        return new String[]{null, "System", null};
    }

    static String cut(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }

    // ---------------------------------------------------------------- batching

    private Batch batch(EventSource session) {
        IdentityHashMap<Object, Batch> map = batches.get();
        if (map.size() > 20) map.clear(); // sessions that never completed a transaction
        Batch b = map.get(session);
        if (b == null) {
            b = new Batch(session);
            map.put(session, b);
            session.getActionQueue().registerProcess((BeforeTransactionCompletionProcess) b);
            session.getActionQueue().registerProcess((AfterTransactionCompletionProcess) b);
        }
        return b;
    }

    private final class Batch implements BeforeTransactionCompletionProcess, AfterTransactionCompletionProcess {
        final Object session;
        final String tx = Long.toString(System.nanoTime(), 36) + Integer.toString(new Random().nextInt(1296), 36);
        final List<Row> rows = new ArrayList<>();
        /** entity class → {created, changed, deleted} */
        final Map<Class<?>, int[]> counts = new LinkedHashMap<>();

        Batch(Object session) { this.session = session; }
        /** Adds a line, folding it into an earlier one for the same record (create + set number = one create). */
        void add(Row r) {
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row o = rows.get(i);
                if (!Objects.equals(o.entity(), r.entity()) || o.entityId() == null || !o.entityId().equals(r.entityId())) continue;
                String label = r.label() != null ? r.label() : o.label();
                if ("CREATE".equals(o.action()) && "UPDATE".equals(r.action())) {
                    Map<String, String[]> m = parse(o.changes());
                    for (var c : parse(r.changes()).entrySet()) {
                        if (c.getValue()[1].isEmpty()) m.remove(c.getKey()); else m.put(c.getKey(), new String[]{"", c.getValue()[1]});
                    }
                    rows.set(i, withChanges(o, label, join(m), o.action())); return;
                }
                if ("UPDATE".equals(o.action()) && "UPDATE".equals(r.action())) {
                    Map<String, String[]> m = parse(o.changes());
                    for (var c : parse(r.changes()).entrySet()) {
                        String[] prev = m.get(c.getKey());
                        String before = prev != null ? prev[0] : c.getValue()[0];
                        if (before.equals(c.getValue()[1])) m.remove(c.getKey()); else m.put(c.getKey(), new String[]{before, c.getValue()[1]});
                    }
                    rows.set(i, withChanges(o, label, join(m), o.action())); return;
                }
                if ("CREATE".equals(o.action()) && "DELETE".equals(r.action())) { rows.remove(i); return; }
                if ("UPDATE".equals(o.action()) && "DELETE".equals(r.action())) { rows.set(i, r); return; }
                break;
            }
            rows.add(r);
        }
        void count(Object entity, int kind) { counts.computeIfAbsent(entity.getClass(), k -> new int[3])[kind]++; }

        @Override
        public void doBeforeTransactionCompletion(SessionImplementor s) {
            batches.get().remove(session);
            if (rows.isEmpty() && counts.isEmpty()) return;
            String[] who = who();
            for (var c : counts.entrySet()) {
                int[] n = c.getValue();
                List<String> parts = new ArrayList<>();
                if (n[0] > 0) parts.add(n[0] + " created");
                if (n[1] > 0) parts.add(n[1] + " changed");
                if (n[2] > 0) parts.add(n[2] + " deleted");
                String action = n[0] > 0 && n[1] == 0 && n[2] == 0 ? "CREATE" : n[2] > 0 && n[0] == 0 && n[1] == 0 ? "DELETE" : "UPDATE";
                rows.add(new Row(LocalDateTime.now(), who[0], who[1], action, module(c.getKey()), c.getKey().getSimpleName(),
                        null, String.join(", ", parts), null, who[2]));
            }
            try {
                s.doWork(conn -> write(conn, rows, tx));
            } catch (RuntimeException ex) {
                log.warn("Audit trail not written ({} lines): {}", rows.size(), ex.getMessage());
            }
        }

        @Override
        public void doAfterTransactionCompletion(boolean success, SharedSessionContractImplementor s) {
            batches.get().remove(session);
            if (batches.get().isEmpty()) batches.remove();
        }
    }

    private static Row withChanges(Row o, String label, String changes, String action) {
        return new Row(o.at(), o.username(), o.userName(), action, o.module(), o.entity(), o.entityId(), label, changes, o.ip());
    }

    private static Map<String, String[]> parse(String changes) {
        Map<String, String[]> m = new LinkedHashMap<>();
        if (changes == null || changes.isEmpty()) return m;
        for (String l : changes.split("\n")) {
            String[] p = l.split(String.valueOf(SEP), -1);
            if (p.length == 3) m.put(p[0], new String[]{p[1], p[2]});
        }
        return m;
    }

    private static String join(Map<String, String[]> m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append(SEP).append(e.getValue()[0]).append(SEP).append(e.getValue()[1]);
        }
        return sb.toString();
    }

    /** For changes made with plain SQL (settings): one line, written in the caller's transaction. */
    public static void writeDirect(org.springframework.jdbc.core.JdbcTemplate jdbc, String action, String module, String entity,
                                   String entityId, String label, String field, String before, String after) {
        String[] who = who();
        jdbc.update("insert into sys_audit (at, username, user_name, action, module, entity, entity_id, label, changes, ip) values (?,?,?,?,?,?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now()), who[0], cut(who[1], 160), action, module, entity, entityId, label,
                field + SEP + clean(before == null ? "" : before) + SEP + clean(after == null ? "" : after), cut(who[2], 64));
    }

    private static void write(Connection conn, List<Row> rows, String tx) throws java.sql.SQLException {
        Savepoint sp = conn.getAutoCommit() ? null : conn.setSavepoint();
        try (PreparedStatement ps = conn.prepareStatement("insert into sys_audit (at, username, user_name, action, module, entity, entity_id, label, changes, ip, tx)"
                + " values (?,?,?,?,?,?,?,?,?,?,?)")) {
            for (Row r : rows) {
                ps.setTimestamp(1, Timestamp.valueOf(r.at()));
                ps.setString(2, r.username()); ps.setString(3, cut(r.userName(), 160)); ps.setString(4, r.action());
                ps.setString(5, r.module()); ps.setString(6, r.entity()); ps.setString(7, r.entityId());
                ps.setString(8, r.label()); ps.setString(9, r.changes()); ps.setString(10, cut(r.ip(), 64)); ps.setString(11, tx);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (java.sql.SQLException ex) {
            if (sp != null) conn.rollback(sp);
            log.warn("Audit trail not written ({} lines): {}", rows.size(), ex.getMessage());
            return;
        }
        if (sp != null) try { conn.releaseSavepoint(sp); } catch (java.sql.SQLException ignore) { }
    }
}
