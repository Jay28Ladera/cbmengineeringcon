package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.service.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › Bank Reconciliation. */
@Controller
@RequestMapping("/Accounting/BankRecon")
public class ReconController {

    private final ReconService service;
    private final BankReconRepository recons;
    private final StatementLineRepository stmt;
    private final BankAccountRepository banks;
    private final Ledger ledger;
    private final AuthUtil auth;

    public ReconController(ReconService service, BankReconRepository recons, StatementLineRepository stmt, BankAccountRepository banks, Ledger ledger, AuthUtil auth) {
        this.service = service; this.recons = recons; this.stmt = stmt; this.banks = banks; this.ledger = ledger; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }

    public record BankRow(BankAccount bank, BigDecimal book, BankRecon last, BankRecon draft) {}

    @GetMapping
    @Transactional(readOnly = true)
    public String list(Model model) {
        List<BankRow> rows = new ArrayList<>();
        for (BankAccount b : banks.findByActiveTrueOrderByKindAscNameAsc()) {
            if (!b.isCheques()) continue;
            rows.add(new BankRow(b, ledger.balanceOf(b.accountId, LocalDate.now()),
                    recons.findFirstByBankIdAndStatusOrderByStatementDateDescIdDesc(b.id, "DONE").orElse(null),
                    recons.findFirstByBankIdAndStatus(b.id, "DRAFT").orElse(null)));
        }
        Map<Long, BankAccount> bm = new HashMap<>(); banks.findAll().forEach(b -> bm.put(b.id, b));
        List<BankRecon> history = new ArrayList<>();
        for (BankRow r : rows) history.addAll(recons.findByBankIdOrderByStatementDateDescIdDesc(r.bank().id));
        history.sort(Comparator.comparing((BankRecon r) -> r.statementDate).reversed());
        model.addAttribute("rows", rows);
        model.addAttribute("history", history);
        model.addAttribute("bankMap", bm);
        model.addAttribute("lastMonthEnd", YearMonth.now().minusMonths(1).atEndOfMonth());
        model.addAttribute("activePage", "bank-recon");
        return "acc-recon-list";
    }

    @PostMapping
    public String start(@RequestParam(required = false) String bankId, @RequestParam(required = false) String statementDate,
                        @RequestParam(required = false) String statementBalance, RedirectAttributes ra) {
        try { return "redirect:/Accounting/BankRecon/" + service.start(id(bankId), date(statementDate), num(statementBalance), me()).id; }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); return "redirect:/Accounting/BankRecon"; }
    }

    private BankRecon find(Long id) { return recons.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String view(@PathVariable Long id, Model model) {
        BankRecon r = find(id);
        ReconService.View v = service.view(r);
        List<StatementLine> sl = stmt.findByReconIdOrderByTxnDateAscIdAsc(id);
        Map<Long, StatementLine> byLine = new HashMap<>();
        for (StatementLine s : sl) if (s.matchedLineId != null) byLine.put(s.matchedLineId, s);
        model.addAttribute("v", v);
        model.addAttribute("r", r);
        model.addAttribute("stmt", sl);
        model.addAttribute("unmatched", sl.stream().filter(s -> s.matchedLineId == null && !s.ignored).toList());
        model.addAttribute("byLine", byLine);
        model.addAttribute("activePage", "bank-recon");
        return "acc-recon";
    }

    @PostMapping("/{id}/tick")
    public String tick(@PathVariable Long id, @RequestParam(name = "line", required = false) List<Long> ticked, @RequestParam(required = false) String statementBalance,
                       @RequestParam(defaultValue = "save") String action, RedirectAttributes ra) {
        BankRecon r = find(id);
        try {
            service.tick(r, ticked == null ? Set.of() : new HashSet<>(ticked), num(statementBalance));
            if ("complete".equals(action)) { service.complete(r, me()); ra.addFlashAttribute("success", "Reconciled: " + r.statementDate + " statement agrees with the books."); }
            else ra.addFlashAttribute("success", "Saved.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/BankRecon/" + id;
    }

    @PostMapping("/{id}/adjust")
    public String adjust(@PathVariable Long id, @RequestParam(required = false) String kind, @RequestParam(required = false) String date,
                         @RequestParam(required = false) String amount, @RequestParam(required = false) String memo, @RequestParam(required = false) Long stmtLineId,
                         RedirectAttributes ra) {
        BankRecon r = find(id);
        try {
            service.adjust(r, kind, date(date), num(amount), memo, me());
            if (stmtLineId != null) stmt.findById(stmtLineId).ifPresent(s -> service.ignore(s, true));
            ra.addFlashAttribute("success", ("INTEREST".equals(kind) ? "Interest" : "Bank charge") + " posted and cleared.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/BankRecon/" + id;
    }

    @PostMapping("/{id}/import")
    public String importCsv(@PathVariable Long id, @RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        BankRecon r = find(id);
        try {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose the statement file (CSV).");
            var res = service.importCsv(r, file.getInputStream());
            ra.addFlashAttribute("success", res.lines() + " statement line(s) read; " + res.matched() + " matched to the books and ticked.");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/BankRecon/" + id;
    }

    @PostMapping("/{id}/stmt/{lineId}/ignore")
    public String ignore(@PathVariable Long id, @PathVariable Long lineId) {
        stmt.findById(lineId).ifPresent(s -> service.ignore(s, !s.ignored));
        return "redirect:/Accounting/BankRecon/" + id;
    }

    @PostMapping("/{id}/{action}")
    public String action(@PathVariable Long id, @PathVariable String action, RedirectAttributes ra) {
        BankRecon r = find(id);
        try {
            switch (action) {
                case "reopen" -> { service.reopen(r); ra.addFlashAttribute("success", "Reopened."); }
                case "delete" -> { service.delete(r); ra.addFlashAttribute("success", "Reconciliation deleted; its ticks were cleared."); return "redirect:/Accounting/BankRecon"; }
                case "rematch" -> ra.addFlashAttribute("success", service.match(r) + " more matched.");
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/BankRecon/" + id;
    }
}
