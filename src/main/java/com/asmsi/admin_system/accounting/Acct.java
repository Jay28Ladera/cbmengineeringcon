package com.asmsi.admin_system.accounting;

/** Codes of the accounts that automatic postings use. They are created on first start (see ChartSetup). */
public final class Acct {
    private Acct() {}
    public static final String CASH_ON_HAND = "1010";
    public static final String PETTY_CASH = "1020";
    public static final String CASH_IN_BANK = "1030";
    public static final String AR_CLIENTS = "1100";
    public static final String RETENTION_RECEIVABLE = "1110";
    public static final String INVENTORY = "1200";
    public static final String INPUT_VAT = "1300";
    public static final String CWT = "1310";
    public static final String ADVANCES_SUPPLIERS = "1400";
    public static final String ADVANCES_EMPLOYEES = "1410";
    public static final String TOOLS_EQUIPMENT = "1500";

    public static final String AP_SUPPLIERS = "2010";
    public static final String AP_SUBCONTRACTORS = "2020";
    public static final String RETENTION_PAYABLE = "2030";
    public static final String GRNI = "2040";
    public static final String OUTPUT_VAT = "2100";
    public static final String EWT_PAYABLE = "2110";
    public static final String WTC_PAYABLE = "2120";
    public static final String SSS_PAYABLE = "2130";
    public static final String PHILHEALTH_PAYABLE = "2140";
    public static final String PAGIBIG_PAYABLE = "2150";
    public static final String SALARIES_PAYABLE = "2160";
    public static final String SINKING_FUND = "2170";
    public static final String SALARIES_OFFICE = "6010";
    public static final String EMPLOYER_CONTRIBUTIONS = "6020";
    public static final String DEPRECIATION = "6140";
    public static final String ACCUMULATED_DEPRECIATION = "1510";
    public static final String GAIN_ON_DISPOSAL = "4095";
    public static final String LOSS_ON_DISPOSAL = "6160";
    public static final String DEPRECIATION_JOBS = "5050";
    public static final String CUSTOMER_DEPOSITS = "2200";

    public static final String OWNERS_CAPITAL = "3010";
    public static final String OWNERS_DRAWINGS = "3020";
    public static final String RETAINED_EARNINGS = "3100";
    public static final String OPENING_BALANCE_EQUITY = "3900";

    public static final String CONTRACT_REVENUE = "4010";
    public static final String SALES_SUPPLIES = "4020";
    public static final String OTHER_INCOME = "4090";

    public static final String MATERIALS_USED = "5010";
    public static final String DIRECT_LABOR = "5020";
    public static final String SUBCONTRACT_COSTS = "5030";

    public static final String INVENTORY_LOSSES = "6150";
    public static final String MISC_EXPENSE = "6190";
}
