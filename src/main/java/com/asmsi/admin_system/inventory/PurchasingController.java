package com.asmsi.admin_system.inventory;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.inventory.Forms.*;

/** Purchasing › Suppliers, Canvasses, Procurements (purchase orders), Market Prices. */
@Controller
@RequestMapping("/Purchasing")
public class PurchasingController {

    private final SupplierRepository suppliers;
    private final CanvassRepository canvasses;
    private final CanvassItemRepository canvassItems;
    private final CanvassSupplierRepository canvassSuppliers;
    private final CanvassQuoteRepository quotes;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final StockReceiptRepository receipts;
    private final MaterialRepository materials;
    private final MarketPriceRepository prices;
    private final PurchasingService purchasing;
    private final InventoryService inventory;
    private final Lookups lookups;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private PoPaymentGate paymentGate;
    @org.springframework.beans.factory.annotation.Autowired private CanvassApprovalService canvassApprovals;
    @org.springframework.beans.factory.annotation.Autowired private CanvassSheet canvassSheet;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.service.AuthUtil auth;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.repository.UserSignatureRepository userSignatures;
    @org.springframework.beans.factory.annotation.Autowired private JobOrderMaterials jobMaterials;
    @org.springframework.beans.factory.annotation.Autowired private com.asmsi.admin_system.repository.JobOrderRepository jobOrderRepo;

    public PurchasingController(SupplierRepository suppliers, CanvassRepository canvasses, CanvassItemRepository canvassItems,
                                CanvassSupplierRepository canvassSuppliers, CanvassQuoteRepository quotes, PurchaseOrderRepository orders,
                                PurchaseOrderLineRepository orderLines, StockReceiptRepository receipts, MaterialRepository materials,
                                MarketPriceRepository prices, PurchasingService purchasing, InventoryService inventory, Lookups lookups) {
        this.suppliers = suppliers; this.canvasses = canvasses; this.canvassItems = canvassItems; this.canvassSuppliers = canvassSuppliers;
        this.quotes = quotes; this.orders = orders; this.orderLines = orderLines; this.receipts = receipts; this.materials = materials;
        this.prices = prices; this.purchasing = purchasing; this.inventory = inventory; this.lookups = lookups;
    }

    // ================================================================ Suppliers

    public record SupplierRow(Supplier supplier, long orders, BigDecimal ordered) {}

    @GetMapping("/Suppliers")
    @Transactional(readOnly = true)
    public String suppliers(Model model) {
        Map<Long, long[]> count = new HashMap<>();
        Map<Long, BigDecimal> amount = new HashMap<>();
        List<PurchaseOrder> pos = orders.findAll();
        Map<Long, PurchaseOrder> byId = new HashMap<>();
        for (PurchaseOrder p : pos) if (!"DRAFT".equals(p.status) && !"CANCELLED".equals(p.status)) { byId.put(p.id, p); count.computeIfAbsent(p.supplierId, k -> new long[1])[0]++; }
        for (PurchaseOrderLine l : orderLines.findByPoIdIn(byId.keySet())) amount.merge(byId.get(l.poId).supplierId, l.getAmount(), BigDecimal::add);
        List<SupplierRow> rows = new ArrayList<>();
        for (Supplier s : suppliers.findAllByOrderByActiveDescNameAsc())
            rows.add(new SupplierRow(s, count.getOrDefault(s.id, new long[1])[0], amount.getOrDefault(s.id, BigDecimal.ZERO)));
        model.addAttribute("rows", rows);
        model.addAttribute("activePage", "suppliers");
        return "pur-suppliers";
    }

