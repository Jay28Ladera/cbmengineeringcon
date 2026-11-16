package com.asmsi.admin_system.accounting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Fixed asset register and straight-line depreciation.
 * Each month's run posts: Dr depreciation (5050 for assets used on a job, charged to that job;
 * 6140 otherwise) / Cr accumulated depreciation (1510). Disposal removes cost and accumulated
 * depreciation and books the gain (4095) or loss (6160).
 */
@Service
public class FixedAssetService {

    public record Category(String key, String label, String accountCode, int lifeMonths) {}

    public static final List<Category> CATEGORIES = List.of(
            new Category("EQUIPMENT", "Construction equipment", "1530", 60),
            new Category("TOOLS", "Tools", "1500", 36),
            new Category("VEHICLE", "Vehicles", "1520", 60),
            new Category("OFFICE", "Office equipment and furniture", "1540", 36),
            new Category("BUILDING", "Building and improvements", "1550", 120));

    public static Category category(String key) { return CATEGORIES.stream().filter(c -> c.key().equals(key)).findFirst().orElse(CATEGORIES.get(0)); }
    public static String categoryLabel(String key) { return category(key).label(); }

    /** An asset with its depreciation so far. */
    public record View(FixedAsset a, BigDecimal accumulated, LocalDate lastMonth) {
        public BigDecimal getBookValue() { return a.cost.subtract(accumulated); }
        public BigDecimal getRemaining() { return a.getDepreciable().subtract(accumulated).max(BigDecimal.ZERO); }
        public boolean isFullyDepreciated() { return getRemaining().signum() == 0; }
        public int getPct() { return a.getDepreciable().signum() == 0 ? 100 : accumulated.multiply(BigDecimal.valueOf(100)).divide(a.getDepreciable(), 0, java.math.RoundingMode.DOWN).intValue(); }
    }

    public record RunLine(FixedAsset a, BigDecimal amount, Long jobId) {}
    public record ScheduleRow(YearMonth month, BigDecimal amount, BigDecimal accumulated, BigDecimal bookValue, boolean posted) {}

    private final FixedAssetRepository assets;
    private final DepreciationRunRepository runs;
    private final DepreciationLineRepository runLines;
    private final BankAccountRepository banks;
    private final Ledger ledger;
    private final JdbcTemplate jdbc;

