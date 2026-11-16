package com.asmsi.admin_system.admin;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Full data backup as a ZIP: one CSV per table (PostgreSQL COPY, so values round-trip exactly) and
 * a restore.sql that loads them back with psql. Read-only, in one consistent snapshot.
 */
@Service
public class BackupService {

    public record TableInfo(String name, long rows) {}

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public BackupService(JdbcTemplate jdbc, DataSource dataSource) { this.jdbc = jdbc; this.dataSource = dataSource; }

    public List<TableInfo> tables() {
        List<TableInfo> out = new ArrayList<>();
        jdbc.query("select c.relname, c.reltuples::bigint from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                + " where n.nspname = 'public' and c.relkind = 'r' order by c.relname", rs -> {
            out.add(new TableInfo(rs.getString(1), Math.max(0, rs.getLong(2))));
        });
        return out;
    }

    public Map<String, Object> lastBackup() {
        List<Map<String, Object>> l = jdbc.queryForList("select at, user_name from sys_audit where entity = 'Backup' order by id desc limit 1");
        return l.isEmpty() ? null : l.get(0);
    }

    /** Recorded on the audit trail (call on the request thread, so it carries who did it). */
    public void record(boolean withAudit) {
        AuditListener.writeDirect(jdbc, "CREATE", "Administration", "Backup", null, "Downloaded a backup", "auditTrail", "", withAudit ? "included" : "left out");
    }

    public String fileName() { return "cbm-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"; }

    /** Writes the ZIP. {@code withAudit} = include the audit trail (can be large). */
    public int write(OutputStream out, boolean withAudit, String who) throws Exception {
        List<String> tables = new ArrayList<>(tables().stream().map(TableInfo::name).toList());
        if (!withAudit) tables.remove("sys_audit");
        List<String> order = loadOrder(tables);
        try (Connection c = dataSource.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            c.setReadOnly(true);
            try (var st = c.createStatement()) { st.execute("set transaction isolation level repeatable read"); }
            CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
            ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
            OutputStream keepOpen = new FilterOutputStream(zip) { @Override public void close() { } @Override public void write(byte[] b, int off, int len) throws IOException { zip.write(b, off, len); } };
            for (String t : order) {
                zip.putNextEntry(new ZipEntry("data/" + t + ".csv"));
                copy.copyOut("COPY public.\"" + t + "\" TO STDOUT WITH (FORMAT csv, HEADER true)", keepOpen);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("restore.sql"));
            zip.write(restoreSql(order).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme(order.size(), who).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            c.rollback();
            c.setReadOnly(false);
            c.setAutoCommit(auto);
        }
        return order.size();
    }

    /** Tables ordered so a table comes after the ones its foreign keys point to. */
    List<String> loadOrder(List<String> tables) {
        Map<String, Set<String>> deps = new HashMap<>();
        for (String t : tables) deps.put(t, new HashSet<>());
        jdbc.query("select tc.relname, rc.relname from pg_constraint k join pg_class tc on tc.oid = k.conrelid join pg_class rc on rc.oid = k.confrelid"
                + " join pg_namespace n on n.oid = tc.relnamespace where k.contype = 'f' and n.nspname = 'public'", rs -> {
            String t = rs.getString(1), r = rs.getString(2);
            if (deps.containsKey(t) && deps.containsKey(r) && !t.equals(r)) deps.get(t).add(r);
        });
        List<String> out = new ArrayList<>();
        Set<String> done = new HashSet<>();
        List<String> sorted = new ArrayList<>(tables);
        Collections.sort(sorted);
        while (out.size() < sorted.size()) {
            boolean progress = false;
            for (String t : sorted) if (!done.contains(t) && done.containsAll(deps.get(t))) { out.add(t); done.add(t); progress = true; }
            if (!progress) for (String t : sorted) if (done.add(t)) out.add(t); // cycle: add the rest
        }
        return out;
    }

    private String restoreSql(List<String> order) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Restores this backup into an EMPTY CBM database.\n")
          .append("-- 1. Point the portal at the new database and start it once, so it creates the tables; then stop it.\n")
          .append("-- 2. In this folder run:  psql \"<connection string of the new database>\" -f restore.sql\n")
          .append("-- 3. Start the portal again.\n\n")
          .append("\\set ON_ERROR_STOP on\nBEGIN;\n");
        List<String> rev = new ArrayList<>(order);
        Collections.reverse(rev);
        sb.append("TRUNCATE ").append(String.join(", ", rev.stream().map(t -> "public.\"" + t + "\"").toList())).append(" CASCADE;\n");
        for (String t : order) sb.append("\\copy public.\"").append(t).append("\" FROM 'data/").append(t).append(".csv' WITH (FORMAT csv, HEADER true)\n");
        sb.append("\n-- Continue ids after the restored rows\n");
        for (String t : order) {
            List<String> idCols = jdbc.queryForList("select column_name from information_schema.columns where table_schema = 'public' and table_name = ?"
                    + " and (is_identity = 'YES' or column_default like 'nextval%')", String.class, t);
            for (String col : idCols) sb.append("SELECT setval(pg_get_serial_sequence('public.\"").append(t).append("\"', '").append(col)
                    .append("'), COALESCE((SELECT MAX(\"").append(col).append("\") FROM public.\"").append(t).append("\"), 0) + 1, false);\n");
        }
        sb.append("COMMIT;\n");
        return sb.toString();
    }

    private static String readme(int tables, String who) {
        return "CBM Engineering & Construction portal: data backup\n"
                + "Made " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a")) + " by " + who + "\n"
                + tables + " tables, one CSV each in data/ (open any of them in Excel to look).\n\n"
                + "To restore into an empty database, see the steps at the top of restore.sql.\n"
                + "Keep this file safe: it holds all of the company's records, including employee pay and IDs.\n"
                + "Passwords are stored only as one-way hashes; no API keys or secrets are in the backup.\n";
    }
}