    @PostMapping("/Suppliers")
    @Transactional
    public String saveSupplier(@RequestParam(required = false) Long id, @RequestParam String name, @RequestParam(required = false) String contactPerson,
                               @RequestParam(required = false) String phone, @RequestParam(required = false) String email,
                               @RequestParam(required = false) String address, @RequestParam(required = false) String tin,
                               @RequestParam(required = false) String paymentTerms, @RequestParam(required = false) String supplies,
                               @RequestParam(required = false) String notes, @RequestParam(defaultValue = "false") boolean active,
                               @RequestParam(required = false) String vatRegistered, @RequestParam(required = false) String ewtRate,
                               RedirectAttributes ra) {
        if (blank(name)) { ra.addFlashAttribute("error", "Enter the supplier's name."); return "redirect:/Purchasing/Suppliers"; }
        Supplier s = id == null ? new Supplier() : suppliers.findById(id).orElse(new Supplier());
        s.name = cut(name, 160); s.contactPerson = cut(contactPerson, 120); s.phone = cut(phone, 60); s.email = cut(email, 160);
        s.address = cut(address, 300); s.tin = cut(tin, 40); s.paymentTerms = cut(paymentTerms, 120); s.supplies = cut(supplies, 300);
        s.notes = cut(notes, 1000); s.active = id == null || active;
        s.vatRegistered = "NO".equals(vatRegistered) ? Boolean.FALSE : "YES".equals(vatRegistered) ? Boolean.TRUE : s.vatRegistered;
        BigDecimal ew = num(ewtRate);
        if (ew != null && ew.signum() >= 0 && ew.compareTo(BigDecimal.valueOf(32)) <= 0) s.ewtRate = ew;
        suppliers.save(s);
        ra.addFlashAttribute("success", (id == null ? "Added " : "Saved ") + s.name + ".");
        return "redirect:/Purchasing/Suppliers";
    }

    // ================================================================ Canvasses

    public record CanvassRow(Canvass canvass, Lookups.JobRef job, int items, long suppliers) {}

    @GetMapping("/Canvasses")
    @Transactional(readOnly = true)
    public String canvasses(Model model) {
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        Map<Long, Integer> itemCount = new HashMap<>();
        for (CanvassItem it : canvassItems.findAll()) itemCount.merge(it.canvassId, 1, Integer::sum);
        Map<Long, Long> supCount = new HashMap<>();
        for (CanvassSupplier s : canvassSuppliers.findAll()) supCount.merge(s.canvassId, 1L, Long::sum);
        List<CanvassRow> rows = new ArrayList<>();
        for (Canvass c : canvasses.findAllByOrderByIdDesc())
            rows.add(new CanvassRow(c, jobs.get(c.jobOrderId), itemCount.getOrDefault(c.id, 0), supCount.getOrDefault(c.id, 0L)));
        model.addAttribute("rows", rows);
        model.addAttribute("jobOrders", lookups.openJobOrders());
        model.addAttribute("activePage", "canvasses");
        return "pur-canvasses";
    }

    @PostMapping("/Canvasses")
    public String createCanvass(@RequestParam String title, @RequestParam(required = false) String jobOrderId,
                                @RequestParam(required = false) String neededBy, @RequestParam(required = false) String notes,
                                @RequestParam(required = false) String mrNo, RedirectAttributes ra) {
        if (blank(title)) { ra.addFlashAttribute("error", "Enter what the canvass is for."); return "redirect:/Purchasing/Canvasses"; }
        Canvass c = purchasing.createCanvass(cut(title, 200), id(jobOrderId), date(neededBy), cut(notes, 1000), mrNo, user());
        ra.addFlashAttribute("success", "Canvass " + c.number + " created. Add the items and the suppliers to compare.");
        return "redirect:/Purchasing/Canvasses/" + c.id;
    }

    /** Starts a canvass for a job order with the quoted materials it still has to buy. */
    @PostMapping("/Canvasses/from-job/{jobId}")
    @Transactional
    public String canvassFromJob(@PathVariable Long jobId, RedirectAttributes ra) {
        var job = jobOrderRepo.findWithDetailsById(jobId).orElse(null);
        if (job == null) return "redirect:/JobOrders";
        var lines = jobMaterials.forJobOrder(jobId, job.getQuotation()).getToBuy();
        if (lines.isEmpty()) {
            ra.addFlashAttribute("error", "Nothing left to buy: every quoted material is issued, in stock or on order.");
            return "redirect:/JobOrders/" + jobId;
        }
        String project = job.getQuotation().getInquiry().getProjectName();
        Canvass c = purchasing.createCanvass(cut("Materials for " + job.getJobOrderNumber() + (blank(project) ? "" : " · " + project), 200),
                jobId, job.getQuotation().getInquiry().getTargetStartDate(), "Started from the job order's quoted materials.", user());
        List<Long> ids = new ArrayList<>();
        List<PurchasingService.LineInput> in = new ArrayList<>();
        for (var b : lines) {
            ids.add(null);
            in.add(new PurchasingService.LineInput(b.isLinked() ? b.material().id : null, b.description(), b.toBuy(), b.unit(), null));
        }
        purchasing.saveItems(c, ids, in);
        ra.addFlashAttribute("success", "Canvass " + c.number + " started with " + lines.size() + " item(s) still to buy. Check the quantities, then add the supplier quotes.");
        return "redirect:/Purchasing/Canvasses/" + c.id;
    }

