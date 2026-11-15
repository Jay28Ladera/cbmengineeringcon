package com.asmsi.admin_system.payroll;

import com.asmsi.admin_system.entity.PayrollPeriod;
import com.asmsi.admin_system.entity.PayrollSettings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Pay sheet as an Excel file, laid out like the PAYLIST sheet HR used before. */
@Component
public class PayrollExcelExporter {

    private record Col(String header, Function<PayLine, Object> value, boolean money) {}

    public byte[] export(PayrollPeriod p, List<PayLine> lines, PayrollSettings s) throws Exception {
        List<Col> cols = List.of(
                new Col("NAME OF EMPLOYEE", l -> l.name, false),
                new Col("POSITION", l -> l.position, false),
                new Col("STATUS", l -> l.statusCode, false),
                new Col("RATE/DAY", l -> l.dailyRate, true),
                new Col("RATE/HR", l -> l.hourlyRate, true),
                new Col("# DAYS", l -> l.regDays, false),
                new Col("REG", l -> l.basicPay, true),
                new Col("# OT", l -> l.otHours, false),
                new Col("O.T. REG.", l -> l.otPay, true),
                new Col("# SUN", l -> l.sunDays, false),
                new Col("SUN", l -> l.sunPay, true),
                new Col("# SUN-OT", l -> l.sunOtHours, false),
                new Col("SUN-OT", l -> l.sunOtPay, true),
                new Col("# HOL 30%", l -> l.specialHolDays, false),
                new Col("HOLIDAY 30%", l -> l.specialHolPay, true),
                new Col("# HOL 2X", l -> l.regularHolDays, false),
                new Col("HOLIDAY DOUBLE", l -> l.regularHolPay, true),
                new Col("INCENTIVES", l -> l.amount("INCENTIVE"), true),
                new Col("MIDYEAR BONUS", l -> l.amount("MIDYEAR_BONUS"), true),
                new Col("BONUS", l -> l.amount("BONUS"), true),
                new Col("13TH MONTH", l -> l.amount("THIRTEENTH_MONTH"), true),
                new Col("# ND HRS", l -> l.ndHours, false),
                new Col("NIGHT DIFF", l -> l.ndPay, true),
                new Col("COMMISSION / OTHER", l -> l.amount("COMMISSION").add(l.amount("OTHER_EARNING")), true),
                new Col("TOTAL PAY", l -> l.grossPay, true),
                new Col("# LATE MIN", l -> l.lateMinutes, false),
                new Col("LATES", l -> l.lateAmount, true),
                new Col("# ABSENT", l -> l.absentDays, false),
                new Col("ABSENT", l -> l.absentAmount, true),
                new Col("LOAN", l -> l.amount("LOAN"), true),
                new Col("CASH ADVANCE", l -> l.amount("CASH_ADVANCE"), true),
                new Col("SINKING", l -> l.amount("SINKING"), true),
                new Col("SSS", l -> l.amount("SSS"), true),
                new Col("PHILHEALTH", l -> l.amount("PHILHEALTH"), true),
                new Col("PAG-IBIG", l -> l.amount("PAGIBIG"), true),
                new Col("OTHERS", l -> l.amount("OTHER_DEDUCTION"), true),
                new Col("TOTAL DEDUCTIONS", l -> l.totalDeductions, true),
                new Col("NET PAY", l -> l.netPay, true),
                new Col("REMARKS", l -> l.remarks, false));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("PAYLIST");
            DataFormat fmt = wb.createDataFormat();
            Font bold = wb.createFont(); bold.setBold(true);
            Font title = wb.createFont(); title.setBold(true); title.setFontHeightInPoints((short) 14);

            CellStyle titleSt = wb.createCellStyle(); titleSt.setFont(title);
            CellStyle head = wb.createCellStyle(); head.setFont(bold); head.setWrapText(true);
            head.setAlignment(HorizontalAlignment.CENTER); head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(head);
            CellStyle moneySt = wb.createCellStyle(); moneySt.setDataFormat(fmt.getFormat("#,##0.00;-#,##0.00;\"-\""));  border(moneySt);
            CellStyle numSt = wb.createCellStyle(); numSt.setDataFormat(fmt.getFormat("0.##;-0.##;\"\"")); border(numSt);
            CellStyle textSt = wb.createCellStyle(); border(textSt);
            CellStyle groupSt = wb.createCellStyle(); groupSt.setFont(bold);
            groupSt.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); groupSt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle totalSt = wb.createCellStyle(); totalSt.cloneStyleFrom(moneySt); totalSt.setFont(bold);
            CellStyle totalLabel = wb.createCellStyle(); totalLabel.cloneStyleFrom(textSt); totalLabel.setFont(bold);

            int r = 0;
            Row t = sh.createRow(r++); cell(t, 0, s.getCompanyName() + " — PAYROLL", titleSt);
            Row t2 = sh.createRow(r++); cell(t2, 0, "DATE COVERED: " + p.getLabel() + (p.isLocked() ? "   (LOCKED)" : "   (DRAFT — not locked)"), null);
            r++;
            Row h = sh.createRow(r++); h.setHeightInPoints(32);
            cell(h, 0, "#", head);
            for (int i = 0; i < cols.size(); i++) cell(h, i + 1, cols.get(i).header(), head);
            int headerRow = r - 1;

