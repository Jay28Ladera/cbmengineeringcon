package com.asmsi.admin_system.accounting;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Monthly recurring office expenses: each becomes a bill (Payables) for the month with one click. */
@Service
public class ExpenseService {
    private final RecurringExpenseRepository recurring;
    private final PayablesService payables;

    public ExpenseService(RecurringExpenseRepository recurring, PayablesService payables) { this.recurring = recurring; this.payables = payables; }

    public static String month(YearMonth m) { return m.toString(); }

    @Transactional
    public Bill bill(RecurringExpense r, YearMonth m, BigDecimal amount, String invoiceNo, String by) {
        if (month(m).equals(r.lastMonth)) throw new IllegalStateException(r.description + " is already billed for " + m + ".");
        Bill b = new Bill();
        b.supplierId = r.supplierId; b.kind = "EXPENSE"; b.jobOrderId = r.jobOrderId; b.invoiceNo = invoiceNo;
        b.billDate = m.atDay(Math.min(r.dayOfMonth, m.lengthOfMonth()));
        b.dueDate = b.billDate.plusDays(r.dueDays);
        b.vatMode = r.vatMode; b.ewtRate = r.ewtRate;
        b.memo = r.description + " · " + m;
        BigDecimal amt = amount == null || amount.signum() <= 0 ? r.amount : amount;
        Bill saved = payables.create(b, List.of(new PayablesService.LineIn(r.description + " · " + m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + m.getYear(),
                BigDecimal.ONE, amt, r.accountId, r.jobOrderId, null)), by);
        r.lastMonth = month(m);
        recurring.save(r);
        return saved;
    }

    public boolean dueThisMonth(RecurringExpense r, LocalDate today) {
        return r.active && !month(YearMonth.from(today)).equals(r.lastMonth);
    }
}
