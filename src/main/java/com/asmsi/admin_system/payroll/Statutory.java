package com.asmsi.admin_system.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Philippine statutory rates used by payroll (rates in force from January 2025).
 * <ul>
 *   <li><b>SSS</b> (RA 11199): 15% of the monthly salary credit (MSC ₱5,000 – ₱35,000, in ₱500 steps):
 *       employee 5%, employer 10%, plus employer EC ₱10 (MSC below ₱15,000) or ₱30.</li>
 *   <li><b>PhilHealth</b>: 5% of the monthly basic salary (floor ₱10,000, ceiling ₱100,000), shared equally.</li>
 *   <li><b>Pag-IBIG</b>: 2% each of the monthly compensation up to ₱10,000 (employee 1% when ₱1,500 or less).</li>
 *   <li><b>Withholding tax on compensation</b> (TRAIN, 2023 onward): the annual table below, applied per
 *       pay period by annualising the period's taxable pay.</li>
 * </ul>
 * When the rates change, update this class: everything else reads from here.
 */
public final class Statutory {
    private Statutory() {}

    static final BigDecimal ZERO = BigDecimal.ZERO;
    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final BigDecimal SSS_EE = new BigDecimal("0.05"), SSS_ER = new BigDecimal("0.10");
    public static final BigDecimal SSS_MSC_MIN = BigDecimal.valueOf(5000), SSS_MSC_MAX = BigDecimal.valueOf(35000);
    public static final BigDecimal EC_LOW = BigDecimal.TEN, EC_HIGH = BigDecimal.valueOf(30), EC_THRESHOLD = BigDecimal.valueOf(15000);
    public static final BigDecimal PH_RATE = new BigDecimal("0.05"), PH_FLOOR = BigDecimal.valueOf(10000), PH_CEILING = BigDecimal.valueOf(100000);
    public static final BigDecimal PI_RATE = new BigDecimal("0.02"), PI_LOW_RATE = new BigDecimal("0.01"), PI_CAP = BigDecimal.valueOf(10000), PI_LOW = BigDecimal.valueOf(1500);
    /** Yearly ceiling of non-taxable 13th month pay and other benefits. */
    public static final BigDecimal BENEFITS_CEILING = BigDecimal.valueOf(90000);

    /** Employee and employer share of one contribution (employer share includes EC for SSS). */
    public record Share(BigDecimal ee, BigDecimal er, BigDecimal ec) {
        public BigDecimal getEmployerTotal() { return er.add(ec); }
    }

    // ---------------------------------------------------------------- contribution tables (monthly)

    /** SSS monthly salary credit for a monthly compensation. */
    public static BigDecimal sssMsc(BigDecimal monthly) {
        if (monthly == null || monthly.signum() <= 0) return ZERO;
        BigDecimal msc = monthly.divide(BigDecimal.valueOf(500), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(500));
        return msc.max(SSS_MSC_MIN).min(SSS_MSC_MAX);
    }

    public static Share sss(BigDecimal monthly) {
        BigDecimal msc = sssMsc(monthly);
        if (msc.signum() == 0) return new Share(ZERO, ZERO, ZERO);
        return new Share(m(msc.multiply(SSS_EE)), m(msc.multiply(SSS_ER)), msc.compareTo(EC_THRESHOLD) < 0 ? EC_LOW : EC_HIGH);
    }

    public static Share philhealth(BigDecimal monthly) {
        if (monthly == null || monthly.signum() <= 0) return new Share(ZERO, ZERO, ZERO);
        BigDecimal base = monthly.max(PH_FLOOR).min(PH_CEILING);
        BigDecimal half = m(base.multiply(PH_RATE).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP));
        return new Share(half, half, ZERO);
    }

    public static Share pagibig(BigDecimal monthly) {
        if (monthly == null || monthly.signum() <= 0) return new Share(ZERO, ZERO, ZERO);
        BigDecimal base = monthly.min(PI_CAP);
        BigDecimal ee = m(base.multiply(monthly.compareTo(PI_LOW) <= 0 ? PI_LOW_RATE : PI_RATE));
        return new Share(ee, m(base.multiply(PI_RATE)), ZERO);
    }

    /**
     * Employer share that goes with an employee deduction actually taken (which may have been typed in
     * by hand). {@code fraction} is the part of the month this cut-off carries: 1, or 0.5 when the
     * contribution is split over both cut-offs.
     */
    public static Share employerFor(String kind, BigDecimal ee, BigDecimal fraction) {
        if (ee == null || ee.signum() <= 0) return new Share(ZERO, ZERO, ZERO);
        if (fraction == null || fraction.signum() <= 0) fraction = BigDecimal.ONE;
        switch (kind) {
            case "SSS": {
                BigDecimal mscPart = ee.divide(SSS_EE, 6, RoundingMode.HALF_UP);
                BigDecimal fullMsc = mscPart.divide(fraction, 6, RoundingMode.HALF_UP);
                BigDecimal ec = (fullMsc.compareTo(EC_THRESHOLD) < 0 ? EC_LOW : EC_HIGH).multiply(fraction);
                return new Share(ee, m(mscPart.multiply(SSS_ER)), m(ec));
            }
            case "PHILHEALTH": return new Share(ee, ee, ZERO);
            case "PAGIBIG": {
                // 1% employee rate only applies at ₱1,500 a month or less (₱15); the employer always pays 2%.
                BigDecimal fullEe = ee.divide(fraction, 6, RoundingMode.HALF_UP);
                return new Share(ee, fullEe.compareTo(new BigDecimal("15")) <= 0 ? m(ee.multiply(BigDecimal.valueOf(2))) : ee, ZERO);
            }
            default: return new Share(ee, ZERO, ZERO);
        }
    }

    // ---------------------------------------------------------------- withholding tax on compensation

    /** Annual income tax on compensation (TRAIN table, 2023 onward). */
    public static BigDecimal annualTax(BigDecimal taxable) {
        if (taxable == null || taxable.signum() <= 0) return ZERO;
        double t = taxable.doubleValue();
        double tax;
        if (t <= 250_000) tax = 0;
        else if (t <= 400_000) tax = (t - 250_000) * 0.15;
        else if (t <= 800_000) tax = 22_500 + (t - 400_000) * 0.20;
        else if (t <= 2_000_000) tax = 102_500 + (t - 800_000) * 0.25;
        else if (t <= 8_000_000) tax = 402_500 + (t - 2_000_000) * 0.30;
        else tax = 2_202_500 + (t - 8_000_000) * 0.35;
        return m(BigDecimal.valueOf(tax));
    }

    /** Tax to withhold on one pay period's taxable pay, with {@code periodsPerYear} periods (24 = semi-monthly). */
    public static BigDecimal periodTax(BigDecimal taxable, int periodsPerYear) {
        if (taxable == null || taxable.signum() <= 0) return ZERO;
        BigDecimal n = BigDecimal.valueOf(periodsPerYear);
        return m(annualTax(taxable.multiply(n)).divide(n, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal m(BigDecimal v) { return (v == null ? ZERO : v).setScale(2, RoundingMode.HALF_UP); }
}
