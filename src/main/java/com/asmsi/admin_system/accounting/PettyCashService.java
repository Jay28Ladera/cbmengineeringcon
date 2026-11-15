package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.entity.DeductionPlan;
import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.inventory.InventoryService;
import com.asmsi.admin_system.repository.DeductionPlanRepository;
import com.asmsi.admin_system.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.asmsi.admin_system.accounting.ClientBillingService.*;

/**
 * Petty cash (imprest): vouchers post at once (Dr expense / Cr fund); a replenishment refills the fund from a bank
 * for what was paid out. Cash advances to employees: Dr advances to employees / Cr fund or bank; liquidated with
 * receipts, cash returned, and any balance deducted from payroll.
 */
@Service
public class PettyCashService {

    private final PettyCashVoucherRepository vouchers;
    private final ReplenishmentRepository replenishments;
    private final CashAdvanceRepository advances;
    private final CashAdvanceLineRepository advanceLines;
    private final BankAccountRepository banks;
    private final EmployeeRepository employees;
    private final DeductionPlanRepository plans;
    private final Ledger ledger;
    private final InventoryService numbers;

    public PettyCashService(PettyCashVoucherRepository vouchers, ReplenishmentRepository replenishments, CashAdvanceRepository advances,
                            CashAdvanceLineRepository advanceLines, BankAccountRepository banks, EmployeeRepository employees,
                            DeductionPlanRepository plans, Ledger ledger, InventoryService numbers) {
        this.vouchers = vouchers; this.replenishments = replenishments; this.advances = advances; this.advanceLines = advanceLines;
        this.banks = banks; this.employees = employees; this.plans = plans; this.ledger = ledger; this.numbers = numbers;
    }

    public BigDecimal balance(BankAccount fund) { return ledger.balanceOf(fund.accountId, LocalDate.now()); }

    private BankAccount fund(Long id) { return banks.findById(id == null ? -1L : id).orElseThrow(() -> new IllegalArgumentException("Choose the fund.")); }

    // ------------------------------------------------------------------ vouchers

    @Transactional
    public PettyCashVoucher pay(PettyCashVoucher v, String by) {
        BankAccount f = fund(v.fundId);
        if (v.payee == null || v.payee.isBlank()) throw new IllegalArgumentException("Who was paid?");
        if (v.description == null || v.description.isBlank()) throw new IllegalArgumentException("What was it for?");
        if (v.accountId == null) throw new IllegalArgumentException("Choose the expense account.");
        if (v.amount == null || v.amount.signum() <= 0) throw new IllegalArgumentException("Enter the amount.");
        v.amount = r2(v.amount);
        if (v.voucherDate == null) v.voucherDate = LocalDate.now();
        BigDecimal bal = balance(f);
        if (v.amount.compareTo(bal) > 0) throw new IllegalArgumentException(f.name + " has only ₱" + peso(bal) + ". Replenish it first.");
        v.number = numbers.nextNumber("acc_pcv", "PCV"); v.createdBy = by;
        vouchers.save(v);
        JournalEntry e = ledger.post(v.voucherDate, "Petty cash " + v.number + " · " + v.payee + " · " + v.description, "PETTY", v.id, v.number,
                List.of(JournalLine.dr(v.accountId, v.amount, v.description).job(v.jobOrderId), JournalLine.cr(f.accountId, v.amount, f.name)), by);
        v.journalId = e.id;
        return vouchers.save(v);
    }

    @Transactional
    public void voidVoucher(PettyCashVoucher v, String reason, String by) {
        if (!v.isPosted()) return;
        if (v.replenishmentId != null) throw new IllegalStateException(v.number + " was already replenished. Correct it with a journal entry instead.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason.");
        ledger.reverseSource("PETTY", v.id, reason.trim(), by);
        v.status = "VOID"; v.voidReason = cut(reason.trim(), 300);
        vouchers.save(v);
    }

    // ------------------------------------------------------------------ replenishment

