package com.asmsi.admin_system.accounting;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the starting chart of accounts (a Philippine engineering/construction and aircon supply business) and the
 * Cash on Hand money account, the first time the app starts. Accounts already there are left alone, so this is safe
 * on every start and the accountant can rename or add accounts freely.
 */
@Component
public class ChartSetup implements ApplicationRunner {

    private final AccountRepository accounts;
    private final BankAccountRepository banks;

    public ChartSetup(AccountRepository accounts, BankAccountRepository banks) { this.accounts = accounts; this.banks = banks; }

    private static final String[][] CHART = {
        // code, name, type, group, system(1/0)
        {"1010", "Cash on Hand", "ASSET", "Cash", "1"},
        {"1020", "Petty Cash Fund", "ASSET", "Cash", "1"},
        {"1030", "Cash in Bank", "ASSET", "Cash", "1"},
        {"1100", "Accounts Receivable - Clients", "ASSET", "Receivables", "1"},
        {"1110", "Retention Receivable", "ASSET", "Receivables", "1"},
        {"1200", "Inventory - Materials", "ASSET", "Inventory", "1"},
        {"1300", "Input VAT", "ASSET", "Other current assets", "1"},
        {"1310", "Creditable Withholding Tax", "ASSET", "Other current assets", "1"},
        {"1400", "Advances to Suppliers", "ASSET", "Other current assets", "1"},
        {"1410", "Advances to Employees", "ASSET", "Other current assets", "1"},
        {"1500", "Tools and Equipment", "ASSET", "Property and equipment", "1"},
        {"1510", "Accumulated Depreciation", "ASSET", "Property and equipment", "0"},
        {"1520", "Service Vehicles", "ASSET", "Property and equipment", "0"},
        {"2010", "Accounts Payable - Suppliers", "LIABILITY", "Payables", "1"},
        {"2020", "Accounts Payable - Subcontractors", "LIABILITY", "Payables", "1"},
        {"2030", "Retention Payable", "LIABILITY", "Payables", "1"},
        {"2040", "Goods Received, Not Yet Billed", "LIABILITY", "Payables", "1"},
        {"2100", "Output VAT", "LIABILITY", "Taxes payable", "1"},
        {"2110", "Withholding Tax Payable - Expanded", "LIABILITY", "Taxes payable", "1"},
        {"2120", "Withholding Tax Payable - Compensation", "LIABILITY", "Taxes payable", "1"},
        {"2130", "SSS Contributions Payable", "LIABILITY", "Government contributions", "1"},
        {"2140", "PhilHealth Contributions Payable", "LIABILITY", "Government contributions", "1"},
        {"2150", "Pag-IBIG Contributions Payable", "LIABILITY", "Government contributions", "1"},
        {"2160", "Salaries Payable", "LIABILITY", "Payables", "1"},
        {"2170", "Employee Sinking Fund", "LIABILITY", "Other liabilities", "1"},
        {"2200", "Customer Deposits", "LIABILITY", "Other liabilities", "1"},
        {"2300", "Loans Payable", "LIABILITY", "Other liabilities", "0"},
        {"3010", "Owner's Capital", "EQUITY", "Equity", "1"},
        {"3020", "Owner's Drawings", "EQUITY", "Equity", "1"},
        {"3100", "Retained Earnings", "EQUITY", "Equity", "1"},
        {"3900", "Opening Balance Equity", "EQUITY", "Equity", "1"},
        {"4010", "Contract Revenue", "INCOME", "Revenue", "1"},
        {"4020", "Sales - Aircon Supplies", "INCOME", "Revenue", "1"},
        {"4030", "Service Income - Repairs and Maintenance", "INCOME", "Revenue", "0"},
        {"4090", "Other Income", "INCOME", "Other income", "1"},
        {"5010", "Materials Used", "EXPENSE", "Cost of contracts", "1"},
        {"5020", "Direct Labor", "EXPENSE", "Cost of contracts", "1"},
        {"5030", "Subcontract Costs", "EXPENSE", "Cost of contracts", "1"},
        {"5040", "Equipment Rental", "EXPENSE", "Cost of contracts", "0"},
        {"5090", "Other Direct Costs", "EXPENSE", "Cost of contracts", "0"},
        {"6010", "Salaries and Wages - Office", "EXPENSE", "Operating expenses", "0"},
        {"6020", "Employer Contributions (SSS, PhilHealth, Pag-IBIG)", "EXPENSE", "Operating expenses", "0"},
        {"6030", "Rent", "EXPENSE", "Operating expenses", "0"},
        {"6040", "Light and Water", "EXPENSE", "Operating expenses", "0"},
        {"6050", "Communication and Internet", "EXPENSE", "Operating expenses", "0"},
        {"6060", "Fuel and Oil", "EXPENSE", "Operating expenses", "0"},
        {"6070", "Transportation and Travel", "EXPENSE", "Operating expenses", "0"},
        {"6080", "Office Supplies", "EXPENSE", "Operating expenses", "0"},
        {"6090", "Repairs and Maintenance", "EXPENSE", "Operating expenses", "0"},
        {"6100", "Taxes and Licenses", "EXPENSE", "Operating expenses", "0"},
        {"6110", "Professional Fees", "EXPENSE", "Operating expenses", "0"},
        {"6120", "Representation", "EXPENSE", "Operating expenses", "0"},
        {"6130", "Bank Charges", "EXPENSE", "Operating expenses", "0"},
        {"6140", "Depreciation", "EXPENSE", "Operating expenses", "0"},
        {"6150", "Inventory Losses and Shrinkage", "EXPENSE", "Operating expenses", "1"},
        {"6190", "Miscellaneous Expense", "EXPENSE", "Operating expenses", "1"},
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String[] a : CHART) {
            if (accounts.existsByCode(a[0])) continue;
            Account x = new Account();
            x.code = a[0]; x.name = a[1]; x.type = a[2]; x.groupName = a[3]; x.systemAccount = "1".equals(a[4]);
            accounts.save(x);
        }
        if (banks.count() == 0) {
            BankAccount cash = new BankAccount();
            cash.name = "Cash on Hand"; cash.kind = "CASH"; cash.accountId = accounts.findByCode(Acct.CASH_ON_HAND).orElseThrow().id;
            banks.save(cash);
            BankAccount petty = new BankAccount();
            petty.name = "Petty Cash Fund"; petty.kind = "PETTY"; petty.accountId = accounts.findByCode(Acct.PETTY_CASH).orElseThrow().id;
            banks.save(petty);
        }
    }
}
