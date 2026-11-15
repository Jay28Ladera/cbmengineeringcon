package com.asmsi.admin_system.payroll;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Computed pay of one employee for one period: one row of the pay sheet and one payslip.
 * Plain public fields so the line can be shown in templates and saved as JSON when a period is locked.
 */
public class PayLine {

    /** An extra earning or deduction line. */
    public static class Item {
        public String kind;
        public String label;
        public String description;
        public BigDecimal amount = BigDecimal.ZERO;
        /** Id of the pay-sheet entry, so it can be removed; null for automatic lines. */
        public Long adjustmentId;
        /** True for lines added automatically (standard contributions, loan schedules). */
        public boolean auto;
        /** Loan / advance schedule this installment comes from, if any. */
        public Long planId;

        public Item() {}
        public Item(String kind, String label, String description, BigDecimal amount, Long adjustmentId, boolean auto) {
            this.kind = kind; this.label = label; this.description = description; this.amount = amount;
            this.adjustmentId = adjustmentId; this.auto = auto;
        }
    }

    public Long employeeId;
    public String employeeNo;
    public String name;
    public String position;
    public String payGroup;
    public String statusCode;
    public String rateType;

    public BigDecimal dailyRate = BigDecimal.ZERO;
    public BigDecimal hourlyRate = BigDecimal.ZERO;
    public BigDecimal monthlyRate;

    public BigDecimal regDays = BigDecimal.ZERO;
    public BigDecimal basicPay = BigDecimal.ZERO;
    public BigDecimal otHours = BigDecimal.ZERO;
    public BigDecimal otPay = BigDecimal.ZERO;
    public BigDecimal sunDays = BigDecimal.ZERO;
    public BigDecimal sunPay = BigDecimal.ZERO;
    public BigDecimal sunOtHours = BigDecimal.ZERO;
    public BigDecimal sunOtPay = BigDecimal.ZERO;
    public BigDecimal specialHolDays = BigDecimal.ZERO;
    public BigDecimal specialHolPay = BigDecimal.ZERO;
    public BigDecimal regularHolDays = BigDecimal.ZERO;
    public BigDecimal regularHolPay = BigDecimal.ZERO;
    public BigDecimal ndHours = BigDecimal.ZERO;
    public BigDecimal ndPay = BigDecimal.ZERO;
    public List<Item> earnings = new ArrayList<>();
    public BigDecimal otherEarnings = BigDecimal.ZERO;
    public BigDecimal grossPay = BigDecimal.ZERO;

    public int lateMinutes;
    public BigDecimal lateAmount = BigDecimal.ZERO;
    public BigDecimal absentDays = BigDecimal.ZERO;
    public BigDecimal absentAmount = BigDecimal.ZERO;
    public List<Item> deductions = new ArrayList<>();
    public BigDecimal totalDeductions = BigDecimal.ZERO;
    public BigDecimal netPay = BigDecimal.ZERO;

    public String remarks;
    public List<String> warnings = new ArrayList<>();

    /** Sum of extra lines of one kind, e.g. amount('LOAN'). */
    public BigDecimal amount(String kind) {
        BigDecimal t = BigDecimal.ZERO;
        for (Item i : earnings) if (kind.equals(i.kind)) t = t.add(i.amount);
        for (Item i : deductions) if (kind.equals(i.kind)) t = t.add(i.amount);
        return t;
    }

    /** Sum of deduction lines whose kind is in the list (e.g. government contributions). */
    public BigDecimal deductionSum(String... kinds) {
        BigDecimal t = BigDecimal.ZERO;
        for (Item i : deductions) for (String k : kinds) if (k.equals(i.kind)) t = t.add(i.amount);
        return t;
    }

    public boolean hasActivity() {
        return grossPay.signum() != 0 || totalDeductions.signum() != 0 || regDays.signum() != 0;
    }
}