    public record Unreplenished(List<PettyCashVoucher> vouchers, List<CashAdvance> advances, BigDecimal total) {}

    @Transactional(readOnly = true)
    public Unreplenished unreplenished(Long fundId) {
        List<PettyCashVoucher> vs = vouchers.findByFundIdAndStatusAndReplenishmentIdIsNullOrderByIdAsc(fundId, "POSTED");
        List<CashAdvance> as = advances.findBySourceIdAndReplenishmentIdIsNullAndStatusNotOrderByIdAsc(fundId, "VOID");
        BigDecimal t = BigDecimal.ZERO;
        for (PettyCashVoucher v : vs) t = t.add(v.amount);
        for (CashAdvance a : as) t = t.add(a.amount.subtract(a.returned));
        return new Unreplenished(vs, as, t);
    }

    @Transactional
    public Replenishment replenish(Long fundId, Long bankId, LocalDate date, String chequeNo, String by) {
        BankAccount f = fund(fundId), bank = banks.findById(bankId == null ? -1L : bankId).orElseThrow(() -> new IllegalArgumentException("Choose the bank the cheque is drawn on."));
        if (!f.isPetty()) throw new IllegalArgumentException(f.name + " is not a petty cash fund.");
        if (bank.id.equals(f.id)) throw new IllegalArgumentException("Choose a different account to refill from.");
        Unreplenished u = unreplenished(fundId);
        if (u.total().signum() <= 0) throw new IllegalStateException("Nothing to replenish: no vouchers since the last replenishment.");
        Replenishment r = new Replenishment();
        r.number = numbers.nextNumber("acc_pcf_replenishment", "PCR"); r.fundId = f.id; r.bankId = bank.id;
        r.replDate = date == null ? LocalDate.now() : date; r.chequeNo = cut(chequeNo, 40); r.amount = u.total(); r.createdBy = by;
        replenishments.save(r);
        JournalEntry e = ledger.post(r.replDate, "Petty cash replenishment " + r.number + " · " + f.name + (r.chequeNo != null ? " · cheque " + r.chequeNo : ""),
                "REPLENISH", r.id, r.number, List.of(JournalLine.dr(f.accountId, r.amount, f.name), JournalLine.cr(bank.accountId, r.amount, bank.name)), by);
        r.journalId = e.id;
        replenishments.save(r);
        for (PettyCashVoucher v : u.vouchers()) { v.replenishmentId = r.id; vouchers.save(v); }
        for (CashAdvance a : u.advances()) { a.replenishmentId = r.id; advances.save(a); }
        return r;
    }

    // ------------------------------------------------------------------ cash advances

    @Transactional
    public CashAdvance advance(CashAdvance a, String by) {
        Employee emp = employees.findById(a.employeeId == null ? -1L : a.employeeId).orElseThrow(() -> new IllegalArgumentException("Choose the employee."));
        BankAccount src = fund(a.sourceId);
        if (a.amount == null || a.amount.signum() <= 0) throw new IllegalArgumentException("Enter the amount.");
        if (a.purpose == null || a.purpose.isBlank()) throw new IllegalArgumentException("What is the advance for?");
        a.amount = r2(a.amount);
        if (src.isPetty() && a.amount.compareTo(balance(src)) > 0) throw new IllegalArgumentException(src.name + " has only ₱" + peso(balance(src)) + ".");
        if (a.advanceDate == null) a.advanceDate = LocalDate.now();
        a.employeeName = emp.getFullName();
        a.number = numbers.nextNumber("acc_cash_advance", "CA"); a.createdBy = by;
        advances.save(a);
        ledger.post(a.advanceDate, "Cash advance " + a.number + " · " + a.employeeName + " · " + a.purpose, "ADVANCE", a.id, a.number,
                List.of(JournalLine.dr(ledger.id(Acct.ADVANCES_EMPLOYEES), a.amount, a.employeeName).job(a.jobOrderId), JournalLine.cr(src.accountId, a.amount, src.name)), by);
        return a;
    }

