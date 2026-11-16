package com.asmsi.admin_system.admin;

import com.asmsi.admin_system.accounting.*;
import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.inventory.*;
import com.asmsi.admin_system.repository.EmployeeRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Brings existing records into the portal from a CSV or Excel file: suppliers, materials (with
 * opening stock), employees, and opening account balances. Each import is checked first (preview),
 * then saved in one transaction: either every valid row goes in, or nothing does.
 */
@Service
public class ImportService {

    public enum Kind {
        SUPPLIERS("Suppliers and subcontractors", "Matched by name: an existing supplier is updated (blank cells leave a value as it is)."),
        MATERIALS("Materials and opening stock", "Matched by code, else by name. Opening quantity and unit cost go into stock for materials that have none yet."),
        EMPLOYEES("Employees", "Matched by employee no., biometric ID or name. People on the organizational chart keep their name, position and department."),
        OPENING("Opening account balances", "Your trial balance on the start date: one entry, with any difference to Opening Balance Equity (3900).");
        public final String label, help;
        Kind(String label, String help) { this.label = label; this.help = help; }
        public String getLabel() { return label; }
        public String getHelp() { return help; }
        public String getKey() { return name(); }
        public static Kind parse(String s) { try { return valueOf(s.trim().toUpperCase()); } catch (RuntimeException e) { return null; } }
    }

    /** One checked row. action: NEW, UPDATE, SKIP or ERROR. */
    public record Row(int line, String action, String label, String detail) {
        public boolean isError() { return "ERROR".equals(action); }
    }

    public record Preview(Kind kind, List<Row> rows, List<String> warnings) {
        public long count(String action) { return rows.stream().filter(r -> r.action().equals(action)).count(); }
        public boolean isOk() { return rows.stream().anyMatch(r -> "NEW".equals(r.action()) || "UPDATE".equals(r.action())); }
    }

    /** Column → accepted header spellings (compared without spaces, case or punctuation). */
    static final Map<Kind, LinkedHashMap<String, List<String>>> FIELDS = new EnumMap<>(Kind.class);
    static {
        FIELDS.put(Kind.SUPPLIERS, fields(
                "name", "name,suppliername,supplier,company,payee",
                "contact", "contactperson,contact",
                "phone", "phone,mobile,contactno,contactnumber,telephone,tel",
                "email", "email,emailaddress",
                "address", "address,registeredaddress",
                "tin", "tin,tinno",
                "vat", "vatregistered,vat",
                "ewt", "ewtrate,ewt,withholdingrate,withholding",
                "terms", "paymentterms,terms",
                "supplies", "supplies,items,products,whattheysupply",
                "subcon", "subcontractor,subcon",
                "trade", "trade,scope",
                "payeeType", "payeetype,type",
                "atc", "atc",
                "notes", "notes,remarks"));
        FIELDS.put(Kind.MATERIALS, fields(
                "code", "code,materialcode,itemcode,sku",
                "name", "name,materialname,item,itemname,description",
                "category", "category,group",
                "unit", "unit,uom,unitofmeasure",
                "reorder", "reorderlevel,reorder,minimum,minstock",
                "location", "location,bin,warehouse",
                "qty", "openingqty,qty,quantity,onhand,stock,balance",
                "cost", "unitcost,cost,averagecost,price",
                "notes", "notes,remarks"));
        FIELDS.put(Kind.EMPLOYEES, fields(
                "employeeNo", "employeeno,empno,idno,employeeid,employeenumber",
                "name", "fullname,name,employeename,employee",
                "position", "position,designation,jobtitle",
                "department", "department,dept",
                "payGroup", "paygroup,group",
                "rateType", "ratetype,paytype,basis",
                "daily", "dailyrate,rateperday,daily,rate",
                "monthly", "monthlyrate,monthly,salary,monthlysalary",
                "sss", "sss,sssamount,ssscontribution",
                "philhealth", "philhealth,phic,philhealthamount",
                "pagibig", "pagibig,hdmf,pagibigamount",
                "cutoff", "contributioncutoff,cutoff,deducton",
                "hired", "datehired,hired,hiredate",
                "biometric", "biometricid,bioid,biometric",
                "tin", "tin,tinno",
                "sssNo", "sssno,sssnumber",
                "philhealthNo", "philhealthno,phicno,philhealthnumber",
                "pagibigNo", "pagibigno,hdmfno,mid,midno,pagibignumber",
                "birthDate", "birthdate,birthday,dob,dateofbirth",
                "address", "address",
                "taxStatus", "taxstatus,tax"));
        FIELDS.put(Kind.OPENING, fields(
                "code", "accountcode,code,account,acctcode",
                "name", "accountname,name,title",
                "debit", "debit,dr",
                "credit", "credit,cr",
                "amount", "amount,balance"));
    }

