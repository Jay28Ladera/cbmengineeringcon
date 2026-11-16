package com.asmsi.admin_system.payroll;

/** Kinds of extra pay-sheet lines. Earnings add to gross pay; the rest are deductions. */
public enum PayItem {
    INCENTIVE("Incentives", true),
    MIDYEAR_BONUS("Midyear bonus", true),
    BONUS("Bonus", true),
    THIRTEENTH_MONTH("13th month", true),
    COMMISSION("Commission", true),
    OTHER_EARNING("Other earning", true),
    LOAN("Loan", false),
    CASH_ADVANCE("Cash advance", false),
    SINKING("Sinking fund", false),
    SSS("SSS", false),
    PHILHEALTH("PhilHealth", false),
    PAGIBIG("Pag-IBIG", false),
    WTAX("Withholding tax", false),
    OTHER_DEDUCTION("Other deduction", false);

    private final String label;
    private final boolean earning;

    PayItem(String label, boolean earning) { this.label = label; this.earning = earning; }

    public String getLabel() { return label; }
    public boolean isEarning() { return earning; }
    public String getKey() { return name(); }
    public boolean isGovernment() { return this == SSS || this == PHILHEALTH || this == PAGIBIG; }
    /** Earnings that are not taxable up to the ₱90,000 yearly ceiling (13th month and other benefits). */
    public boolean isBenefit() { return this == THIRTEENTH_MONTH || this == MIDYEAR_BONUS || this == BONUS; }
    /** Kinds a loan / advance schedule can post. */
    public boolean isPlanKind() { return this == LOAN || this == CASH_ADVANCE || this == SINKING || this == OTHER_DEDUCTION; }

    public static PayItem parse(String v) {
        if (v == null) return null;
        try { return valueOf(v.trim().toUpperCase()); } catch (IllegalArgumentException e) { return null; }
    }
}
