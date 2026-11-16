package com.asmsi.admin_system.accounting;

import com.asmsi.admin_system.inventory.Supplier;
import com.asmsi.admin_system.inventory.SupplierRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.accounting.F.*;

/** Accounting › BIR: 2307 for payees, 1601-EQ and VAT summaries, tax withheld by clients, alphalist exports. */
@Controller
@RequestMapping("/Accounting/Bir")
public class BirController {

    private final BirService bir;
    private final CompanyInfoRepository company;
    private final SupplierRepository suppliers;
    private final ClientCollectionRepository collections;
    private final ClientBillingService billing;

    private final com.asmsi.admin_system.admin.SettingsService settings;

    public BirController(BirService bir, CompanyInfoRepository company, SupplierRepository suppliers, ClientCollectionRepository collections, ClientBillingService billing,
                         com.asmsi.admin_system.admin.SettingsService settings) {
        this.settings = settings;
        this.bir = bir; this.company = company; this.suppliers = suppliers; this.collections = collections; this.billing = billing;
    }

    static BirService.Quarter quarter(String year, String q) {
        BirService.Quarter now = BirService.Quarter.of(LocalDate.now());
        try { int y = Integer.parseInt(year), n = Integer.parseInt(q); if (n >= 1 && n <= 4) return new BirService.Quarter(y, n); } catch (Exception ignored) { }
        return now;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public String page(@RequestParam(required = false) String year, @RequestParam(required = false) String q, Model model) {
        BirService.Quarter qt = quarter(year, q);
        List<BirService.Payee> payees = bir.withheld(qt);
        List<BirService.VatMonth> vat = bir.vat(qt);
        BigDecimal out = BigDecimal.ZERO, in = BigDecimal.ZERO;
        for (var m : vat) { out = out.add(m.output()); in = in.add(m.input()); }
        model.addAttribute("qt", qt);
        model.addAttribute("prev", qt.q() == 1 ? new BirService.Quarter(qt.year() - 1, 4) : new BirService.Quarter(qt.year(), qt.q() - 1));
        model.addAttribute("next", qt.q() == 4 ? new BirService.Quarter(qt.year() + 1, 1) : new BirService.Quarter(qt.year(), qt.q() + 1));
        model.addAttribute("payees", payees);
        model.addAttribute("atcs", bir.byAtc(payees));
        model.addAttribute("ewtTotal", payees.stream().map(BirService.Payee::getTotalTax).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("clients", bir.clientCwt(qt));
        model.addAttribute("vat", vat); model.addAttribute("vatOut", out); model.addAttribute("vatIn", in);
        model.addAttribute("co", company.get());
        model.addAttribute("missingTin", payees.stream().filter(p -> p.supplier != null && (p.supplier.tin == null || p.supplier.tin.isBlank())).map(p -> p.supplier.name).distinct().toList());
        model.addAttribute("activePage", "bir");
        return "acc-bir";
    }

    @PostMapping("/company")
    public String saveCompany(@RequestParam(required = false) String name, @RequestParam(required = false) String tin, @RequestParam(required = false) String rdoCode,
                              @RequestParam(required = false) String address, @RequestParam(required = false) String zipCode, @RequestParam(required = false) String signatory,
                              @RequestParam(required = false) String signatoryTitle, @RequestParam(required = false) String signatoryTin,
                              @RequestParam(required = false) String back, RedirectAttributes ra) {
        CompanyInfo c = company.get();
        c.name = Optional.ofNullable(cut(name, 160)).orElse(c.name); c.tin = cut(tin, 40); c.rdoCode = cut(rdoCode, 10); c.address = cut(address, 300);
        c.zipCode = cut(zipCode, 10); c.signatory = cut(signatory, 160); c.signatoryTitle = cut(signatoryTitle, 120); c.signatoryTin = cut(signatoryTin, 40);
        settings.saveCompany(c);
        ra.addFlashAttribute("success", "Company details saved.");
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/Accounting/Bir");
    }

    @PostMapping("/payee/{supplierId}")
    public String savePayee(@PathVariable Long supplierId, @RequestParam(required = false) String tin, @RequestParam(required = false) String address,
                            @RequestParam(required = false) String payeeType, @RequestParam(required = false) String atc,
                            @RequestParam(required = false) String back, RedirectAttributes ra) {
        suppliers.findById(supplierId).ifPresent(s -> {
            s.tin = cut(tin, 40); s.address = cut(address, 300);
            s.payeeType = "INDIVIDUAL".equals(payeeType) ? "INDIVIDUAL" : "CORPORATION"; s.atc = cut(atc, 10) == null ? null : atc.trim().toUpperCase();
            suppliers.save(s);
            ra.addFlashAttribute("success", s.name + " updated.");
        });
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/Accounting/Bir");
    }

    @GetMapping("/2307")
    @Transactional(readOnly = true)
    public String form2307(@RequestParam(required = false) String year, @RequestParam(required = false) String q, @RequestParam(required = false) Long supplier, Model model) {
        BirService.Quarter qt = quarter(year, q);
        List<BirService.Payee> ps = bir.withheld(qt);
        if (supplier != null) ps = ps.stream().filter(p -> p.supplier != null && p.supplier.id.equals(supplier)).toList();
        // one form per payee: a payee with several ATCs gets one form listing them all
        Map<Long, List<BirService.Payee>> byPayee = new LinkedHashMap<>();
        for (BirService.Payee p : ps) byPayee.computeIfAbsent(p.supplier == null ? -1L : p.supplier.id, k -> new ArrayList<>()).add(p);
        model.addAttribute("forms", new ArrayList<>(byPayee.values()));
        model.addAttribute("qt", qt);
        model.addAttribute("co", company.get());
        return "acc-bir-2307";
    }

    @GetMapping("/qap.csv")
    public ResponseEntity<byte[]> qap(@RequestParam(required = false) String year, @RequestParam(required = false) String q) {
        BirService.Quarter qt = quarter(year, q);
        return csv("QAP-" + qt.year() + "Q" + qt.q() + ".csv", bir.qapCsv(qt, company.get()));
    }

    @GetMapping("/sawt.csv")
    public ResponseEntity<byte[]> sawt(@RequestParam(required = false) String year, @RequestParam(required = false) String q) {
        BirService.Quarter qt = quarter(year, q);
        return csv("SAWT-" + qt.year() + "Q" + qt.q() + ".csv", bir.sawtCsv(qt));
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }

}