    private static LinkedHashMap<String, List<String>> fields(String... kv) {
        LinkedHashMap<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], List.of(kv[i + 1].split(",")));
        return m;
    }

    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final MaterialCategoryRepository categories;
    private final InventoryService inventory;
    private final EmployeeRepository employees;
    private final AccountRepository accounts;
    private final Ledger ledger;
    private final JdbcTemplate jdbc;

    public ImportService(SupplierRepository suppliers, MaterialRepository materials, MaterialCategoryRepository categories,
                         InventoryService inventory, EmployeeRepository employees, AccountRepository accounts, Ledger ledger, JdbcTemplate jdbc) {
        this.suppliers = suppliers; this.materials = materials; this.categories = categories; this.inventory = inventory;
        this.employees = employees; this.accounts = accounts; this.ledger = ledger; this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- reading files

    /** Rows of the file as field → value (fields named as in {@link #FIELDS}); the line number is under "#". */
    public List<Map<String, String>> read(Kind kind, String filename, InputStream in) throws IOException {
        List<List<String>> raw = filename != null && filename.toLowerCase().matches(".*\\.xlsx?$") ? readExcel(in) : readCsv(in);
        int headerAt = -1;
        Map<Integer, String> cols = new HashMap<>();
        // The header is the first row (of the first 10) that names at least two known columns.
        for (int r = 0; r < Math.min(10, raw.size()) && headerAt < 0; r++) {
            Map<Integer, String> found = new HashMap<>();
            for (int c = 0; c < raw.get(r).size(); c++) {
                String h = norm(raw.get(r).get(c));
                for (var f : FIELDS.get(kind).entrySet()) if (f.getValue().contains(h) && !found.containsValue(f.getKey())) { found.put(c, f.getKey()); break; }
            }
            if (found.size() >= 2 || (found.size() == 1 && FIELDS.get(kind).size() <= 2)) { headerAt = r; cols = found; }
        }
        if (headerAt < 0) throw new IllegalArgumentException("Couldn't find the header row. Use the template: its first row names the columns.");
        List<Map<String, String>> out = new ArrayList<>();
        for (int r = headerAt + 1; r < raw.size(); r++) {
            List<String> row = raw.get(r);
            Map<String, String> m = new LinkedHashMap<>();
            boolean any = false;
            for (var c : cols.entrySet()) {
                String v = c.getKey() < row.size() ? row.get(c.getKey()) : "";
                v = v == null ? "" : v.trim();
                if (!v.isEmpty()) any = true;
                m.put(c.getValue(), v);
            }
            if (!any) continue;
            m.put("#", String.valueOf(r + 1));
            out.add(m);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("The file has a header but no rows.");
        if (out.size() > 5000) throw new IllegalArgumentException("Up to 5,000 rows per import, please split the file.");
        return out;
    }

    static List<List<String>> readCsv(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        char sep = text.lines().findFirst().map(l -> l.chars().filter(ch -> ch == ';').count() > l.chars().filter(ch -> ch == ',').count() ? ';' : ',').orElse(',');
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false; }
                else cell.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == sep) { row.add(cell.toString()); cell.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
            } else cell.append(ch);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    static List<List<String>> readExcel(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sh = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            for (org.apache.poi.ss.usermodel.Row r : sh) {
                List<String> row = new ArrayList<>();
                for (int c = 0; c < r.getLastCellNum(); c++) {
                    Cell cell = r.getCell(c, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) { row.add(""); continue; }
                    CellType t = cell.getCellType() == CellType.FORMULA ? ev.evaluateFormulaCell(cell) : cell.getCellType();
                    if (t == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) row.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
                    else if (t == CellType.NUMERIC) row.add(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString());
                    else row.add(fmt.formatCellValue(cell, ev));
                }
                while (rows.size() < r.getRowNum()) rows.add(new ArrayList<>());
                rows.add(row);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Couldn't read the Excel file: " + e.getMessage());
        }
        return rows;
    }

    static String norm(String h) { return h == null ? "" : h.toLowerCase().replaceAll("[^a-z0-9]", ""); }

    // ---------------------------------------------------------------- templates

    public String template(Kind kind) {
        return switch (kind) {
            case SUPPLIERS -> "Name,Contact person,Phone,Email,Address,TIN,VAT registered,EWT rate,Payment terms,Supplies,Subcontractor,Trade,Payee type,ATC,Notes\n"
                    + "Citi Hardware,Ana Cruz,0917 123 4567,sales@citihardware.ph,\"Mandaue City, Cebu\",005-112-334-000,Yes,1,30 days,\"Cement, rebars, plywood\",No,,Corporation,,\n"
                    + "Volt Electrical Services,Ramon Tan,0918 222 3344,,Cebu City,201-555-777-000,Yes,2,15 days,,Yes,Electrical,Corporation,,\n";
            case MATERIALS -> "Code,Name,Category,Unit,Reorder level,Location,Opening qty,Unit cost,Notes\n"
                    + ",Portland cement 40kg,Cement and aggregates,bag,50,Main warehouse,120,265.00,\n"
                    + ",Deformed bar 10mm x 6m,Steel,pc,100,Main warehouse,340,185.50,\n";
            case EMPLOYEES -> "Employee no,Full name,Position,Department,Pay group,Rate type,Daily rate,Monthly rate,SSS,PhilHealth,Pag-IBIG,Contribution cutoff,Date hired,Biometric ID,TIN,SSS no,PhilHealth no,Pag-IBIG no,Birth date,Address,Tax status\n"
                    + "E-0101,\"DELA CRUZ, JUAN\",Mason,Construction,Site crew,DAILY,680,,,,,SECOND,2024-03-01,101,,34-1234567-8,,,1990-05-14,\"Talisay, Cebu\",REGULAR\n"
                    + "E-0102,\"REYES, MARIA\",Accounting Staff,Finance,Office,MONTHLY,,25000,1250,625,200,SECOND,2023-06-15,102,123-456-789-000,,,,,Cebu City,REGULAR\n";
            case OPENING -> "Account code,Account name,Debit,Credit\n"
                    + "1100,Accounts Receivable - Clients,850000.00,\n"
                    + "1500,Tools and Equipment,320000.00,\n"
                    + "2010,Accounts Payable - Suppliers,,410000.00\n"
                    + "3010,Owner's Capital,,760000.00\n";
        };
    }

    // ---------------------------------------------------------------- checking

    public Preview preview(Kind kind, List<Map<String, String>> rows) {
        return switch (kind) {
            case SUPPLIERS -> suppliers(rows, null);
            case MATERIALS -> materials(rows, null, null);
            case EMPLOYEES -> employees(rows, null);
            case OPENING -> opening(rows, null, null);
        };
    }

    @Transactional
    public Preview apply(Kind kind, List<Map<String, String>> rows, LocalDate date, String by) {
        Preview p = switch (kind) {
            case SUPPLIERS -> suppliers(rows, by);
            case MATERIALS -> materials(rows, date == null ? LocalDate.now() : date, by);
            case EMPLOYEES -> employees(rows, by);
            case OPENING -> opening(rows, date == null ? LocalDate.now() : date, by);
        };
        AuditListener.writeDirect(jdbc, "CREATE", "Administration", "Import", kind.name(), "Imported " + kind.label.toLowerCase(),
                "rows", "", p.count("NEW") + " new, " + p.count("UPDATE") + " updated, " + p.count("ERROR") + " skipped");
        return p;
    }

    // ---- suppliers

    private Preview suppliers(List<Map<String, String>> rows, String by) {
        boolean save = by != null;
        Map<String, Supplier> byName = new HashMap<>();
        for (Supplier s : suppliers.findAll()) byName.put(key(s.name), s);
        List<Row> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, String> r : rows) {
            int line = Integer.parseInt(r.get("#"));
            String name = v(r, "name");
            if (name == null) { out.add(new Row(line, "ERROR", "—", "No name")); continue; }
            if (!seen.add(key(name))) { out.add(new Row(line, "SKIP", name, "Same name appears earlier in the file")); continue; }
            Supplier s = byName.get(key(name));
            boolean isNew = s == null;
            List<String> problems = new ArrayList<>();
            BigDecimal ewt = num(v(r, "ewt"), problems, "EWT rate");
            if (ewt != null && ewt.compareTo(BigDecimal.ONE) < 0 && ewt.signum() > 0) ewt = ewt.multiply(BigDecimal.valueOf(100)); // 0.02 → 2
            if (ewt != null && (ewt.signum() < 0 || ewt.compareTo(BigDecimal.valueOf(50)) > 0)) problems.add("EWT rate must be 0–50%");
            String payee = v(r, "payeeType");
            if (payee != null) payee = payee.toUpperCase().startsWith("IND") ? "INDIVIDUAL" : "CORPORATION";
            if (!problems.isEmpty()) { out.add(new Row(line, "ERROR", name, String.join("; ", problems))); continue; }
            if (isNew) { s = new Supplier(); s.name = cut(name, 160); }
            s.contactPerson = pick(v(r, "contact"), s.contactPerson, 120);
            s.phone = pick(v(r, "phone"), s.phone, 60);
            s.email = pick(v(r, "email"), s.email, 160);
            s.address = pick(v(r, "address"), s.address, 300);
            s.tin = pick(v(r, "tin"), s.tin, 40);
            if (v(r, "vat") != null) s.vatRegistered = yes(v(r, "vat"));
            if (ewt != null) s.ewtRate = ewt.setScale(2, RoundingMode.HALF_UP);
            s.paymentTerms = pick(v(r, "terms"), s.paymentTerms, 120);
            s.supplies = pick(v(r, "supplies"), s.supplies, 300);
            if (v(r, "subcon") != null) s.subcon = yes(v(r, "subcon"));
            s.trade = pick(v(r, "trade"), s.trade, 80);
            if (payee != null) s.payeeType = payee;
            s.atc = pick(v(r, "atc") == null ? null : v(r, "atc").toUpperCase(), s.atc, 10);
            s.notes = pick(v(r, "notes"), s.notes, 1000);
            if (save) { suppliers.save(s); byName.put(key(name), s); }
            out.add(new Row(line, isNew ? "NEW" : "UPDATE", name, (Boolean.TRUE.equals(s.subcon) ? "Subcontractor" : "Supplier")
                    + (s.tin != null ? " · TIN " + s.tin : "") + (s.ewtRate != null && s.ewtRate.signum() > 0 ? " · EWT " + s.ewtRate.stripTrailingZeros().toPlainString() + "%" : "")));
        }
        return new Preview(Kind.SUPPLIERS, out, List.of());
    }

    // ---- materials

    private Preview materials(List<Map<String, String>> rows, LocalDate date, String by) {
        boolean save = by != null;
        Map<String, Material> byCode = new HashMap<>(), byName = new HashMap<>();
        for (Material m : materials.findAll()) { byCode.put(key(m.code), m); byName.put(key(m.name), m); }
        Map<String, MaterialCategory> cats = new HashMap<>();
        for (MaterialCategory c : categories.findAll()) cats.put(key(c.name), c);
        Set<String> newCats = new TreeSet<>();
        List<Row> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BigDecimal stockValue = BigDecimal.ZERO;
        for (Map<String, String> r : rows) {
            int line = Integer.parseInt(r.get("#"));
            String name = v(r, "name"), code = v(r, "code");
            if (name == null && code == null) { out.add(new Row(line, "ERROR", "—", "No name")); continue; }
            Material m = code != null ? byCode.get(key(code)) : null;
            if (m == null && name != null) m = byName.get(key(name));
            String label = (code != null ? code + " · " : "") + (name != null ? name : m != null ? m.name : "");
            if (!seen.add(m != null ? "id" + m.id : key(code != null ? code : name))) { out.add(new Row(line, "SKIP", label, "Already earlier in the file")); continue; }
            boolean isNew = m == null;
            if (isNew && name == null) { out.add(new Row(line, "ERROR", label, "New material needs a name")); continue; }
            if (isNew && code != null && byCode.containsKey(key(code))) { out.add(new Row(line, "ERROR", label, "Code already used")); continue; }
            List<String> problems = new ArrayList<>();
            BigDecimal reorder = num(v(r, "reorder"), problems, "Reorder level");
            BigDecimal qty = num(v(r, "qty"), problems, "Opening qty");
            BigDecimal cost = num(v(r, "cost"), problems, "Unit cost");
            if (qty != null && qty.signum() < 0) problems.add("Opening qty can't be negative");
            if (qty != null && qty.signum() > 0 && (cost == null || cost.signum() <= 0)) problems.add("Opening qty needs a unit cost");
            if (!problems.isEmpty()) { out.add(new Row(line, "ERROR", label, String.join("; ", problems))); continue; }
            List<String> notes = new ArrayList<>();
            boolean stockIn = qty != null && qty.signum() > 0;
            if (stockIn && !isNew && m.onHand.signum() != 0) { stockIn = false; notes.add("has stock already, opening qty ignored (use a stock adjustment)"); }
            if (isNew) { m = new Material(); m.code = code != null ? cut(code, 40) : null; m.name = cut(name, 200); }
            else if (name != null) m.name = cut(name, 200);
            String cat = v(r, "category");
            if (cat != null) {
                MaterialCategory c = cats.get(key(cat));
                if (c == null) {
                    newCats.add(cat);
                    if (save) { c = new MaterialCategory(); c.name = cut(cat, 80); categories.save(c); cats.put(key(cat), c); }
                }
                if (c != null) m.categoryId = c.id;
            }
            if (v(r, "unit") != null) m.unit = cut(v(r, "unit").toLowerCase(), 20);
            if (reorder != null) m.reorderLevel = reorder;
            m.location = pick(v(r, "location"), m.location, 120);
            m.notes = pick(v(r, "notes"), m.notes, 600);
            if (stockIn) { stockValue = stockValue.add(qty.multiply(cost)); notes.add(0, "opening " + qty.stripTrailingZeros().toPlainString() + " " + (m.unit == null ? "" : m.unit) + " @ ₱" + cost.setScale(2, RoundingMode.HALF_UP).toPlainString()); }
            if (save) {
                if (m.code == null) m.code = inventory.nextMaterialCode();
                materials.saveAndFlush(m);
                byCode.put(key(m.code), m); byName.put(key(m.name), m);
                if (stockIn) inventory.move(m.id, qty, cost, "OPENING", date, "OPENING", null, "Opening stock (import)", by);
            }
            out.add(new Row(line, isNew ? "NEW" : "UPDATE", label, String.join("; ", notes)));
        }
        List<String> warn = new ArrayList<>();
        if (!newCats.isEmpty()) warn.add("New categories: " + String.join(", ", newCats) + ".");
        if (stockValue.signum() > 0) warn.add("Opening stock worth ₱" + String.format("%,.2f", stockValue) + ". Afterwards, bring it into the books in Accounting › Opening balances (inventory).");
        return new Preview(Kind.MATERIALS, out, warn);
    }

    // ---- employees

    private Preview employees(List<Map<String, String>> rows, String by) {
        boolean save = by != null;
        Map<String, Employee> byNo = new HashMap<>(), byBio = new HashMap<>(), byName = new HashMap<>();
        for (Employee e : employees.findAll()) {
            if (e.getEmployeeNo() != null) byNo.put(key(e.getEmployeeNo()), e);
            if (e.getBiometricId() != null) byBio.put(key(e.getBiometricId()), e);
            byName.put(key(e.getFullName()), e);
        }
        List<Row> out = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (Map<String, String> r : rows) {
            int line = Integer.parseInt(r.get("#"));
            String no = v(r, "employeeNo"), name = v(r, "name"), bio = v(r, "biometric");
            Employee e = no != null ? byNo.get(key(no)) : null;
            if (e == null && bio != null) e = byBio.get(key(bio));
            if (e == null && name != null) e = byName.get(key(name));
            String label = (no != null ? no + " · " : "") + (name != null ? name : e != null ? e.getFullName() : "—");
            if (!seen.add(e != null ? e.getId() : key(no != null ? no : name == null ? "" : name))) { out.add(new Row(line, "SKIP", label, "Already earlier in the file")); continue; }
            boolean isNew = e == null;
            if (isNew && name == null) { out.add(new Row(line, "ERROR", label, "New employee needs a name")); continue; }
            List<String> problems = new ArrayList<>();
            BigDecimal daily = num(v(r, "daily"), problems, "Daily rate"), monthly = num(v(r, "monthly"), problems, "Monthly rate");
            BigDecimal sss = num(v(r, "sss"), problems, "SSS"), ph = num(v(r, "philhealth"), problems, "PhilHealth"), pi = num(v(r, "pagibig"), problems, "Pag-IBIG");
            LocalDate hired = date(v(r, "hired"), problems, "Date hired"), birth = date(v(r, "birthDate"), problems, "Birth date");
            if (bio != null) {
                Employee other = byBio.get(key(bio));
                if (other != null && (e == null || !other.getId().equals(e.getId()))) problems.add("Biometric ID " + bio + " belongs to " + other.getFullName());
            }
            if (!problems.isEmpty()) { out.add(new Row(line, "ERROR", label, String.join("; ", problems))); continue; }
            if (isNew) { e = new Employee(); e.setFullName(cut(name, 160)); e.setActive(true); }
            boolean linked = e.isLinked();
            if (!linked) {
                if (name != null) e.setFullName(cut(name, 160));
                if (no != null) e.setEmployeeNo(cut(no, 64));
                if (v(r, "position") != null) e.setPosition(cut(v(r, "position"), 160));
                if (v(r, "department") != null) e.setDepartment(cut(v(r, "department"), 120));
                if (hired != null) e.setDateHired(hired);
            }
            if (v(r, "payGroup") != null) e.setPayGroup(cut(v(r, "payGroup"), 80));
            String rt = v(r, "rateType");
            if (rt != null) e.setRateType(rt.toUpperCase().startsWith("M") ? "MONTHLY" : "DAILY");
            else if (monthly != null && monthly.signum() > 0 && (daily == null || daily.signum() == 0)) e.setRateType("MONTHLY");
            if (daily != null) e.setDailyRate(daily.signum() > 0 ? daily : null);
            if (monthly != null) e.setMonthlyRate(monthly.signum() > 0 ? monthly : null);
            if (sss != null) e.setSssAmount(sss.signum() > 0 ? sss : null);
            if (ph != null) e.setPhilhealthAmount(ph.signum() > 0 ? ph : null);
            if (pi != null) e.setPagibigAmount(pi.signum() > 0 ? pi : null);
            String cut = v(r, "cutoff");
            if (cut != null) e.setContributionCutoff(cut.toUpperCase().startsWith("B") ? "BOTH" : cut.toUpperCase().startsWith("F") || cut.contains("26") ? "FIRST" : "SECOND");
            if (bio != null) e.setBiometricId(cut(bio, 32));
            if (v(r, "tin") != null) e.setTin(cut(v(r, "tin"), 40));
            if (v(r, "sssNo") != null) e.setSssNo(cut(v(r, "sssNo"), 40));
            if (v(r, "philhealthNo") != null) e.setPhilhealthNo(cut(v(r, "philhealthNo"), 40));
            if (v(r, "pagibigNo") != null) e.setPagibigNo(cut(v(r, "pagibigNo"), 40));
            if (birth != null) e.setBirthDate(birth);
            if (v(r, "address") != null) e.setAddress(cut(v(r, "address"), 300));
            String tax = v(r, "taxStatus");
            if (tax != null) e.setTaxStatus(tax.toUpperCase().startsWith("M") ? "MWE" : tax.toUpperCase().startsWith("N") || tax.toUpperCase().startsWith("EX") ? "NONE" : "REGULAR");
            if (save) {
                employees.save(e);
                if (e.getEmployeeNo() != null) byNo.put(key(e.getEmployeeNo()), e);
                if (e.getBiometricId() != null) byBio.put(key(e.getBiometricId()), e);
                byName.put(key(e.getFullName()), e);
            }
            String rate = e.isMonthly() ? (e.getMonthlyRate() == null ? "no rate" : "₱" + e.getMonthlyRate().stripTrailingZeros().toPlainString() + "/mo")
                    : (e.getDailyRate() == null ? "no rate" : "₱" + e.getDailyRate().stripTrailingZeros().toPlainString() + "/day");
            out.add(new Row(line, isNew ? "NEW" : "UPDATE", label, rate + (linked ? " · on the org chart (name, position, department kept)" : "")));
        }
        return new Preview(Kind.EMPLOYEES, out, List.of());
    }

    // ---- opening balances

    private Preview opening(List<Map<String, String>> rows, LocalDate date, String by) {
        Map<String, Account> byCode = new HashMap<>();
        for (Account a : accounts.findAll()) byCode.put(a.code, a);
        List<Row> out = new ArrayList<>();
        List<JournalLine> lines = new ArrayList<>();
        BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        List<String> warn = new ArrayList<>();
        for (Map<String, String> r : rows) {
            int line = Integer.parseInt(r.get("#"));
            String code = v(r, "code");
            if (code != null && code.contains(" ")) code = code.substring(0, code.indexOf(' ')); // "1100 Accounts receivable"
            if (code != null && code.endsWith(".0")) code = code.substring(0, code.length() - 2);
            Account a = code == null ? null : byCode.get(code);
            String label = code == null ? "—" : code + (a != null ? " · " + a.name : v(r, "name") != null ? " · " + v(r, "name") : "");
            if (a == null) { out.add(new Row(line, "ERROR", label, code == null ? "No account code" : "No account " + code + " in the chart. Add it first (Accounting › Chart of accounts).")); continue; }
            List<String> problems = new ArrayList<>();
            BigDecimal d = num(v(r, "debit"), problems, "Debit"), c = num(v(r, "credit"), problems, "Credit"), amt = num(v(r, "amount"), problems, "Amount");
            // A single "Amount / Balance" column is in the account's normal direction (negative = the other side).
            if (d == null && c == null && amt != null) { if (a.isDebitNormal() == (amt.signum() >= 0)) d = amt.abs(); else c = amt.abs(); }
            d = d == null ? BigDecimal.ZERO : d; c = c == null ? BigDecimal.ZERO : c;
            if (d.signum() < 0) { c = c.add(d.negate()); d = BigDecimal.ZERO; }
            if (c.signum() < 0) { d = d.add(c.negate()); c = BigDecimal.ZERO; }
            BigDecimal net = d.subtract(c);
            if (!problems.isEmpty()) { out.add(new Row(line, "ERROR", label, String.join("; ", problems))); continue; }
            if (net.signum() == 0) { out.add(new Row(line, "SKIP", label, "Zero balance")); continue; }
            if (!seen.add(code)) { out.add(new Row(line, "ERROR", label, "Account appears twice")); continue; }
            if (Acct.OPENING_BALANCE_EQUITY.equals(code)) { out.add(new Row(line, "SKIP", label, "Worked out automatically")); continue; }
            List<String> notes = new ArrayList<>();
            if (Acct.INVENTORY.equals(code)) notes.add("tip: inventory is better brought in from the stock on hand (Opening balances › inventory)");
            if ("INCOME".equals(a.type) || "EXPENSE".equals(a.type)) notes.add("income/expense account: only if starting mid-year");
            BigDecimal amount = Ledger.r2(net.abs());
            lines.add(net.signum() > 0 ? JournalLine.dr(a.id, amount, "Opening balance") : JournalLine.cr(a.id, amount, "Opening balance"));
            if (net.signum() > 0) dr = dr.add(amount); else cr = cr.add(amount);
            out.add(new Row(line, "NEW", label, (net.signum() > 0 ? "Dr ₱" : "Cr ₱") + String.format("%,.2f", amount) + (notes.isEmpty() ? "" : " · " + String.join("; ", notes))));
        }
        BigDecimal diff = dr.subtract(cr);
        warn.add(String.format("Debits ₱%,.2f, credits ₱%,.2f.", dr, cr) + (diff.signum() == 0 ? " Balanced." : String.format(" The difference of ₱%,.2f goes to Opening Balance Equity (3900).", diff.abs())));
        Integer existing = jdbc.queryForObject("select count(*) from acc_journal where source = 'OPENING' and status = 'POSTED' and (source_ref is null or source_ref = 'TRIAL')", Integer.class);
        if (existing != null && existing > 0) warn.add("Opening balances from a trial balance were posted before. Posting again adds to them; void the earlier entry in the Journal first if this replaces it.");
        if (by != null && !lines.isEmpty()) {
            Long obe = ledger.id(Acct.OPENING_BALANCE_EQUITY);
            if (diff.signum() > 0) lines.add(JournalLine.cr(obe, diff, "Opening balance"));
            if (diff.signum() < 0) lines.add(JournalLine.dr(obe, diff.negate(), "Opening balance"));
            JournalEntry e = ledger.post(date, "Opening balances (trial balance import)", "OPENING", null, "TRIAL", lines, by);
            warn.add(0, "Posted as " + e.number + ".");
        }
        return new Preview(Kind.OPENING, out, warn);
    }

    // ---------------------------------------------------------------- helpers

    static String v(Map<String, String> r, String k) { String s = r.get(k); return s == null || s.isBlank() ? null : s.trim(); }
    static String key(String s) { return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " "); }
    static String cut(String s, int n) { if (s == null) return null; s = s.trim(); return s.length() > n ? s.substring(0, n) : s; }
    static String pick(String v, String old, int n) { return v == null ? old : cut(v, n); }
    static boolean yes(String s) { String x = s.trim().toLowerCase(); return x.startsWith("y") || x.equals("true") || x.equals("1") || x.equals("vat") || x.equals("x") || x.equals("✓"); }

    static BigDecimal num(String s, List<String> problems, String what) {
        if (s == null) return null;
        String x = s.replace("₱", "").replace("PHP", "").replace(",", "").replace("%", "").trim();
        boolean neg = x.startsWith("(") && x.endsWith(")");
        if (neg) x = x.substring(1, x.length() - 1);
        if (x.isEmpty() || x.equals("-")) return null;
        try { BigDecimal v = new BigDecimal(x); return neg ? v.negate() : v; }
        catch (NumberFormatException e) { problems.add(what + " \"" + s + "\" is not a number"); return null; }
    }

    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("M/d/uu"), DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH), DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH), DateTimeFormatter.ofPattern("uuuu/M/d"));

    static LocalDate date(String s, List<String> problems, String what) {
        if (s == null) return null;
        for (DateTimeFormatter f : DATES) { try { return LocalDate.parse(s.trim(), f); } catch (RuntimeException ignore) { } }
        problems.add(what + " \"" + s + "\" is not a date (use YYYY-MM-DD or MM/DD/YYYY)");
        return null;
    }
}
