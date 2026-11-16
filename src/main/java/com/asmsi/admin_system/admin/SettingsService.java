package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.accounting.CompanyInfo;
import com.asmsi.admin_system.accounting.CompanyInfoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Company profile and system defaults, kept in memory (they are shown on every printout and
 * read on every page, and the database is remote). Saving here refreshes the copy.
 */
@Service
public class SettingsService {

    /** Document series numbered PREFIX-YEAR-0001 by InventoryService.nextNumber. */
    public record Series(String prefix, String table, String label, String module) {}

    public static final List<Series> SERIES = List.of(
            new Series("PO", "pur_purchase_order", "Purchase order", "Purchasing"),
            new Series("RR", "inv_stock_receipt", "Receiving report", "Inventory"),
            new Series("IS", "inv_stock_issue", "Stock issuance", "Inventory"),
            new Series("ADJ", "inv_stock_adjustment", "Stock adjustment", "Inventory"),
            new Series("JV", "acc_journal", "Journal entry", "Accounting"),
            new Series("BL", "acc_bill", "Supplier bill", "Accounting"),
            new Series("DV", "acc_voucher", "Payment (disbursement) voucher", "Accounting"),
            new Series("PB", "acc_client_billing", "Client billing", "Accounting"),
            new Series("CR", "acc_collection", "Collection receipt", "Accounting"),
            new Series("SC", "acc_subcon_contract", "Subcontract", "Accounting"),
            new Series("SCB", "acc_subcon_billing", "Subcontractor billing", "Accounting"),
            new Series("PCV", "acc_pcv", "Petty cash voucher", "Accounting"),
            new Series("PCR", "acc_pcf_replenishment", "Petty cash replenishment", "Accounting"),
            new Series("CA", "acc_cash_advance", "Cash advance", "Accounting"),
            new Series("RM", "acc_remittance", "Remittance", "Accounting"));

    public record SeriesRow(Series s, String last, long lastNo, long next, boolean seeded) {
        public String getNextNumber() { return s.prefix() + "-" + LocalDate.now().getYear() + "-" + String.format("%04d", next); }
    }

    private final CompanyInfoRepository companies;
    private final AppSettingsRepository settings;
    private final JdbcTemplate jdbc;
    private volatile CompanyInfo company;
    private volatile AppSettings app;

    public SettingsService(CompanyInfoRepository companies, AppSettingsRepository settings, JdbcTemplate jdbc) {
        this.companies = companies; this.settings = settings; this.jdbc = jdbc;
    }

    public CompanyInfo company() {
        CompanyInfo c = company;
        if (c == null) { c = companies.get(); company = c; }
        return c;
    }

    public AppSettings app() {
        AppSettings a = app;
        if (a == null) { a = settings.findById(1L).orElseGet(AppSettings::new); app = a; }
        return a;
    }

    /** Call after CompanyInfo is saved elsewhere (BIR page). */
    public void evict() { company = null; app = null; }

    @Transactional
    public CompanyInfo saveCompany(CompanyInfo c) {
        CompanyInfo saved = companies.save(c);
        company = saved;
        return saved;
    }

    @Transactional
    public AppSettings saveApp(AppSettings a, String by) {
        a.id = 1L; a.updatedBy = by; a.updatedAt = LocalDateTime.now();
        AppSettings saved = settings.save(a);
        app = saved;
        return saved;
    }

    // ---------------------------------------------------------------- numbering

    public List<SeriesRow> series() {
        int yr = LocalDate.now().getYear();
        List<SeriesRow> rows = new ArrayList<>();
        // One round trip for all series (remote database).
        StringBuilder sql = new StringBuilder();
        for (Series s : SERIES) {
            if (sql.length() > 0) sql.append(" union all ");
            sql.append("select '").append(s.prefix()).append("', (select number from ").append(s.table())
               .append(" where number like '").append(s.prefix()).append('-').append(yr).append("-%' order by number desc limit 1)")
               .append(", (select next_no from sys_number_seed where prefix = '").append(s.prefix()).append("' and yr = ").append(yr).append(")");
        }
        var found = new java.util.HashMap<String, Object[]>();
        jdbc.query(sql.toString(), rs -> { found.put(rs.getString(1), new Object[]{rs.getString(2), rs.getObject(3)}); });
        for (Series s : SERIES) {
            Object[] f = found.getOrDefault(s.prefix(), new Object[]{null, null});
            String last = (String) f[0];
            long lastNo = parseTail(last);
            Long seed = f[1] == null ? null : ((Number) f[1]).longValue();
            long next = Math.max(lastNo + 1, seed == null ? 1 : seed);
            rows.add(new SeriesRow(s, last, lastNo, next, seed != null && seed > lastNo + 1));
        }
        return rows;
    }

    /** Sets where this year's numbering continues. Can't go back to a number already issued. */
    @Transactional
    public String setNext(String prefix, long next) {
        Series s = SERIES.stream().filter(x -> x.prefix().equals(prefix)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown document series."));
        if (next < 1 || next > 999999) throw new IllegalArgumentException("Enter a number from 1 to 999999.");
        int yr = LocalDate.now().getYear();
        List<String> last = jdbc.queryForList("select number from " + s.table() + " where number like ? order by number desc limit 1", String.class, s.prefix() + "-" + yr + "-%");
        long lastNo = last.isEmpty() ? 0 : parseTail(last.get(0));
        if (next <= lastNo) throw new IllegalArgumentException(s.label() + ": " + last.get(0) + " is already issued, so the next number must be " + (lastNo + 1) + " or higher.");
        List<Long> before = jdbc.queryForList("select next_no from sys_number_seed where prefix = ? and yr = ?", Long.class, s.prefix(), yr);
        long was = Math.max(lastNo + 1, before.isEmpty() ? 1 : before.get(0));
        int n = jdbc.update("update sys_number_seed set next_no = ? where prefix = ? and yr = ?", next, s.prefix(), yr);
        if (n == 0) jdbc.update("insert into sys_number_seed (prefix, yr, next_no) values (?, ?, ?)", s.prefix(), yr, next);
        AuditListener.writeDirect(jdbc, "UPDATE", "Administration", "NumberSeed", s.prefix() + "-" + yr, s.label() + " numbering " + yr,
                "nextNumber", String.valueOf(was), String.valueOf(next));
        return s.prefix() + "-" + yr + "-" + String.format("%04d", next);
    }

    static long parseTail(String number) {
        if (number == null) return 0;
        int i = number.lastIndexOf('-');
        try { return Long.parseLong(number.substring(i + 1)); } catch (RuntimeException e) { return 0; }
    }
}