    public FixedAssetService(FixedAssetRepository assets, DepreciationRunRepository runs, DepreciationLineRepository runLines,
                             BankAccountRepository banks, Ledger ledger, JdbcTemplate jdbc) {
        this.assets = assets; this.runs = runs; this.runLines = runLines; this.banks = banks; this.ledger = ledger; this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- register

    @Transactional(readOnly = true)
    public List<View> views() {
        Map<Long, Object[]> posted = posted();
        List<View> out = new ArrayList<>();
        for (FixedAsset a : assets.findAllByOrderByCodeAsc()) out.add(view(a, posted));
        return out;
    }

    @Transactional(readOnly = true)
    public View view(FixedAsset a) { return view(a, posted()); }

    private View view(FixedAsset a, Map<Long, Object[]> posted) {
        Object[] p = posted.get(a.id);
        BigDecimal acc = a.openingAccum.add(p == null ? BigDecimal.ZERO : (BigDecimal) p[1]);
        return new View(a, acc, p == null ? null : (LocalDate) p[2]);
    }

    private Map<Long, Object[]> posted() {
        Map<Long, Object[]> m = new HashMap<>();
        for (Object[] r : runLines.postedByAsset()) m.put((Long) r[0], r);
        return m;
    }

    public String nextCode() {
        List<String> last = jdbc.queryForList("select code from acc_fixed_asset where code ~ '^FA-[0-9]+$' order by length(code) desc, code desc limit 1", String.class);
        int n = last.isEmpty() ? 1 : Integer.parseInt(last.get(0).substring(3)) + 1;
        return String.format("FA-%04d", n);
    }

    /** Registers a new asset and posts how it was acquired. */
    @Transactional
    public FixedAsset create(FixedAsset a, Long bankId, String by) {
        validate(a);
        if (a.acquiredDate == null) throw new IllegalArgumentException("Enter the date acquired.");
        if (a.cost == null || a.cost.signum() <= 0) throw new IllegalArgumentException("Enter the cost.");
        if (a.openingAccum.compareTo(a.getDepreciable()) > 0) throw new IllegalArgumentException("Depreciation to date can't be more than cost less salvage value.");
        if (a.depStart == null) a.depStart = a.acquiredDate.withDayOfMonth(1).plusMonths(a.acquiredDate.getDayOfMonth() <= 15 ? 0 : 1);
        a.depStart = a.depStart.withDayOfMonth(1);
        a.code = nextCode(); a.createdBy = by;
        a.assetAccountId = ledger.id(category(a.category).accountCode());
        if (!"PAID".equals(a.acquiredHow) && !"OPENING".equals(a.acquiredHow)) a.acquiredHow = "BOOKED";
        if ("BOOKED".equals(a.acquiredHow)) a.openingAccum = a.openingAccum == null ? BigDecimal.ZERO : a.openingAccum;
        assets.save(a);
        String memo = a.code + " " + a.name;
        switch (a.acquiredHow) {
            case "PAID" -> {
                BankAccount b = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose where it was paid from."));
                if (a.openingAccum.signum() != 0) throw new IllegalArgumentException("A new purchase has no depreciation to date.");
                a.acquisitionJournalId = ledger.post(a.acquiredDate, "Bought " + memo, "ASSET", a.id, a.code,
                        List.of(JournalLine.dr(a.assetAccountId, a.cost, memo), JournalLine.cr(b.accountId, a.cost, b.name)), by).id;
            }
            case "OPENING" -> {
                Long eq = ledger.id(Acct.OPENING_BALANCE_EQUITY);
                List<JournalLine> j = new ArrayList<>(List.of(JournalLine.dr(a.assetAccountId, a.cost, memo), JournalLine.cr(eq, a.cost, "Opening balance")));
                if (a.openingAccum.signum() > 0) {
                    j.add(JournalLine.dr(eq, a.openingAccum, "Opening balance"));
                    j.add(JournalLine.cr(ledger.id(Acct.ACCUMULATED_DEPRECIATION), a.openingAccum, memo + " · depreciation to date"));
                }
                a.acquisitionJournalId = ledger.post(a.acquiredDate.isBefore(LocalDate.now()) ? a.acquiredDate : LocalDate.now(), "Opening balance · " + memo, "ASSET", a.id, a.code, j, by).id;
            }
            default -> { }
        }
        return assets.save(a);
    }

    /** Changes details; cost and how it was acquired are fixed once registered. */
    @Transactional
    public FixedAsset update(FixedAsset a) {
        validate(a);
        if (posted().containsKey(a.id) && a.lifeMonths <= 0) throw new IllegalArgumentException("Enter the useful life.");
        return assets.save(a);
    }

    private static void validate(FixedAsset a) {
        if (a.name == null || a.name.isBlank()) throw new IllegalArgumentException("Enter the asset's name.");
        if (a.lifeMonths < 1 || a.lifeMonths > 600) throw new IllegalArgumentException("Useful life must be 1 to 600 months.");
        if (a.salvage == null || a.salvage.signum() < 0) a.salvage = BigDecimal.ZERO;
        if (a.cost != null && a.salvage.compareTo(a.cost) > 0) throw new IllegalArgumentException("Salvage value can't be more than the cost.");
        if (a.openingAccum == null || a.openingAccum.signum() < 0) a.openingAccum = BigDecimal.ZERO;
    }

    @Transactional
    public void delete(FixedAsset a, String by) {
        if (posted().containsKey(a.id)) throw new IllegalStateException("Depreciation has been posted for this asset. Dispose of it instead.");
        if (!a.isActive()) throw new IllegalStateException("Undo the disposal first.");
        ledger.reverseSource("ASSET", a.id, "Asset " + a.code + " deleted", by);
        assets.delete(a);
    }

    // ---------------------------------------------------------------- depreciation

    /** Amount for one asset in a month, given what is depreciated already. */
    static BigDecimal amountFor(FixedAsset a, YearMonth m, BigDecimal accumulated) {
        if (m.isBefore(a.getFirstOpenMonth())) return BigDecimal.ZERO;
        if (a.disposedDate != null && !m.isBefore(YearMonth.from(a.disposedDate))) return BigDecimal.ZERO;
        BigDecimal remaining = a.getDepreciable().subtract(accumulated);
        if (remaining.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal amt = a.getMonthly();
        // Last month of the life takes what's left (rounding).
        if (!m.isBefore(a.getEndMonth())) amt = remaining;
        return amt.min(remaining);
    }

    @Transactional(readOnly = true)
    public List<RunLine> preview(YearMonth m) {
        Map<Long, Object[]> posted = posted();
        List<RunLine> out = new ArrayList<>();
        for (FixedAsset a : assets.findAllByOrderByCodeAsc()) {
            if (!a.isActive()) continue;
            BigDecimal amt = amountFor(a, m, view(a, posted).accumulated());
            if (amt.signum() > 0) out.add(new RunLine(a, amt, a.jobOrderId));
        }
        return out;
    }

    /** The month that should be run next: the month after the last run, or the earliest start. */
    @Transactional(readOnly = true)
    public YearMonth nextMonth() {
        var last = runs.findFirstByStatusOrderByMonthEndDesc("POSTED");
        if (last.isPresent()) return YearMonth.from(last.get().monthEnd).plusMonths(1);
        return assets.findAll().stream().filter(FixedAsset::isActive).map(FixedAsset::getFirstOpenMonth).min(Comparator.naturalOrder())
                .orElse(YearMonth.now().minusMonths(1));
    }

    @Transactional
    public DepreciationRun post(YearMonth m, String by) {
        if (m.isAfter(YearMonth.now())) throw new IllegalArgumentException("Depreciation can't be posted for a month that hasn't started.");
        LocalDate end = m.atEndOfMonth();
        if (runs.findFirstByMonthEndAndStatus(end, "POSTED").isPresent()) throw new IllegalStateException("Depreciation for " + label(m) + " is already posted.");
        var last = runs.findFirstByStatusOrderByMonthEndDesc("POSTED");
        if (last.isPresent() && end.isBefore(last.get().monthEnd)) throw new IllegalStateException("Months are posted in order; the last one posted is " + label(YearMonth.from(last.get().monthEnd)) + ".");
        List<RunLine> lines = preview(m);
        if (lines.isEmpty()) throw new IllegalStateException("Nothing to depreciate for " + label(m) + ".");
        DepreciationRun r = new DepreciationRun();
        r.monthEnd = end; r.postedBy = by; r.assetCount = lines.size();
        r.total = lines.stream().map(RunLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        runs.save(r);
        Long onJobs = ledger.id(Acct.DEPRECIATION_JOBS), office = ledger.id(Acct.DEPRECIATION), accum = ledger.id(Acct.ACCUMULATED_DEPRECIATION);
        List<JournalLine> j = new ArrayList<>();
        for (RunLine l : lines) {
            DepreciationLine d = new DepreciationLine();
            d.runId = r.id; d.assetId = l.a().id; d.amount = l.amount(); d.jobOrderId = l.jobId();
            runLines.save(d);
            j.add(JournalLine.dr(l.jobId() != null ? onJobs : office, l.amount(), l.a().code + " " + l.a().name).job(l.jobId()));
        }
        j.add(JournalLine.cr(accum, r.total, "Depreciation " + label(m)));
        r.journalId = ledger.post(end, "Depreciation " + label(m), "DEPRECIATION", r.id, label(m), j, by).id;
        return runs.save(r);
    }

    @Transactional
    public void voidRun(DepreciationRun r, String by) {
        if (!r.isPosted()) return;
        var last = runs.findFirstByStatusOrderByMonthEndDesc("POSTED").orElse(null);
        if (last == null || !last.id.equals(r.id)) throw new IllegalStateException("Only the latest month can be undone.");
        Set<Long> ids = new HashSet<>();
        for (DepreciationLine l : runLines.findByRunId(r.id)) ids.add(l.assetId);
        for (FixedAsset a : assets.findAllById(ids))
            if (!a.isActive()) throw new IllegalStateException(a.code + " was disposed of after this month. Undo the disposal first.");
        ledger.reverseSource("DEPRECIATION", r.id, "Depreciation " + label(YearMonth.from(r.monthEnd)) + " undone", by);
        r.status = "VOID";
        runs.save(r);
    }

    // ---------------------------------------------------------------- disposal

    @Transactional
    public void dispose(FixedAsset a, LocalDate date, BigDecimal proceeds, Long bankId, String note, String by) {
        if (!a.isActive()) throw new IllegalStateException("Already disposed of.");
        if (date == null) throw new IllegalArgumentException("Enter the date.");
        if (date.isBefore(a.acquiredDate)) throw new IllegalArgumentException("The date is before it was acquired.");
        proceeds = proceeds == null || proceeds.signum() < 0 ? BigDecimal.ZERO : Ledger.r2(proceeds);
        View v = view(a);
        if (v.lastMonth() != null && !YearMonth.from(date).isAfter(YearMonth.from(v.lastMonth())))
            throw new IllegalArgumentException("Depreciation is already posted through " + label(YearMonth.from(v.lastMonth())) + "; the disposal must be after that month.");
        String memo = a.code + " " + a.name;
        List<JournalLine> j = new ArrayList<>();
        if (proceeds.signum() > 0) {
            BankAccount b = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose where the money went."));
            j.add(JournalLine.dr(b.accountId, proceeds, "Sold " + memo));
        }
        if (v.accumulated().signum() > 0) j.add(JournalLine.dr(ledger.id(Acct.ACCUMULATED_DEPRECIATION), v.accumulated(), memo + " · depreciation to date"));
        j.add(JournalLine.cr(a.assetAccountId, a.cost, memo));
        BigDecimal gain = proceeds.subtract(v.getBookValue());
        if (gain.signum() > 0) j.add(JournalLine.cr(ledger.id(Acct.GAIN_ON_DISPOSAL), gain, "Gain on " + memo));
        if (gain.signum() < 0) j.add(JournalLine.dr(ledger.id(Acct.LOSS_ON_DISPOSAL), gain.negate(), "Loss on " + memo));
        a.disposalJournalId = ledger.post(date, (proceeds.signum() > 0 ? "Sold " : "Disposed of ") + memo, "ASSET_DISPOSAL", a.id, a.code, j, by).id;
        a.status = "DISPOSED"; a.disposedDate = date; a.disposalProceeds = proceeds; a.disposalNote = note == null || note.isBlank() ? null : note.trim();
        assets.save(a);
    }

    @Transactional
    public void undoDisposal(FixedAsset a, String by) {
        if (a.isActive()) return;
        ledger.reverseSource("ASSET_DISPOSAL", a.id, "Disposal of " + a.code + " undone", by);
        a.status = "ACTIVE"; a.disposedDate = null; a.disposalProceeds = null; a.disposalNote = null; a.disposalJournalId = null;
        assets.save(a);
    }

    // ---------------------------------------------------------------- schedule

    @Transactional(readOnly = true)
    public List<ScheduleRow> schedule(FixedAsset a) {
        Map<YearMonth, BigDecimal> posted = new TreeMap<>();
        Map<Long, DepreciationRun> runById = new HashMap<>();
        for (DepreciationRun r : runs.findAll()) runById.put(r.id, r);
        for (DepreciationLine l : runLines.findByAssetId(a.id)) {
            DepreciationRun r = runById.get(l.runId);
            if (r != null && r.isPosted()) posted.merge(YearMonth.from(r.monthEnd), l.amount, BigDecimal::add);
        }
        List<ScheduleRow> out = new ArrayList<>();
        BigDecimal acc = a.openingAccum;
        YearMonth m = a.getStartMonth();
        YearMonth stop = a.disposedDate != null ? YearMonth.from(a.disposedDate) : null;
        YearMonth firstOpen = a.getFirstOpenMonth();
        for (int i = 0; i < 720; i++, m = m.plusMonths(1)) {
            if (stop != null && !m.isBefore(stop)) break;
            BigDecimal amt; boolean p = posted.containsKey(m);
            if (p) amt = posted.get(m);
            else {
                if (m.isBefore(firstOpen)) continue;
                if (!posted.isEmpty() && m.isBefore(((TreeMap<YearMonth, BigDecimal>) posted).lastKey())) continue; // gap before the last posted month
                amt = amountFor(a, m, acc);
                if (amt.signum() == 0) { if (acc.compareTo(a.getDepreciable()) >= 0) break; else continue; }
            }
            acc = acc.add(amt);
            out.add(new ScheduleRow(m, amt, acc, a.cost.subtract(acc), p));
            if (acc.compareTo(a.getDepreciable()) >= 0) break;
        }
        return out;
    }

    public static String label(YearMonth m) { return m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear(); }
}