    public record Receipt(String description, Long accountId, Long jobOrderId, BigDecimal amount, String receiptNo) {}

    /** Receipts and cash returned settle the advance; whatever is left is deducted from the employee's pay. */
    @Transactional
    public CashAdvance liquidate(CashAdvance a, List<Receipt> receipts, BigDecimal returned, BigDecimal perCutoff, String by) {
        if (!a.isOpen()) throw new IllegalStateException(a.number + " is already " + a.status.toLowerCase() + ".");
        List<Receipt> use = receipts.stream().filter(r -> r.amount() != null && r.amount().signum() > 0).toList();
        if (use.stream().anyMatch(r -> r.accountId() == null)) throw new IllegalArgumentException("Choose an account for every receipt.");
        BigDecimal spent = use.stream().map(r -> r2(r.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ret = returned == null ? BigDecimal.ZERO : r2(returned);
        if (spent.add(ret).compareTo(a.amount) > 0) throw new IllegalArgumentException("Receipts and cash returned (₱" + peso(spent.add(ret)) + ") are more than the advance (₱" + peso(a.amount) + "). Pay the excess as a petty cash voucher.");
        BigDecimal left = a.amount.subtract(spent).subtract(ret);
        Long adv = ledger.id(Acct.ADVANCES_EMPLOYEES);
        List<JournalLine> j = new ArrayList<>();
        for (Receipt r : use) {
            CashAdvanceLine l = new CashAdvanceLine();
            l.advanceId = a.id; l.description = cut(r.description() == null || r.description().isBlank() ? a.purpose : r.description(), 300);
            l.accountId = r.accountId(); l.jobOrderId = r.jobOrderId() != null ? r.jobOrderId() : a.jobOrderId; l.amount = r2(r.amount()); l.receiptNo = cut(r.receiptNo(), 60);
            advanceLines.save(l);
            j.add(JournalLine.dr(l.accountId, l.amount, l.description + (l.receiptNo != null ? " · OR " + l.receiptNo : "")).job(l.jobOrderId));
        }
        if (ret.signum() > 0) j.add(JournalLine.dr(fund(a.sourceId).accountId, ret, "Cash returned by " + a.employeeName));
        if (spent.add(ret).signum() > 0) {
            j.add(JournalLine.cr(adv, spent.add(ret), "Advance " + a.number + " liquidated"));
            ledger.post(LocalDate.now(), "Liquidation of " + a.number + " · " + a.employeeName, "LIQUIDATION", a.id, a.number, j, by);
        }
        if (left.signum() > 0) {
            // the balance stays in advances to employees until payroll deducts it
            DeductionPlan p = new DeductionPlan();
            p.setEmployeeId(a.employeeId); p.setKind("CASH_ADVANCE"); p.setDescription("Unliquidated " + a.number);
            p.setTotalAmount(left); p.setBalance(left);
            p.setPerCutoff(perCutoff == null || perCutoff.signum() <= 0 ? left : r2(perCutoff).min(left));
            p.setStartDate(LocalDate.now()); p.setActive(true);
            plans.save(p);
            a.planId = p.getId();
        }
        a.spent = spent; a.returned = ret; a.toPayroll = left;
        a.status = "LIQUIDATED"; a.liquidatedAt = LocalDateTime.now(); a.liquidatedBy = by;
        return advances.save(a);
    }

    @Transactional
    public void voidAdvance(CashAdvance a, String reason, String by) {
        if (!a.isOpen()) throw new IllegalStateException("Only an open advance can be voided.");
        if (a.replenishmentId != null) throw new IllegalStateException("The fund was already replenished for this advance. Liquidate it instead.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give the reason.");
        ledger.reverseSource("ADVANCE", a.id, reason.trim(), by);
        a.status = "VOID"; a.voidReason = cut(reason.trim(), 300);
        advances.save(a);
    }
}
