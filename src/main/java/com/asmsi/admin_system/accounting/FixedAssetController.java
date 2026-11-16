package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Lookups;
import com.asmsi.admin_system.service.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › Fixed Assets: register, monthly depreciation, disposal. */
@Controller
@RequestMapping("/Accounting/FixedAssets")
public class FixedAssetController {

    private final FixedAssetService service;
    private final FixedAssetRepository assets;
    private final DepreciationRunRepository runs;
    private final BankAccountRepository banks;
    private final Lookups lookups;
    private final AuthUtil auth;

    public FixedAssetController(FixedAssetService service, FixedAssetRepository assets, DepreciationRunRepository runs,
                                BankAccountRepository banks, Lookups lookups, AuthUtil auth) {
        this.service = service; this.assets = assets; this.runs = runs; this.banks = banks; this.lookups = lookups; this.auth = auth;
    }

    private String me() { var u = auth.getCurrentUser(); return u == null ? "system" : u.getName(); }

    private FixedAsset find(Long id) { return assets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }

    @GetMapping
    @Transactional(readOnly = true)
    public String list(@RequestParam(required = false) String month, @RequestParam(required = false) String show, Model model) {
        List<FixedAssetService.View> all = service.views();
        boolean disposed = "disposed".equals(show);
        List<FixedAssetService.View> rows = all.stream().filter(v -> v.a().isActive() != disposed).toList();
        BigDecimal cost = BigDecimal.ZERO, acc = BigDecimal.ZERO;
        for (var v : all) if (v.a().isActive()) { cost = cost.add(v.a().cost); acc = acc.add(v.accumulated()); }
        YearMonth next = service.nextMonth();
        YearMonth m = parse(month, next);
        List<FixedAssetService.RunLine> preview = service.preview(m);
        model.addAttribute("rows", rows);
        model.addAttribute("disposed", disposed);
        model.addAttribute("disposedCount", all.stream().filter(v -> !v.a().isActive()).count());
        model.addAttribute("cost", cost); model.addAttribute("accumulated", acc); model.addAttribute("bookValue", cost.subtract(acc));
        model.addAttribute("month", m.toString());
        model.addAttribute("monthLabel", FixedAssetService.label(m));
        model.addAttribute("nextMonth", next.toString());
        model.addAttribute("preview", preview);
        model.addAttribute("previewTotal", preview.stream().map(FixedAssetService.RunLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("alreadyRun", runs.findFirstByMonthEndAndStatus(m.atEndOfMonth(), "POSTED").isPresent());
        model.addAttribute("canRun", !m.isAfter(YearMonth.now()));
        model.addAttribute("runs", runs.findAllByOrderByMonthEndDesc().stream().limit(24).toList());
        model.addAttribute("lastRun", runs.findFirstByStatusOrderByMonthEndDesc("POSTED").orElse(null));
        common(model);
        return "acc-assets";
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public String view(@PathVariable Long id, Model model) {
        FixedAsset a = find(id);
        model.addAttribute("v", service.view(a));
        model.addAttribute("a", a);
        model.addAttribute("schedule", service.schedule(a));
        common(model);
        return "acc-asset";
    }

    private void common(Model model) {
        model.addAttribute("categories", FixedAssetService.CATEGORIES);
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("jobMap", lookups.jobMap());
        model.addAttribute("banks", banks.findByActiveTrueOrderByKindAscNameAsc());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("activePage", "acc-assets");
    }

    @PostMapping
    public String create(@RequestParam Map<String, String> f, RedirectAttributes ra) {
        FixedAsset a = new FixedAsset();
        try {
            fill(a, f, true);
            a.acquiredHow = f.getOrDefault("acquiredHow", "BOOKED");
            a = service.create(a, id(f.get("bankId")), me());
            ra.addFlashAttribute("success", a.code + " " + a.name + " added.");
            return "redirect:/Accounting/FixedAssets/" + a.id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/FixedAssets";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam Map<String, String> f, RedirectAttributes ra) {
        FixedAsset a = find(id);
        try {
            fill(a, f, false);
            service.update(a);
            ra.addFlashAttribute("success", "Saved.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/FixedAssets/" + id;
    }

    private static void fill(FixedAsset a, Map<String, String> f, boolean isNew) {
        a.name = cut(f.get("name"), 160);
        a.category = FixedAssetService.category(f.getOrDefault("category", "EQUIPMENT")).key();
        a.serialNo = cut(f.get("serialNo"), 80); a.plateNo = cut(f.get("plateNo"), 20);
        a.custodian = cut(f.get("custodian"), 120); a.location = cut(f.get("location"), 160);
        a.jobOrderId = id(f.get("jobOrderId")); a.notes = cut(f.get("notes"), 600);
        BigDecimal life = num(f.get("lifeYears"));
        if (life != null) a.lifeMonths = life.multiply(BigDecimal.valueOf(12)).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        BigDecimal salvage = num(f.get("salvage"));
        a.salvage = salvage == null ? BigDecimal.ZERO : Ledger.r2(salvage);
        if (isNew) {
            a.acquiredDate = date(f.get("acquiredDate"));
            BigDecimal cost = num(f.get("cost"));
            a.cost = cost == null ? BigDecimal.ZERO : Ledger.r2(cost);
            BigDecimal acc = num(f.get("openingAccum"));
            a.openingAccum = acc == null ? BigDecimal.ZERO : Ledger.r2(acc);
            LocalDate start = date(f.get("depStart") == null || f.get("depStart").isBlank() ? null : f.get("depStart") + "-01");
            a.depStart = start;
        }
    }

    @PostMapping("/{id}/dispose")
    public String dispose(@PathVariable Long id, @RequestParam(required = false) String date, @RequestParam(required = false) String proceeds,
                          @RequestParam(required = false) String bankId, @RequestParam(required = false) String note, RedirectAttributes ra) {
        try {
            FixedAsset a = find(id);
            service.dispose(a, date(date), num(proceeds), id(bankId), note, me());
            ra.addFlashAttribute("success", a.code + " disposed of; its cost and depreciation are off the books.");
        } catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/FixedAssets/" + id;
    }

    @PostMapping("/{id}/undo-disposal")
    public String undoDisposal(@PathVariable Long id, RedirectAttributes ra) {
        try { service.undoDisposal(find(id), me()); ra.addFlashAttribute("success", "Disposal undone; the asset is back in use."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/FixedAssets/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try { FixedAsset a = find(id); service.delete(a, me()); ra.addFlashAttribute("success", a.code + " deleted."); return "redirect:/Accounting/FixedAssets"; }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); return "redirect:/Accounting/FixedAssets/" + id; }
    }

    @PostMapping("/depreciation")
    public String run(@RequestParam(required = false) String month, RedirectAttributes ra) {
        YearMonth m = parse(month, null);
        try {
            if (m == null) throw new IllegalArgumentException("Choose the month.");
            DepreciationRun r = service.post(m, me());
            ra.addFlashAttribute("success", "Depreciation for " + FixedAssetService.label(m) + " posted: ₱" + String.format("%,.2f", r.total) + " on " + r.assetCount + " asset(s).");
            return "redirect:/Accounting/FixedAssets?month=" + m.plusMonths(1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/Accounting/FixedAssets" + (m == null ? "" : "?month=" + m);
        }
    }

    @PostMapping("/depreciation/{runId}/void")
    public String voidRun(@PathVariable Long runId, RedirectAttributes ra) {
        DepreciationRun r = runs.findById(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try { service.voidRun(r, me()); ra.addFlashAttribute("success", "Depreciation for " + FixedAssetService.label(YearMonth.from(r.monthEnd)) + " undone."); }
        catch (IllegalArgumentException | IllegalStateException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/Accounting/FixedAssets?month=" + YearMonth.from(r.monthEnd);
    }

    private static YearMonth parse(String s, YearMonth fallback) {
        try { return s == null || s.isBlank() ? fallback : YearMonth.parse(s.trim()); } catch (RuntimeException e) { return fallback; }
    }
}
