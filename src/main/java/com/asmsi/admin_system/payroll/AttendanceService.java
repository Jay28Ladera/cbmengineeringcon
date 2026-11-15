package com.asmsi.admin_system.payroll;

import com.asmsi.admin_system.entity.*;
import com.asmsi.admin_system.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Biometric punches: saving them (from a device, a file or by hand), matching them to employees,
 * turning a day's punches into REG / OT / late, and writing those into the time sheet.
 */
@Service
public class AttendanceService {

    public record Punch(String pin, LocalDateTime time, Integer state, String name) {}

    public record SaveResult(int added, int duplicates, int skipped, Set<String> pins) {
        public int total() { return added + duplicates; }
    }

    /** A day's attendance for one employee, worked out from the punches. */
    public record Day(LocalDate date, List<LocalDateTime> punches, LocalTime in, LocalTime out,
                      BigDecimal reg, BigDecimal ot, int late, String status) {
        public boolean usable() { return "PRESENT".equals(status) || "LATE".equals(status); }
        public String regText() { return reg == null || reg.signum() == 0 ? "" : reg.stripTrailingZeros().toPlainString(); }
        public String otText() { return ot == null || ot.signum() == 0 ? "" : ot.stripTrailingZeros().toPlainString(); }
    }

    public record ApplyResult(int employees, int daysWritten, int daysKept, int daysIncomplete, int unmatchedPins) {}

    private final AttendanceLogRepository logs;
    private final EmployeeRepository employees;
    private final BiometricUserRepository users;
    private final TimeEntryRepository entries;
    private final PayrollService payroll;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AttendanceService(AttendanceLogRepository logs, EmployeeRepository employees, BiometricUserRepository users,
                             TimeEntryRepository entries, PayrollService payroll, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.logs = logs; this.employees = employees; this.users = users; this.entries = entries; this.payroll = payroll; this.jdbc = jdbc;
    }

    // ================================================================ saving

