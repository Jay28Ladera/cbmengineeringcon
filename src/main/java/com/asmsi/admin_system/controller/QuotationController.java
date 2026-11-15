package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.QuotationRepository;
import com.asmsi.admin_system.repository.JobOrderRepository;
import com.asmsi.admin_system.entity.Quotation;
import com.asmsi.admin_system.entity.QuotationLineItem;
import com.asmsi.admin_system.entity.JobOrder;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping({"/Quotations", "/quotations"})
public class QuotationController {
    private final QuotationRepository quotations;
    private final JobOrderRepository jobOrders;
    private final com.asmsi.admin_system.inventory.PriceHints priceHints;
    private final com.asmsi.admin_system.inventory.MaterialRepository materials;
    private static final java.util.Set<String> KINDS = java.util.Set.of("MATERIAL", "LABOR", "SUBCON", "EQUIPMENT", "OTHER");
    public QuotationController(QuotationRepository quotations, JobOrderRepository jobOrders,
                               com.asmsi.admin_system.inventory.PriceHints priceHints,
                               com.asmsi.admin_system.inventory.MaterialRepository materials) {
        this.quotations = quotations;
        this.jobOrders = jobOrders;
        this.priceHints = priceHints;
        this.materials = materials;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("quotations", quotations.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "quotations");
        return "quotations";
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public String view(@PathVariable Long id, Model model) {
        Quotation quotation = load(id);
        model.addAttribute("quotation", quotation);
        model.addAttribute("jobOrder", jobOrders.findByQuotationId(id).orElse(null));
        var hints = priceHints.activeMaterials();
        model.addAttribute("hints", hints);
        java.util.Map<Long, com.asmsi.admin_system.inventory.Material> matMap = new java.util.HashMap<>();
        for (var m : materials.findAll()) matMap.put(m.id, m);
        model.addAttribute("materialMap", matMap);
        model.addAttribute("laborRates", laborRates());
        var me = auth.getCurrentUser();
        var so = workflow.signoff(id);
        model.addAttribute("signoff", so);
        String subEmail = workflow.submitterEmail(so);
        model.addAttribute("submitterEmail", subEmail != null && me != null && subEmail.equalsIgnoreCase(me.getEmail()) ? null : subEmail);
        var approverList = workflow.approvers();
        model.addAttribute("approvers", approverList);
        model.addAttribute("noApproversChosen", workflow.noApproversChosen());
        model.addAttribute("canApprove", workflow.canApprove(me));
        model.addAttribute("hasSignature", workflow.hasSignature(me));
        model.addAttribute("approverEmails", String.join(", ", approverList.stream().map(com.asmsi.admin_system.entity.User::getEmail)
                .filter(e -> e != null && !e.isBlank() && (me == null || !e.equalsIgnoreCase(me.getEmail()))).toList()));
        String st = quotation.getStatus();
        if (so.publicToken != null || "APPROVED".equals(st))
            model.addAttribute("clientLink", workflow.clientLink(quotation));
        model.addAttribute("activePage", "quotations");
        return "quotation-detail";
    }

    @PostMapping("/{id}/items")
    @Transactional
    public String saveItems(@PathVariable Long id,
            @RequestParam(name="description", required=false) List<String> descriptions,
            @RequestParam(name="quantity", required=false) List<String> quantities,
            @RequestParam(name="unit", required=false) List<String> units,
            @RequestParam(name="unitPrice", required=false) List<String> prices,
            @RequestParam(name="kind", required=false) List<String> kinds,
            @RequestParam(name="materialId", required=false) List<String> materialIds,
            @RequestParam(name="unitCost", required=false) List<String> costs,
            @RequestParam(required=false) String markupPct,
            @RequestParam(name="crewSize", required=false) List<String> crews,
            @RequestParam(name="workDays", required=false) List<String> days,
            @RequestParam(name="dailyRate", required=false) List<String> rates,
            @RequestParam(defaultValue="0") BigDecimal taxRate,
            @RequestParam(defaultValue="0") BigDecimal discountAmount,
            @RequestParam(required=false) String terms,
            RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"DRAFT".equals(quote.getStatus())) {
            redirect.addFlashAttribute("error", "Only a draft quotation can be edited.");
            return "redirect:/Quotations/" + id;
        }
        try {
            if (taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0 || discountAmount.signum() < 0)
                throw new IllegalArgumentException("Tax must be 0–100% and discount cannot be negative.");
            List<QuotationLineItem> pendingItems = new java.util.ArrayList<>();
            for (int i=0; descriptions != null && i<descriptions.size(); i++) {
                String description = descriptions.get(i) == null ? "" : descriptions.get(i).trim();
                if (description.isEmpty()) continue;
                BigDecimal quantity = new BigDecimal(quantities.get(i));
                BigDecimal price = new BigDecimal(prices.get(i));
                if (quantity.signum() <= 0 || price.signum() < 0) throw new IllegalArgumentException("Each item needs a positive quantity and a non-negative price.");
                QuotationLineItem item = new QuotationLineItem();
                item.setDescription(description);
                item.setQuantity(quantity);
                item.setUnit(units != null && i < units.size() && !units.get(i).isBlank() ? units.get(i).trim() : "unit");
                item.setUnitPrice(price);
                String kind = at(kinds, i).toUpperCase();
                item.setKind(KINDS.contains(kind) ? kind : "OTHER");
                if ("MATERIAL".equals(item.getKind())) {
                    Long mid = at(materialIds, i).matches("\\d+") ? Long.valueOf(at(materialIds, i)) : null;
                    item.setMaterialId(mid != null && materials.existsById(mid) ? mid : null);
                }
                if ("LABOR".equals(item.getKind())) {
                    String cw = at(crews, i), dy = at(days, i).replace(",", ""), rt = at(rates, i).replace(",", "");
                    if (!cw.isEmpty() || !dy.isEmpty() || !rt.isEmpty()) {
                        if (cw.isEmpty() || dy.isEmpty() || rt.isEmpty())
                            throw new IllegalArgumentException("For labor \"" + description + "\", enter the workers, the days and the rate per day (or leave all three blank).");
                        int crew = Integer.parseInt(cw); BigDecimal d = new BigDecimal(dy), r = new BigDecimal(rt);
                        if (crew <= 0 || d.signum() <= 0 || r.signum() < 0) throw new IllegalArgumentException("Workers and days must be more than zero, and the rate can't be negative.");
                        item.setCrewSize(crew); item.setWorkDays(d.setScale(2, java.math.RoundingMode.HALF_UP)); item.setDailyRate(r.setScale(2, java.math.RoundingMode.HALF_UP));
                        // keep unit cost in step so per-unit views still read right
                        item.setUnitCost(item.getCrewCost().divide(quantity, 2, java.math.RoundingMode.HALF_UP));
                        pendingItems.add(item);
                        continue;
                    }
                }
                String c = at(costs, i).replace(",", "");
                if (!c.isEmpty()) {
                    BigDecimal cost = new BigDecimal(c);
                    if (cost.signum() < 0) throw new IllegalArgumentException("A unit cost cannot be negative.");
                    item.setUnitCost(cost.setScale(2, java.math.RoundingMode.HALF_UP));
                }
                pendingItems.add(item);
            }
            if (pendingItems.isEmpty()) throw new IllegalArgumentException("Add at least one priced item.");
            BigDecimal pendingSubtotal = pendingItems.stream().map(QuotationLineItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (discountAmount.compareTo(pendingSubtotal) > 0) throw new IllegalArgumentException("Discount cannot be greater than the quotation subtotal.");
            quote.clearLineItems();
            pendingItems.forEach(quote::addLineItem);
            quote.setTaxRate(taxRate);
            quote.setDiscountAmount(discountAmount);
            quote.setTerms(terms == null ? null : terms.trim());
            String mk = markupPct == null ? "" : markupPct.trim();
            quote.setMarkupPct(mk.isEmpty() ? null : new BigDecimal(mk));
            quotations.save(quote);
            redirect.addFlashAttribute("success", "Quotation items and totals saved.");
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            redirect.addFlashAttribute("error", "Enter a valid quantity and unit price for every item.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/Quotations/" + id;
    }

    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.service.QuoteWorkflowService workflow;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.repository.QuotationSignoffRepository signoffs;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.service.AuthUtil auth;

    private Quotation load(Long id) {
        return quotations.findWithDetailsById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
    }
    private String step(Long id, RedirectAttributes redirect, String done, Runnable action) { return step(id, null, redirect, done, action); }
    /** Runs one workflow step; "from=approvals" returns to the Approvals page instead of the quotation. */
    private String step(Long id, String from, RedirectAttributes redirect, String done, Runnable action) {
        try { action.run(); if (done != null) redirect.addFlashAttribute("success", done); }
        catch (IllegalStateException | IllegalArgumentException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "approvals".equals(from) ? "redirect:/Approvals" : "redirect:/Quotations/" + id;
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id, RedirectAttributes redirect) {
        return step(id, redirect, "Submitted for approval. The approver has been notified by email.", () -> workflow.submit(load(id), auth.getCurrentUser()));
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String from, RedirectAttributes redirect) {
        Quotation q = load(id);
        return step(id, from, redirect, "approvals".equals(from) ? q.getQuotationNumber() + " approved. The preparer was told it can be sent to the client."
                : "Approved. Your signature is on the quotation, and it can now be sent to the client.", () -> workflow.approve(q, auth.getCurrentUser()));
    }

    /**
     * The approver approves and emails the client in one go. The approval stands even if the email fails;
     * then the quotation stays Approved and can be sent with "Send to client".
     */
    @PostMapping("/{id}/approve-send")
    public String approveAndSend(@PathVariable Long id,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) String cc,
                                 @RequestParam(required = false) String message,
                                 @RequestParam(defaultValue = "30") int validDays,
                                 @RequestParam(required = false) String from,
                                 RedirectAttributes redirect) {
        String back = "approvals".equals(from) ? "redirect:/Approvals" : "redirect:/Quotations/" + id;
        var me = auth.getCurrentUser();
        Quotation q = load(id);
        if (to == null || !to.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            redirect.addFlashAttribute("error", "Enter the client's email address. Nothing was approved.");
            return back;
        }
        try { workflow.approve(q, me, false); }
        catch (IllegalStateException | IllegalArgumentException e) { redirect.addFlashAttribute("error", e.getMessage()); return back; }
        try {
            List<String> ccList = cc == null ? List.of() : java.util.Arrays.stream(cc.split("[,;\\s]+")).filter(x -> !x.isBlank()).toList();
            workflow.send(load(id), me, true, to, ccList, message, validDays);
            redirect.addFlashAttribute("success", q.getQuotationNumber() + " approved and emailed to " + to.trim()
                    + (ccList.isEmpty() ? "" : " (cc " + String.join(", ", ccList) + ")") + ".");
        } catch (IllegalStateException | IllegalArgumentException e) {
            String why = e.getMessage() != null && e.getMessage().startsWith("The email could not be sent: ")
                    ? e.getMessage().substring("The email could not be sent: ".length()).replaceAll("\\. The quotation was not marked.*$", "")
                    : e.getMessage();
            redirect.addFlashAttribute("error", q.getQuotationNumber() + " is approved, but the email to the client could not be sent (" + why
                    + "). Open it and use \"Send to client\" to try again.");
        }
        return back;
    }

    @PostMapping("/{id}/return")
    public String returnToDraft(@PathVariable Long id, @RequestParam(required = false) String note, @RequestParam(required = false) String from, RedirectAttributes redirect) {
        return step(id, from, redirect, "approvals".equals(from) ? "Returned to the preparer with your note." : "Back to draft. The prices can be changed again; it needs approval again before sending.", () -> workflow.returnToDraft(load(id), auth.getCurrentUser(), note));
    }

    /** Sends an approved quotation: by email with the client link (emailIt=true), or only marks it as sent. */
    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id,
                       @RequestParam(defaultValue = "false") boolean emailIt,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String cc,
                       @RequestParam(required = false) String message,
                       @RequestParam(defaultValue = "30") int validDays,
                       RedirectAttributes redirect) {
        try {
            List<String> ccList = cc == null ? List.of() : java.util.Arrays.stream(cc.split("[,;\\s]+")).filter(x -> !x.isBlank()).toList();
            var r = workflow.send(load(id), auth.getCurrentUser(), emailIt, to, ccList, message, validDays);
            redirect.addFlashAttribute("success", r.emailed()
                    ? "Sent to " + to.trim() + (ccList.isEmpty() ? "" : " (cc " + String.join(", ", ccList) + ")") + ". You will get an email when the client answers."
                    : "Marked as sent. Share the client link below so they can view and sign it.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/Quotations/" + id;
    }

    /** The client's uploaded signed copy. */
    @GetMapping("/{id}/signed-copy")
    public org.springframework.http.ResponseEntity<byte[]> signedCopy(@PathVariable Long id) {
        var so = signoffs.findById(id).filter(com.asmsi.admin_system.entity.QuotationSignoff::hasClientFile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", so.clientFileType)
                .header("Content-Disposition", org.springframework.http.ContentDisposition.inline().filename(so.clientFileName == null ? "signed-copy" : so.clientFileName, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(so.clientFile);
    }

    @PostMapping("/{id}/decision")
    @Transactional
    public String decision(@PathVariable Long id, @RequestParam String decision, RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        String normalized = decision == null ? "" : decision.toUpperCase();
        if (!"SENT".equals(quote.getStatus()) || !(normalized.equals("ACCEPTED") || normalized.equals("REJECTED"))) {
            redirect.addFlashAttribute("error", "Only a sent quotation can be accepted or rejected.");
        } else {
            quote.setStatus(normalized);
            quote.setDecidedAt(LocalDateTime.now());
            quotations.save(quote);
            redirect.addFlashAttribute("success", "Client decision recorded: " + normalized + ".");
        }
        return "redirect:/Quotations/" + id;
    }

    @PostMapping("/{id}/job-order")
    @Transactional
    public String createJobOrder(@PathVariable Long id, RedirectAttributes redirect) {
        Quotation quote = quotations.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
        if (!"ACCEPTED".equals(quote.getStatus())) {
            redirect.addFlashAttribute("error", "Only an accepted quotation can become a job order.");
            return "redirect:/Quotations/" + id;
        }
        JobOrder order = jobOrders.findByQuotationId(id).orElseGet(() -> {
            JobOrder created = new JobOrder();
            created.setQuotation(quote);
            JobOrder saved = jobOrders.saveAndFlush(created);
            saved.setJobOrderNumber("JO-%06d".formatted(saved.getId()));
            return jobOrders.save(saved);
        });
        redirect.addFlashAttribute("success", "Job order created.");
        return "redirect:/JobOrders/" + order.getId();
    }

    /** Daily rates in HR, by position: average, lowest and highest of active daily-paid employees. */
    public record LaborRate(String position, BigDecimal avg, BigDecimal min, BigDecimal max, int count) {}
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.repository.EmployeeRepository employees;
    private List<LaborRate> laborRates() {
        java.util.Map<String, List<BigDecimal>> by = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var e : employees.findByActiveTrue()) {
            if (e.getDailyRate() == null || e.getDailyRate().signum() <= 0 || !"DAILY".equalsIgnoreCase(e.getRateType())) continue;
            String pos = e.getPosition() == null || e.getPosition().isBlank() ? "No position" : e.getPosition().trim();
            by.computeIfAbsent(pos, k -> new java.util.ArrayList<>()).add(e.getDailyRate());
        }
        List<LaborRate> out = new java.util.ArrayList<>();
        by.forEach((pos, rs) -> {
            BigDecimal sum = rs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new LaborRate(pos, sum.divide(BigDecimal.valueOf(rs.size()), 2, java.math.RoundingMode.HALF_UP),
                    rs.stream().min(BigDecimal::compareTo).get(), rs.stream().max(BigDecimal::compareTo).get(), rs.size()));
        });
        return out;
    }

    private static String at(List<String> list, int i) {
        return list == null || i >= list.size() || list.get(i) == null ? "" : list.get(i).trim();
    }
}