    @GetMapping("/Canvasses/{id}")
    @Transactional(readOnly = true)
    public String canvass(@PathVariable Long id, Model model) {
        Canvass c = canvasses.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        PurchasingService.Grid g = purchasing.grid(c);
        Map<Long, Supplier> sup = lookups.supplierMap();
        Set<Long> inGrid = new HashSet<>();
        for (CanvassSupplier s : g.suppliers()) inGrid.add(s.supplierId);
        List<Supplier> addable = suppliers.findByActiveTrueOrderByNameAsc().stream().filter(s -> !inGrid.contains(s.id)).toList();
        model.addAttribute("c", c);
        model.addAttribute("g", g);
        model.addAttribute("supplierMap", sup);
        model.addAttribute("addable", addable);
        model.addAttribute("job", c.jobOrderId == null ? null : lookups.jobMap().get(c.jobOrderId));
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc());
        model.addAttribute("orders", orders.findByCanvassId(c.id));
        model.addAttribute("draft", "DRAFT".equals(c.status));
        // Per supplier, a JSON list of {i: itemId, p: price, b: brand, n: notAvailable} to fill the edit-quote form
        Map<Long, List<Map<String, Object>>> qp = new HashMap<>();
        for (CanvassItem it : g.items()) for (CanvassSupplier sr : g.suppliers()) {
            BigDecimal p = g.price(it.id, sr.supplierId);
            boolean na = g.isNotAvailable(it.id, sr.supplierId);
            String b = g.brand(it.id, sr.supplierId);
            if (p == null && !na && b == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("i", it.id); if (p != null) m.put("p", p.toPlainString()); if (b != null) m.put("b", b); if (na) m.put("n", true);
            qp.computeIfAbsent(sr.supplierId, x -> new ArrayList<>()).add(m);
        }
        Map<Long, String> quotePrices = new HashMap<>();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var e : qp.entrySet()) {
            try { quotePrices.put(e.getKey(), json.writeValueAsString(e.getValue())); } catch (Exception ignore) { }
        }
        model.addAttribute("quotePrices", quotePrices);
        // sign-off
        var me = auth.getCurrentUser();
        CanvassSignoff so = canvassApprovals.signoff(c.id);
        String pending = canvassApprovals.pendingStep(c, so);
        model.addAttribute("so", so);
        model.addAttribute("pendingStep", pending);
        model.addAttribute("pendingLabel", pending == null ? null : CanvassApprovalService.label(pending));
        model.addAttribute("pendingRole", pending == null ? null : CanvassApprovalService.roleHint(pending));
        model.addAttribute("canSignNow", pending != null && canvassApprovals.canSign(me, pending));
        model.addAttribute("pendingSigners", pending == null ? List.of() : canvassApprovals.signersOf(pending).stream().map(com.asmsi.admin_system.entity.User::getName).toList());
        model.addAttribute("hasSignature", me != null && userSignatures.existsById(me.getId()));
        model.addAttribute("jobs", lookups.openJobOrders());
        model.addAttribute("allSuppliers", suppliers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "canvasses");
        return "pur-canvass";
    }

    @PostMapping("/Canvasses/{id}/items")
    public String canvassItems(@PathVariable Long id, @RequestParam(name = "line_id", required = false) List<String> ids,
                               @RequestParam(name = "line_material", required = false) List<String> mat,
                               @RequestParam(name = "line_desc", required = false) List<String> desc,
                               @RequestParam(name = "line_qty", required = false) List<String> qty,
                               @RequestParam(name = "line_unit", required = false) List<String> unit,
                               @RequestParam(name = "line_remarks", required = false) List<String> remarks, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> {
            List<Long> idList = new ArrayList<>();
            for (int i = 0; i < size(ids, desc); i++) idList.add(Forms.id(at(ids, i)));
            purchasing.saveItems(c, idList, lines(mat, desc, qty, unit, null), remarks == null ? new ArrayList<>() : remarks);
            return "Items saved.";
        });
    }

    @PostMapping("/Canvasses/{id}/suppliers")
    public String canvassAddSupplier(@PathVariable Long id, @RequestParam Long supplierId, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { purchasing.addSupplier(c, supplierId); return "Supplier added. Type their prices in the grid."; });
    }

    @PostMapping("/Canvasses/{id}/suppliers/{sid}/remove")
    public String canvassRemoveSupplier(@PathVariable Long id, @PathVariable Long sid, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { purchasing.removeSupplier(c, sid); return "Supplier removed with their prices."; });
    }

    @PostMapping("/Canvasses/{id}/quotes")
    public String canvassQuotes(@PathVariable Long id, @RequestParam Map<String, String> form, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> {
            Map<String, BigDecimal> p = new HashMap<>();
            Map<Long, Long> awards = new HashMap<>();
            Map<Long, String> terms = new HashMap<>();
            for (var e : form.entrySet()) {
                String k = e.getKey();
                if (k.startsWith("p_")) { String[] parts = k.substring(2).split("_"); if (parts.length == 2) p.put(parts[0] + "|" + parts[1], num(e.getValue())); }
                else if (k.startsWith("award_")) { Long item = Forms.id(k.substring(6)), s = Forms.id(e.getValue()); if (item != null && s != null) awards.put(item, s); }
                else if (k.startsWith("terms_")) { Long s = Forms.id(k.substring(6)); if (s != null) terms.put(s, e.getValue()); }
            }
            purchasing.saveQuotes(c, p, awards, terms, user());
            return "Prices saved.";
        });
    }

    /** One supplier's quotation for the whole canvass (the "Add supplier quote" form). */
    @PostMapping("/Canvasses/{id}/quote")
    public String canvassQuote(@PathVariable Long id, @RequestParam(required = false) String supplierId,
                               @RequestParam(required = false) String newSupplier, @RequestParam(required = false) String quoteRef,
                               @RequestParam(required = false) String quoteDate, @RequestParam(required = false) String deliveryDays,
                               @RequestParam(required = false) String validUntil, @RequestParam(required = false) String terms,
                               @RequestParam(required = false) String vat, @RequestParam(required = false) String paymentMode,
                               @RequestParam(required = false) String termDays, @RequestParam(defaultValue = "false") boolean pdc,
                               @RequestParam Map<String, String> form, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> {
            Long sid = Forms.id(supplierId);
            if (sid == null && !blank(newSupplier)) {
                Supplier s = new Supplier();
                s.name = cut(newSupplier, 160);
                s.paymentTerms = cut(terms, 120);
                sid = suppliers.save(s).id;
            }
            if (sid == null) throw new IllegalStateException("Choose the supplier, or type the name of a new one.");
            Map<Long, BigDecimal> p = new HashMap<>();
            Map<Long, String> brands = new HashMap<>();
            Set<Long> na = new HashSet<>();
            for (var e : form.entrySet()) {
                String k = e.getKey();
                if (k.startsWith("price_")) { Long item = Forms.id(k.substring(6)); if (item != null) p.put(item, num(e.getValue())); }
                else if (k.startsWith("brand_")) { Long item = Forms.id(k.substring(6)); if (item != null && !blank(e.getValue())) brands.put(item, e.getValue().trim()); }
                else if (k.startsWith("na_")) { Long item = Forms.id(k.substring(3)); if (item != null && "true".equals(e.getValue())) na.add(item); }
            }
            na.forEach(p::remove);
            if (p.values().stream().noneMatch(v -> v != null && v.signum() > 0) && na.isEmpty()) throw new IllegalStateException("Enter at least one price, or mark the items they don't have as not available.");
            BigDecimal dd = num(deliveryDays), td = num(termDays);
            Boolean vatIncl = "VAT".equals(vat) ? Boolean.TRUE : "NONVAT".equals(vat) ? Boolean.FALSE : null;
            purchasing.saveSupplierQuote(c, sid, quoteRef, date(quoteDate), dd == null ? null : dd.intValue(), date(validUntil), terms,
                    vatIncl, paymentMode, td == null ? null : td.intValue(), pdc, p, brands, na, user());
            Supplier s = suppliers.findById(sid).orElse(null);
            return "Saved the quote of " + (s == null ? "the supplier" : s.name) + ".";
        });
    }

    @PostMapping("/Canvasses/{id}/choose")
    public String canvassChoose(@PathVariable Long id, @RequestParam Long itemId, @RequestParam Long supplierId, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { purchasing.choose(c, itemId, supplierId); return null; });
    }

    @PostMapping("/Canvasses/{id}/choose-cheapest")
    public String canvassChooseCheapest(@PathVariable Long id, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { purchasing.chooseCheapest(c); return "Every item is now with its cheapest supplier."; });
    }

    @PostMapping("/Canvasses/{id}/choose-supplier")
    public String canvassChooseSupplier(@PathVariable Long id, @RequestParam Long supplierId, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> {
            int n = purchasing.chooseSupplierForAll(c, supplierId);
            Supplier s = suppliers.findById(supplierId).orElse(null);
            return n + " item(s) moved to " + (s == null ? "that supplier" : s.name) + ".";
        });
    }

    /** MR #, title, job order and needed-by date of a draft canvass. */
    @PostMapping("/Canvasses/{id}/header")
    public String canvassHeader(@PathVariable Long id, @RequestParam(required = false) String title, @RequestParam(required = false) String jobOrderId,
                                @RequestParam(required = false) String neededBy, @RequestParam(required = false) String mrNo, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { purchasing.saveHeader(c, title, id(jobOrderId), date(neededBy), mrNo); return "Canvass details saved."; });
    }

    /** Sends the canvass for sign-off: Approved by (QS) → Noted by (Manager). */
    @PostMapping("/Canvasses/{id}/submit")
    public String canvassSubmit(@PathVariable Long id, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { canvassApprovals.submit(c, auth.getCurrentUser()); return "Submitted for approval. The approvers have been notified by email."; });
    }

    @PostMapping("/Canvasses/{id}/reopen")
    public String canvassReopen(@PathVariable Long id, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> { canvassApprovals.reopen(c); return "Canvass reopened for changes. It must be submitted and signed again."; });
    }

    /** The canvass sheet in the company's paper format (CBM-CD-006), for printing or saving as PDF. */
    @GetMapping("/Canvasses/{id}/print")
    @Transactional(readOnly = true)
    public String canvassPrint(@PathVariable Long id, Model model) {
        Canvass c = canvasses.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        canvassSheet.fill(model, c);
        return "pur-canvass-print";
    }

    @PostMapping("/Canvasses/{id}/orders")
    public String canvassOrders(@PathVariable Long id, RedirectAttributes ra) {
        return withCanvass(id, ra, c -> {
            List<PurchaseOrder> made = purchasing.createOrders(c, user());
            return "Created " + made.size() + " draft purchase order(s): " + String.join(", ", made.stream().map(p -> p.number).toList()) + ". Review and place each one.";
        });
    }

    @PostMapping("/Canvasses/{id}/delete")
    @Transactional
    public String canvassDelete(@PathVariable Long id, RedirectAttributes ra) {
        Canvass c = canvasses.findById(id).orElse(null);
        if (c == null) return "redirect:/Purchasing/Canvasses";
        if ("ORDERED".equals(c.status)) { ra.addFlashAttribute("error", "Purchase orders were made from this canvass; it can't be deleted."); return "redirect:/Purchasing/Canvasses/" + id; }
        quotes.deleteAll(quotes.findByCanvassId(id));
        canvassSuppliers.deleteAll(canvassSuppliers.findByCanvassIdOrderByIdAsc(id));
        canvassItems.deleteAll(canvassItems.findByCanvassIdOrderBySortOrderAscIdAsc(id));
        canvasses.delete(c);
        ra.addFlashAttribute("success", "Deleted canvass " + c.number + ".");
        return "redirect:/Purchasing/Canvasses";
    }

    private String withCanvass(Long id, RedirectAttributes ra, java.util.function.Function<Canvass, String> action) {
        Canvass c = canvasses.findById(id).orElse(null);
        if (c == null) return "redirect:/Purchasing/Canvasses";
        try { String msg = action.apply(c); if (msg != null) ra.addFlashAttribute("success", msg); }
        catch (IllegalStateException | IllegalArgumentException | InventoryService.StockException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/Purchasing/Canvasses/" + id + "#compare";
    }

    // ================================================================ Purchase orders

    public record PoRow(PurchaseOrder po, Supplier supplier, Lookups.JobRef job, BigDecimal total, int lines, BigDecimal receivedPct) {}

    @GetMapping("/Procurements")
    @Transactional(readOnly = true)
    public String procurements(@RequestParam(defaultValue = "open") String show, Model model) {
        List<PurchaseOrder> all = orders.findAllByOrderByIdDesc();
        Map<Long, List<PurchaseOrderLine>> lines = new HashMap<>();
        for (PurchaseOrderLine l : orderLines.findAll()) lines.computeIfAbsent(l.poId, k -> new ArrayList<>()).add(l);
        Map<Long, Supplier> sup = lookups.supplierMap();
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        List<PoRow> rows = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (PurchaseOrder p : all) {
            counts.merge(p.status, 1, Integer::sum);
            boolean open = List.of("DRAFT", "ORDERED", "PARTIAL").contains(p.status);
            if ("open".equals(show) && !open) continue;
            if (!"open".equals(show) && !"all".equals(show) && !show.equalsIgnoreCase(p.status)) continue;
            List<PurchaseOrderLine> ls = lines.getOrDefault(p.id, List.of());
            BigDecimal q = BigDecimal.ZERO, r = BigDecimal.ZERO;
            for (PurchaseOrderLine l : ls) { q = q.add(l.qty); r = r.add(l.receivedQty.min(l.qty)); }
            BigDecimal pct = q.signum() == 0 ? BigDecimal.ZERO : r.multiply(BigDecimal.valueOf(100)).divide(q, 0, RoundingMode.DOWN);
            rows.add(new PoRow(p, sup.get(p.supplierId), jobs.get(p.jobOrderId), PurchasingService.subtotal(ls), ls.size(), pct));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("show", show);
        model.addAttribute("counts", counts);
        model.addAttribute("activePage", "procurements");
        return "pur-orders";
    }

    @GetMapping("/Procurements/new")
    @Transactional(readOnly = true)
    public String newPo(@RequestParam(required = false) Long jobOrder, @RequestParam(required = false) Long supplier, Model model) {
        PurchaseOrder po = new PurchaseOrder();
        po.orderDate = LocalDate.now(); po.jobOrderId = jobOrder; po.supplierId = supplier;
        return poForm(po, List.of(), model);
    }

    @GetMapping("/Procurements/{id}")
    @Transactional(readOnly = true)
    public String po(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean edit, Model model) {
        PurchaseOrder po = orders.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        List<PurchaseOrderLine> ls = orderLines.findByPoIdOrderBySortOrderAscIdAsc(id);
        if (po.isEditable() && (edit || ls.isEmpty())) return poForm(po, ls, model);
        poModel(po, ls, model);
        model.addAttribute("receipts", receipts.findByPoIdOrderByIdDesc(id));
        model.addAttribute("payBlock", paymentGate != null && po.isEditable() ? paymentGate.blockReason(po) : null);
        model.addAttribute("payStatus", paymentGate != null ? paymentGate.paymentStatus(po) : null);
        return "pur-order";
    }

    @GetMapping("/Procurements/{id}/print")
    @Transactional(readOnly = true)
    public String printPo(@PathVariable Long id, Model model) {
        PurchaseOrder po = orders.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        poModel(po, orderLines.findByPoIdOrderBySortOrderAscIdAsc(id), model);
        return "pur-order-print";
    }

    private void poModel(PurchaseOrder po, List<PurchaseOrderLine> ls, Model model) {
        BigDecimal sub = PurchasingService.subtotal(ls);
        BigDecimal tax = sub.multiply(po.taxRate == null ? BigDecimal.ZERO : po.taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        model.addAttribute("po", po);
        model.addAttribute("lines", ls);
        model.addAttribute("subtotal", sub);
        model.addAttribute("tax", tax);
        model.addAttribute("total", sub.add(tax));
        model.addAttribute("supplier", po.supplierId == null ? null : suppliers.findById(po.supplierId).orElse(null));
        model.addAttribute("job", po.jobOrderId == null ? null : lookups.jobMap().get(po.jobOrderId));
        model.addAttribute("canvass", po.canvassId == null ? null : canvasses.findById(po.canvassId).orElse(null));
        model.addAttribute("activePage", "procurements");
    }

    private String poForm(PurchaseOrder po, List<PurchaseOrderLine> ls, Model model) {
        model.addAttribute("po", po);
        model.addAttribute("lines", ls);
        model.addAttribute("suppliers", suppliers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("jobOrders", lookups.openJobOrders());
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc());
        model.addAttribute("activePage", "procurements");
        return "pur-order-form";
    }

    @PostMapping("/Procurements")
    public String savePo(@RequestParam(required = false) Long id, @RequestParam(required = false) String supplierId,
                         @RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String orderDate,
                         @RequestParam(required = false) String expectedDate, @RequestParam(required = false) String deliverTo,
                         @RequestParam(required = false) String paymentTerms, @RequestParam(required = false) String taxRate,
                         @RequestParam(required = false) String notes, @RequestParam(defaultValue = "save") String action,
                         @RequestParam(required = false) String paymentMode, @RequestParam(required = false) String termDays,
                         @RequestParam(required = false) String vatInclusive, @RequestParam(defaultValue = "false") boolean pdc,
                         @RequestParam(name = "line_material", required = false) List<String> mat,
                         @RequestParam(name = "line_desc", required = false) List<String> desc,
                         @RequestParam(name = "line_qty", required = false) List<String> qty,
                         @RequestParam(name = "line_unit", required = false) List<String> unit,
                         @RequestParam(name = "line_price", required = false) List<String> price, RedirectAttributes ra) {
        PurchaseOrder po = id == null ? new PurchaseOrder() : orders.findById(id).orElse(null);
        if (po == null) return "redirect:/Purchasing/Procurements";
        Long sid = Forms.id(supplierId);
        if (sid == null) { ra.addFlashAttribute("error", "Choose the supplier."); return id == null ? "redirect:/Purchasing/Procurements/new" : "redirect:/Purchasing/Procurements/" + id + "?edit=true"; }
        po.supplierId = sid; po.jobOrderId = Forms.id(jobOrderId);
        po.orderDate = Optional.ofNullable(date(orderDate)).orElse(LocalDate.now()); po.expectedDate = date(expectedDate);
        po.deliverTo = cut(deliverTo, 300); po.paymentTerms = cut(paymentTerms, 120);
        BigDecimal t = num(taxRate); po.taxRate = t == null || t.signum() < 0 ? BigDecimal.ZERO : t.min(BigDecimal.valueOf(100));
        po.notes = cut(notes, 1000);
        po.paymentMode = "CASH".equals(paymentMode) || "CHEQUE".equals(paymentMode) ? paymentMode : null;
        BigDecimal td = num(termDays); po.termDays = td == null ? null : td.intValue();
        po.vatInclusive = "true".equals(vatInclusive) ? Boolean.TRUE : "false".equals(vatInclusive) ? Boolean.FALSE : null;
        po.pdc = pdc ? Boolean.TRUE : null;
        try {
            purchasing.savePo(po, lines(mat, desc, qty, unit, price), user());
            if ("order".equals(action)) { purchasing.placeOrder(po, user()); ra.addFlashAttribute("success", "PO " + po.number + " placed. Print it and send it to the supplier."); }
            else ra.addFlashAttribute("success", "PO " + po.number + " saved as draft.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return po.id == null ? "redirect:/Purchasing/Procurements/new" : "redirect:/Purchasing/Procurements/" + po.id + "?edit=true";
        }
        return "redirect:/Purchasing/Procurements/" + po.id;
    }

    @PostMapping("/Procurements/{id}/{action}")
    public String poAction(@PathVariable Long id, @PathVariable String action, RedirectAttributes ra) {
        PurchaseOrder po = orders.findById(id).orElse(null);
        if (po == null) return "redirect:/Purchasing/Procurements";
        try {
            switch (action) {
                case "order" -> { purchasing.placeOrder(po, user()); ra.addFlashAttribute("success", "PO " + po.number + " placed."); }
                case "cancel" -> { purchasing.cancel(po); ra.addFlashAttribute("success", "PO " + po.number + " cancelled."); }
                case "draft" -> { purchasing.backToDraft(po); ra.addFlashAttribute("success", "PO " + po.number + " is a draft again."); }
                case "delete" -> {
                    if (!po.isEditable()) throw new IllegalStateException("Only a draft can be deleted. Cancel it instead.");
                    orderLines.deleteAll(orderLines.findByPoIdOrderBySortOrderAscIdAsc(id)); orders.delete(po);
                    ra.addFlashAttribute("success", "Deleted draft " + po.number + ".");
                    return "redirect:/Purchasing/Procurements";
                }
                default -> { }
            }
        } catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/Purchasing/Procurements/" + id;
    }

    // ================================================================ Market prices

    public record PriceSummary(String key, Material material, String description, String unit, MarketPrice latest,
                               BigDecimal low, BigDecimal high, int count, Supplier lowSupplier) {}

    @GetMapping("/MarketPrices")
    @Transactional(readOnly = true)
    public String marketPrices(@RequestParam(required = false) Long material, @RequestParam(defaultValue = "365") int days, Model model) {
        Map<Long, Supplier> sup = lookups.supplierMap();
        Map<Long, Material> mats = lookups.materialMap();
        List<MarketPrice> all = material != null ? prices.findByMaterialIdOrderByPriceDateDescIdDesc(material) : prices.since(LocalDate.now().minusDays(Math.max(1, days)));
        Map<String, List<MarketPrice>> groups = new LinkedHashMap<>();
        for (MarketPrice p : all) {
            String key = p.materialId != null ? "m" + p.materialId : "d" + p.description.trim().toLowerCase();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<PriceSummary> rows = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<MarketPrice> l = e.getValue();
            MarketPrice latest = l.get(0), low = l.get(0), high = l.get(0);
            for (MarketPrice p : l) { if (p.unitPrice.compareTo(low.unitPrice) < 0) low = p; if (p.unitPrice.compareTo(high.unitPrice) > 0) high = p; }
            Material m = latest.materialId == null ? null : mats.get(latest.materialId);
            rows.add(new PriceSummary(e.getKey(), m, m != null ? m.name : latest.description, latest.unit, latest, low.unitPrice, high.unitPrice, l.size(), sup.get(low.supplierId)));
        }
        rows.sort(Comparator.comparing(r -> r.description().toLowerCase()));
        model.addAttribute("rows", rows);
        model.addAttribute("history", material == null ? List.of() : all);
        model.addAttribute("selected", material == null ? null : mats.get(material));
        model.addAttribute("supplierMap", sup);
        model.addAttribute("days", days);
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc());
        model.addAttribute("suppliers", suppliers.findByActiveTrueOrderByNameAsc());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "market-prices");
        return "pur-prices";
    }

    @PostMapping("/MarketPrices")
    public String addPrice(@RequestParam(required = false) String materialId, @RequestParam(required = false) String description,
                           @RequestParam(required = false) String supplierId, @RequestParam String unitPrice,
                           @RequestParam(required = false) String unit, @RequestParam(required = false) String priceDate,
                           @RequestParam(required = false) String note, RedirectAttributes ra) {
        Long mid = Forms.id(materialId);
        BigDecimal p = num(unitPrice);
        if ((mid == null && blank(description)) || p == null || p.signum() <= 0) {
            ra.addFlashAttribute("error", "Choose the material (or describe the item) and enter a price.");
            return "redirect:/Purchasing/MarketPrices";
        }
        inventory.recordPrice(mid, cut(description, 200), Forms.id(supplierId), p, cut(unit, 20), date(priceDate), "MANUAL", cut(note, 30), user());
        ra.addFlashAttribute("success", "Price recorded.");
        return mid == null ? "redirect:/Purchasing/MarketPrices" : "redirect:/Purchasing/MarketPrices?material=" + mid;
    }
}
