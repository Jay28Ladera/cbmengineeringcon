package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Pay rules (single row). Defaults match the formulas of the Excel pay sheet. */
@Entity
@Table(name = "hr_payroll_settings")
public class PayrollSettings {
    @Id private Long id = 1L;
    @Column(name = "ot_multiplier", precision = 6, scale = 3) private BigDecimal otMultiplier = new BigDecimal("1.25");
    @Column(name = "sunday_multiplier", precision = 6, scale = 3) private BigDecimal sundayMultiplier = new BigDecimal("1.30");
    @Column(name = "sunday_ot_multiplier", precision = 6, scale = 3) private BigDecimal sundayOtMultiplier = new BigDecimal("1.30");
    @Column(name = "special_holiday_premium", precision = 6, scale = 3) private BigDecimal specialHolidayPremium = new BigDecimal("0.30");
    @Column(name = "regular_holiday_premium", precision = 6, scale = 3) private BigDecimal regularHolidayPremium = new BigDecimal("1.00");
    @Column(name = "night_diff_rate", precision = 6, scale = 3) private BigDecimal nightDiffRate = new BigDecimal("0.10");
    @Column(name = "late_multiplier", precision = 6, scale = 3) private BigDecimal lateMultiplier = new BigDecimal("1.30");
    @Column(name = "late_grace_minutes") private Integer lateGraceMinutes = 0;
    @Column(name = "hours_per_day") private Integer hoursPerDay = 8;
    /** Used to work out a daily rate from a monthly rate when no daily rate is set (monthly × 12 ÷ days). */
    @Column(name = "work_days_per_year") private Integer workDaysPerYear = 313;
    // Work schedule used to turn biometric punches into REG / OT / late
    @Column(name = "shift_start") private java.time.LocalTime shiftStart = java.time.LocalTime.of(8, 0);
    @Column(name = "shift_end") private java.time.LocalTime shiftEnd = java.time.LocalTime.of(17, 0);
    @Column(name = "break_start") private java.time.LocalTime breakStart = java.time.LocalTime.of(12, 0);
    @Column(name = "break_end") private java.time.LocalTime breakEnd = java.time.LocalTime.of(13, 0);
    /** Overtime after the shift counts in whole blocks of this many minutes (e.g. 30). */
    @Column(name = "ot_block_minutes") private Integer otBlockMinutes = 30;
    /** Whether time after the shift becomes OT automatically when logs are applied. */
    @Column(name = "ot_from_logs") private Boolean otFromLogs = Boolean.TRUE;
    @Column(name = "company_name", length = 160) private String companyName = "CBM Engineering & Construction";

    public Long getId() { return id; }
    public BigDecimal getOtMultiplier() { return otMultiplier; }
    public void setOtMultiplier(BigDecimal v) { otMultiplier = v; }
    public BigDecimal getSundayMultiplier() { return sundayMultiplier; }
    public void setSundayMultiplier(BigDecimal v) { sundayMultiplier = v; }
    public BigDecimal getSundayOtMultiplier() { return sundayOtMultiplier; }
    public void setSundayOtMultiplier(BigDecimal v) { sundayOtMultiplier = v; }
    public BigDecimal getSpecialHolidayPremium() { return specialHolidayPremium; }
    public void setSpecialHolidayPremium(BigDecimal v) { specialHolidayPremium = v; }
    public BigDecimal getRegularHolidayPremium() { return regularHolidayPremium; }
    public void setRegularHolidayPremium(BigDecimal v) { regularHolidayPremium = v; }
    public BigDecimal getNightDiffRate() { return nightDiffRate; }
    public void setNightDiffRate(BigDecimal v) { nightDiffRate = v; }
    public BigDecimal getLateMultiplier() { return lateMultiplier; }
    public void setLateMultiplier(BigDecimal v) { lateMultiplier = v; }
    public Integer getLateGraceMinutes() { return lateGraceMinutes; }
    public void setLateGraceMinutes(Integer v) { lateGraceMinutes = v; }
    public Integer getHoursPerDay() { return hoursPerDay; }
    public void setHoursPerDay(Integer v) { hoursPerDay = v; }
    public Integer getWorkDaysPerYear() { return workDaysPerYear; }
    public void setWorkDaysPerYear(Integer v) { workDaysPerYear = v; }
    public java.time.LocalTime getShiftStart() { return shiftStart == null ? java.time.LocalTime.of(8, 0) : shiftStart; }
    public void setShiftStart(java.time.LocalTime v) { shiftStart = v; }
    public java.time.LocalTime getShiftEnd() { return shiftEnd == null ? java.time.LocalTime.of(17, 0) : shiftEnd; }
    public void setShiftEnd(java.time.LocalTime v) { shiftEnd = v; }
    public java.time.LocalTime getBreakStart() { return breakStart == null ? java.time.LocalTime.of(12, 0) : breakStart; }
    public void setBreakStart(java.time.LocalTime v) { breakStart = v; }
    public java.time.LocalTime getBreakEnd() { return breakEnd == null ? java.time.LocalTime.of(13, 0) : breakEnd; }
    public void setBreakEnd(java.time.LocalTime v) { breakEnd = v; }
    public int getOtBlockMinutes() { return otBlockMinutes == null || otBlockMinutes < 1 ? 30 : otBlockMinutes; }
    public void setOtBlockMinutes(Integer v) { otBlockMinutes = v; }
    public boolean isOtFromLogs() { return otFromLogs == null || otFromLogs; }
    public void setOtFromLogs(Boolean v) { otFromLogs = v; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String v) { companyName = v; }
}