            Map<String, List<PayLine>> groups = new LinkedHashMap<>();
            for (PayLine l : lines) groups.computeIfAbsent(l.payGroup == null || l.payGroup.isBlank() ? "No group" : l.payGroup, k -> new ArrayList<>()).add(l);
            List<Integer> subtotalRows = new ArrayList<>();
            for (var g : groups.entrySet()) {
                Row gr = sh.createRow(r++);
                cell(gr, 0, g.getKey().toUpperCase(), groupSt);
                for (int i = 1; i <= cols.size(); i++) cell(gr, i, null, groupSt);
                int first = r;
                int n = 1;
                for (PayLine l : g.getValue()) {
                    Row row = sh.createRow(r++);
                    cell(row, 0, n++, numSt);
                    for (int i = 0; i < cols.size(); i++) {
                        Col c = cols.get(i);
                        cell(row, i + 1, c.value().apply(l), c.money() ? moneySt : (c.value().apply(l) instanceof String || c.value().apply(l) == null ? textSt : numSt));
                    }
                }
                int last = r - 1;
                Row st = sh.createRow(r++);
                cell(st, 1, "Subtotal — " + g.getKey(), totalLabel);
                for (int i = 0; i < cols.size(); i++) if (cols.get(i).money() && !cols.get(i).header().startsWith("RATE")) {
                    String col = org.apache.poi.ss.util.CellReference.convertNumToColString(i + 1);
                    st.createCell(i + 1).setCellFormula("SUM(" + col + (first + 1) + ":" + col + (last + 1) + ")");
                    st.getCell(i + 1).setCellStyle(totalSt);
                }
                subtotalRows.add(r); // r is now the 1-based Excel row number of the subtotal
                r++;
            }
            Row gt = sh.createRow(r);
            cell(gt, 1, "GRAND TOTAL", totalLabel);
            for (int i = 0; i < cols.size(); i++) if (cols.get(i).money() && !cols.get(i).header().startsWith("RATE")) {
                String col = org.apache.poi.ss.util.CellReference.convertNumToColString(i + 1);
                StringBuilder f = new StringBuilder();
                for (int sr : subtotalRows) f.append(f.length() == 0 ? "" : ",").append(col).append(sr); // sr = 1-based Excel row of the subtotal
                gt.createCell(i + 1).setCellFormula(f.length() == 0 ? "0" : "SUM(" + f + ")");
                gt.getCell(i + 1).setCellStyle(totalSt);
            }

            sh.setColumnWidth(0, 5 * 256);
            sh.setColumnWidth(1, 30 * 256);
            sh.setColumnWidth(2, 22 * 256);
            for (int i = 3; i <= cols.size(); i++) sh.setColumnWidth(i, 12 * 256);
            sh.setColumnWidth(cols.size(), 30 * 256);
            sh.createFreezePane(2, headerRow + 1);
            sh.setAutoFilter(new CellRangeAddress(headerRow, headerRow, 0, cols.size()));

            // Second sheet: one line per extra earning / deduction, with descriptions
            Sheet det = wb.createSheet("DETAILS");
            int dr = 0;
            Row dh = det.createRow(dr++);
            String[] dhs = {"NAME OF EMPLOYEE", "TYPE", "EARNING / DEDUCTION", "DESCRIPTION", "AMOUNT"};
            for (int i = 0; i < dhs.length; i++) cell(dh, i, dhs[i], head);
            for (PayLine l : lines) {
                for (PayLine.Item it : l.earnings) dr = detail(det, dr, l, it, "Earning", textSt, moneySt);
                for (PayLine.Item it : l.deductions) dr = detail(det, dr, l, it, "Deduction", textSt, moneySt);
            }
            det.setColumnWidth(0, 30 * 256); det.setColumnWidth(1, 18 * 256); det.setColumnWidth(2, 14 * 256);
            det.setColumnWidth(3, 40 * 256); det.setColumnWidth(4, 14 * 256);

            wb.setForceFormulaRecalculation(true);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int detail(Sheet sh, int r, PayLine l, PayLine.Item it, String type, CellStyle text, CellStyle money) {
        Row row = sh.createRow(r);
        cell(row, 0, l.name, text); cell(row, 1, it.label, text); cell(row, 2, type, text);
        cell(row, 3, it.description, text); cell(row, 4, it.amount, money);
        return r + 1;
    }

    private static void border(CellStyle st) {
        st.setBorderBottom(BorderStyle.THIN); st.setBorderTop(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN); st.setBorderRight(BorderStyle.THIN);
    }

    private static void cell(Row row, int col, Object v, CellStyle st) {
        Cell c = row.createCell(col);
        if (v instanceof BigDecimal b) c.setCellValue(b.doubleValue());
        else if (v instanceof Number n) c.setCellValue(n.doubleValue());
        else if (v != null) c.setCellValue(v.toString());
        if (st != null) c.setCellStyle(st);
    }
}