    /** Saves punches, skipping ones already stored (same device ID and time). Remembers device user names. */
    @Transactional
    public SaveResult save(List<Punch> punches, String source, Long deviceId, String by) {
        int added = 0, dup = 0, skipped = 0;
        Set<String> pins = new TreeSet<>();
        if (punches.isEmpty()) return new SaveResult(0, 0, 0, pins);
        // Repeats inside this batch are skipped here; ones already in the database by "on conflict".
        Set<String> existing = new HashSet<>();
        Map<String, String> names = new HashMap<>();
        LocalDateTime tooNew = LocalDateTime.now().plusDays(2);
        List<Object[]> batch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Punch p : punches) {
            String pin = cleanPin(p.pin());
            if (pin == null || p.time() == null || p.time().getYear() < 2000 || p.time().isAfter(tooNew)) { skipped++; continue; }
            LocalDateTime t = p.time().withNano(0);
            if (!existing.add(pin + "|" + t)) { dup++; continue; }
            batch.add(new Object[]{pin, java.sql.Timestamp.valueOf(t), p.state(), source, deviceId, by, java.sql.Timestamp.valueOf(now)});
            pins.add(pin);
            if (p.name() != null && !p.name().isBlank()) names.put(pin, p.name().trim());
        }
        // One batched statement instead of one query per punch: a machine's first upload can hold
        // tens of thousands of punches.
        for (int i = 0; i < batch.size(); i += 1000) {
            int[] counts = jdbc.batchUpdate(
                    "insert into hr_attendance_log (pin, punch_time, punch_state, source, device_id, created_by, created_at) " +
                    "values (?, ?, ?, ?, ?, ?, ?) on conflict (pin, punch_time) do nothing",
                    batch.subList(i, Math.min(i + 1000, batch.size())));
            for (int c : counts) { if (c == 0) dup++; else added++; }
        }
        if (!names.isEmpty()) {
            Map<String, BiometricUser> known = new HashMap<>();
            for (BiometricUser u : users.findAll()) known.put(u.getPin(), u);
            names.forEach((pin, name) -> {
                BiometricUser u = known.get(pin);
                if (u != null && name.equals(u.getName())) return;
                rememberUser(pin, name, deviceId);
            });
        }
        return new SaveResult(added, dup, skipped, pins);
    }

    @Transactional
    public void rememberUser(String pin, String name, Long deviceId) {
        pin = cleanPin(pin);
        if (pin == null || name == null || name.isBlank()) return;
        BiometricUser u = users.findByPin(pin).orElseGet(BiometricUser::new);
        u.setPin(pin);
        u.setName(name.trim().length() > 120 ? name.trim().substring(0, 120) : name.trim());
        if (deviceId != null) u.setDeviceId(deviceId);
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
    }

    /** Device IDs are kept as text without leading zeros, so "0015" and "15" are the same person. */
    public static String cleanPin(String pin) {
        if (pin == null) return null;
        String p = pin.trim();
        if (p.isEmpty() || p.length() > 32) return null;
        if (p.matches("\\d+")) p = p.replaceFirst("^0+(?=\\d)", "");
        return p;
    }

    // ================================================================ file import

    /** Reads an attendance export: ZKTeco attlog.dat / .txt, CSV, or Excel (.xlsx / .xls). */
    public List<Punch> parse(String filename, InputStream in) throws Exception {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        List<List<String>> rows = new ArrayList<>();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            DataFormatter fmt = new DataFormatter();
            try (Workbook wb = WorkbookFactory.create(in)) {
                for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                    for (Row r : wb.getSheetAt(s)) {
                        List<String> cells = new ArrayList<>();
                        for (int c = 0; c < Math.max(0, r.getLastCellNum()); c++) {
                            Cell cell = r.getCell(c);
                            if (cell == null) { cells.add(""); continue; }
                            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                                LocalDateTime dt = cell.getLocalDateTimeCellValue();
                                double v = cell.getNumericCellValue();
                                if (v < 1) cells.add(dt.toLocalTime().toString());           // time only
                                else if (v == Math.floor(v)) cells.add(dt.toLocalDate().toString()); // date only
                                else cells.add(dt.toLocalDate() + " " + dt.toLocalTime());
                            } else cells.add(fmt.formatCellValue(cell).trim());
                        }
                        rows.add(cells);
                    }
                }
            }
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.replace("﻿", "");
                if (line.isBlank()) continue;
                if (line.indexOf('\t') >= 0) rows.add(trimAll(line.split("\t", -1)));
                else if (line.indexOf(',') >= 0) rows.add(csv(line));
                else if (line.indexOf(';') >= 0) rows.add(trimAll(line.split(";", -1)));
                else rows.add(Arrays.asList(line.trim().split("\\s+")));
            }
        }
        return toPunches(rows);
    }

    private static final Pattern PIN_HEAD = Pattern.compile("^(ac-?no\\.?|no\\.?|user ?id|userid|pin|enroll(ment)? ?(no\\.?|number|id)|id|emp(loyee)? ?(id|no\\.?|number|code)|badge( ?no\\.?)?|person ?id|card ?no\\.?|ac no\\.?|acno)$");
    private static final Pattern NAME_HEAD = Pattern.compile("^(name|employee ?name|full ?name|user ?name|first ?name)$");

    private List<Punch> toPunches(List<List<String>> rows) {
        int pinCol = -1, nameCol = -1, dtCol = -1, dateCol = -1, timeCol = -1, stateCol = -1, start = 0;
        for (int i = 0; i < Math.min(rows.size(), 15) && pinCol < 0; i++) {
            List<String> r = rows.get(i);
            int p = -1, n = -1, dt = -1, d = -1, t = -1, st = -1;
            for (int c = 0; c < r.size(); c++) {
                String h = r.get(c).trim().toLowerCase(Locale.ROOT).replace("_", " ");
                if (p < 0 && PIN_HEAD.matcher(h).matches()) p = c;
                else if (n < 0 && NAME_HEAD.matcher(h).matches()) n = c;
                else if (dt < 0 && (h.equals("date/time") || h.equals("datetime") || h.equals("date time") || h.equals("check time")
                        || h.equals("checktime") || h.equals("punch time") || h.equals("time stamp") || h.equals("timestamp") || h.equals("att time"))) dt = c;
                else if (d < 0 && (h.equals("date") || h.equals("att date") || h.equals("punch date"))) d = c;
                else if (t < 0 && (h.equals("time") || h.equals("att time") || h.equals("punch time"))) t = c;
                else if (st < 0 && (h.equals("state") || h.equals("status") || h.equals("check type") || h.equals("punch state") || h.equals("in/out"))) st = c;
            }
            if (p >= 0 && (dt >= 0 || d >= 0 || t >= 0)) {
                pinCol = p; nameCol = n; dtCol = dt; dateCol = d; timeCol = t; stateCol = st; start = i + 1;
                if (dtCol < 0 && dateCol < 0) { dtCol = timeCol; timeCol = -1; }   // a single "Time" column holding date and time
            }
        }
        List<Punch> out = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (pinCol >= 0) {
                String pin = cell(r, pinCol);
                LocalDateTime t = dtCol >= 0 ? parseDateTime(cell(r, dtCol))
                        : parseDateTime(cell(r, dateCol) + " " + cell(r, timeCol));
                if (t == null && dtCol >= 0 && timeCol >= 0) t = parseDateTime(cell(r, dtCol) + " " + cell(r, timeCol));
                if (t == null || pin.isEmpty()) continue;
                out.add(new Punch(pin, t, stateFrom(cell(r, stateCol)), nameCol >= 0 ? cell(r, nameCol) : null));
            } else {
                // No header: ZKTeco attlog.dat — PIN, date time, status, verify, ...
                List<String> t = new ArrayList<>();
                for (String s : r) if (!s.isBlank()) t.add(s.trim());
                if (t.size() < 2) continue;
                LocalDateTime dt = parseDateTime(t.get(1));
                int next = 2;
                if (dt == null && t.size() >= 3) { dt = parseDateTime(t.get(1) + " " + t.get(2)); next = 3; }
                if (dt == null && t.size() >= 4) { dt = parseDateTime(t.get(1) + " " + t.get(2) + " " + t.get(3)); next = 4; }
                if (dt == null) continue;
                Integer state = t.size() > next ? stateFrom(t.get(next)) : null;
                out.add(new Punch(t.get(0), dt, state, null));
            }
        }
        return out;
    }

    private static Integer stateFrom(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.matches("\\d{1,2}")) return Integer.valueOf(v);
        if (v.startsWith("c/in") || v.equals("check in") || v.equals("in") || v.equals("checkin")) return 0;
        if (v.startsWith("c/out") || v.equals("check out") || v.equals("out") || v.equals("checkout")) return 1;
        if (v.startsWith("overtime in") || v.equals("ot in") || v.equals("ot-in")) return 4;
        if (v.startsWith("overtime out") || v.equals("ot out") || v.equals("ot-out")) return 5;
        if (v.startsWith("break out")) return 2;
        if (v.startsWith("break in")) return 3;
        return null;
    }

    private static final List<DateTimeFormatter> FORMATS = new ArrayList<>();
    static {
        String[] patterns = {
                "yyyy-M-d H:mm[:ss][.SSS]", "yyyy/M/d H:mm[:ss]", "yyyy-M-d'T'H:mm[:ss]",
                "M/d/yyyy H:mm[:ss]", "M/d/yyyy h:mm[:ss] a", "M/d/yyyy h:mm[:ss]a",
                "M/d/yy H:mm[:ss]", "M/d/yy h:mm[:ss] a",
                "d-MMM-yyyy H:mm[:ss]", "d-MMM-yyyy h:mm[:ss] a", "MMM d, yyyy h:mm[:ss] a", "MMM d yyyy h:mm[:ss] a",
                "yyyy-M-d h:mm[:ss] a", "yyyyMMddHHmmss", "yyyyMMdd HHmmss", "yyyy-M-d"
        };
        for (String p : patterns) FORMATS.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH));
    }

    static LocalDateTime parseDateTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ").replace("a.m.", "AM").replace("p.m.", "PM");
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : FORMATS) {
            try {
                var parsed = f.parseBest(s, LocalDateTime::from, LocalDate::from);
                if (parsed instanceof LocalDateTime dt) return dt;
            } catch (Exception ignore) { /* next format */ }
        }
        return null;
    }

    private static List<String> trimAll(String[] a) { List<String> l = new ArrayList<>(); for (String s : a) l.add(s.trim()); return l; }

    private static List<String> csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else q = !q; }
            else if (ch == ',' && !q) { out.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String cell(List<String> r, int i) { return i < 0 || i >= r.size() || r.get(i) == null ? "" : r.get(i).trim(); }

    // ================================================================ matching and days

    /** Employee for each Biometric ID. */
    public Map<String, Employee> byPin() {
        Map<String, Employee> m = new HashMap<>();
        for (Employee e : employees.findAll()) if (e.getBiometricId() != null && !e.getBiometricId().isBlank()) m.put(cleanPin(e.getBiometricId()), e);
        return m;
    }

    /** Employee a punch belongs to, or null if its device ID isn't linked to anyone yet. */
    public static Long employeeOf(AttendanceLog a, Map<String, Employee> pins) {
        if (a.getEmployeeId() != null) return a.getEmployeeId();
        Employee e = a.getPin() == null ? null : pins.get(a.getPin());
        return e == null ? null : e.getId();
    }

    /** Punch times per employee per day in [from, to]. */
    public Map<Long, Map<LocalDate, List<LocalDateTime>>> punchesBy(LocalDate from, LocalDate to) {
        Map<String, Employee> pins = byPin();
        Map<Long, Map<LocalDate, List<LocalDateTime>>> out = new HashMap<>();
        for (AttendanceLog a : logs.findByPunchTimeBetweenOrderByPunchTimeAsc(from.atStartOfDay(), to.plusDays(1).atStartOfDay().minusNanos(1))) {
            Long emp = employeeOf(a, pins);
            if (emp == null) continue;
            out.computeIfAbsent(emp, k -> new TreeMap<>()).computeIfAbsent(a.getPunchTime().toLocalDate(), k -> new ArrayList<>()).add(a.getPunchTime());
        }
        return out;
    }

    /**
     * Works out a day from its punches with the work schedule in Pay rules:
     * first punch = time in, last punch = time out (punches less than a minute apart count once).
     * <ul>
     *   <li>Late = minutes after the shift start, when arriving before the lunch break (lates are charged
     *       separately, so they don't also reduce REG).</li>
     *   <li>REG = hours within the shift, less the break, in half-hours, up to the hours per day.
     *       Leaving early (undertime) reduces REG.</li>
     *   <li>OT = time after the shift end, in whole blocks (30 min by default).</li>
     * </ul>
     */
    public static Day compute(LocalDate date, List<LocalDateTime> raw, PayrollSettings s) {
        List<LocalDateTime> p = new ArrayList<>();
        List<LocalDateTime> sorted = raw == null ? List.of() : raw.stream().sorted().toList();
        for (LocalDateTime t : sorted) if (p.isEmpty() || Duration.between(p.get(p.size() - 1), t).toSeconds() >= 60) p.add(t);
        if (p.isEmpty()) return new Day(date, p, null, null, BigDecimal.ZERO, BigDecimal.ZERO, 0, "NONE");
        LocalTime in = p.get(0).toLocalTime();
        if (p.size() == 1) return new Day(date, p, in, null, BigDecimal.ZERO, BigDecimal.ZERO, 0, "INCOMPLETE");
        LocalTime out = p.get(p.size() - 1).toLocalTime();

        int ss = min(s.getShiftStart()), se = min(s.getShiftEnd()), bs = min(s.getBreakStart()), be = min(s.getBreakEnd());
        int i = min(in), o = min(out);
        if (be < bs) be = bs;
        int worked = overlap(i, o, ss, se) - overlap(i, o, bs, be);
        int late = (i > ss && i < bs) ? i - ss : 0;
        int shift = Math.max(0, (se - ss) - overlap(ss, se, bs, be));
        int regMin = Math.min(shift, Math.max(0, worked + late));
        int hpdMin = (s.getHoursPerDay() == null ? 8 : s.getHoursPerDay()) * 60;
        regMin = Math.min(regMin, hpdMin);
        BigDecimal reg = BigDecimal.valueOf((regMin / 30) * 30).divide(BigDecimal.valueOf(60), 2, RoundingMode.DOWN).stripTrailingZeros();
        int otMin = s.isOtFromLogs() && o > se ? o - Math.max(se, i) : 0;
        int block = s.getOtBlockMinutes();
        otMin = (otMin / block) * block;
        BigDecimal ot = BigDecimal.valueOf(otMin).divide(BigDecimal.valueOf(60), 2, RoundingMode.DOWN).stripTrailingZeros();
        String status = reg.signum() == 0 && ot.signum() == 0 ? "CHECK" : (late > 0 ? "LATE" : "PRESENT");
        return new Day(date, p, in, out, reg, ot, late, status);
    }

    private static int min(LocalTime t) { return t.getHour() * 60 + t.getMinute(); }
    private static int overlap(int a1, int a2, int b1, int b2) { return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1)); }

    // ================================================================ time sheet

    /**
     * Writes REG / OT / late worked out from the logs into an open period's time sheet.
     * With {@code replace} false only empty days are filled; days HR already typed are kept.
     * Days with a single punch (no time-out) are left for HR to check.
     */
    @Transactional
    public ApplyResult applyToTimesheet(PayrollPeriod p, boolean replace, String group) {
        if (p.isLocked()) throw new IllegalStateException("This payroll is locked. Unlock it first.");
        PayrollSettings s = payroll.settings();
        Map<String, TimeEntry> existing = new HashMap<>();
        for (TimeEntry t : entries.findByPeriodId(p.getId())) existing.put(t.getEmployeeId() + "|" + t.getWorkDate(), t);
        Map<Long, Employee> emp = new HashMap<>();
        for (Employee e : employees.findAll()) emp.put(e.getId(), e);

        List<PayrollService.CellInput> cells = new ArrayList<>();
        int people = 0, kept = 0, incomplete = 0;
        for (var en : punchesBy(p.getStartDate(), p.getEndDate()).entrySet()) {
            Employee e = emp.get(en.getKey());
            if (e == null) continue;
            if (group != null && !group.isBlank() && !group.equals(e.getPayGroup() == null || e.getPayGroup().isBlank() ? "No group" : e.getPayGroup().trim())) continue;
            boolean any = false;
            for (var d : en.getValue().entrySet()) {
                Day day = compute(d.getKey(), d.getValue(), s);
                if (!day.usable()) { if (!"NONE".equals(day.status())) incomplete++; continue; }
                TimeEntry t = existing.get(e.getId() + "|" + d.getKey());
                if (!replace && t != null && !t.isEmpty()) { kept++; continue; }
                cells.add(new PayrollService.CellInput(e.getId(), d.getKey().toString(), day.reg(),
                        day.ot().signum() == 0 ? null : day.ot(), day.late() == 0 ? null : day.late()));
                any = true;
            }
            if (any) people++;
        }
        payroll.saveTimesheet(p, new PayrollService.TimesheetSave(cells, List.of()));
        int unmatched = logs.unmatchedPins().size();
        return new ApplyResult(people, cells.size(), kept, incomplete, unmatched);
    }
}
