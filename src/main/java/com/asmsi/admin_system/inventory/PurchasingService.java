package com.asmsi.admin_system.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Canvasses (price comparisons) and purchase orders. */
@Service
public class PurchasingService {

    public record LineInput(Long materialId, String description, BigDecimal qty, String unit, BigDecimal unitPrice) {}

    /** The comparison grid of a canvass, with the figures the page shows. */
    public record Grid(Canvass canvass, List<CanvassItem> items, List<CanvassSupplier> suppliers, Map<String, BigDecimal> prices,
                       Map<Long, Long> winners, Map<Long, BigDecimal> supplierTotals, BigDecimal awardedTotal,
                       Map<Long, Integer> quotedCount, Map<Long, BigDecimal> chosenBySupplier,
                       BigDecimal cheapestTotal, BigDecimal highestTotal, Long bestSingleSupplier, BigDecimal bestSingleTotal,
                       int unpricedItems, Map<String, String> brands, Set<String> notAvailable) {
        public String brand(Long itemId, Long supplierId) { return brands.get(itemId + "|" + supplierId); }
        /** The supplier said they don't carry this item. */
        public boolean isNotAvailable(Long itemId, Long supplierId) { return notAvailable.contains(itemId + "|" + supplierId); }
        public CanvassSupplier supplierRow(Long supplierId) { return suppliers.stream().filter(x -> x.supplierId.equals(supplierId)).findFirst().orElse(null); }
        public BigDecimal price(Long itemId, Long supplierId) { return prices.get(itemId + "|" + supplierId); }
        public BigDecimal amount(Long itemId, Long supplierId) {
            BigDecimal p = price(itemId, supplierId);
            if (p == null) return null;
            for (CanvassItem it : items) if (it.id.equals(itemId)) return p.multiply(it.qty).setScale(2, RoundingMode.HALF_UP);
            return null;
        }
        public boolean isWinner(Long itemId, Long supplierId) { return supplierId.equals(winners.get(itemId)); }
        public boolean isLowest(Long itemId, Long supplierId) {
            BigDecimal p = price(itemId, supplierId);
            if (p == null) return false;
            int quoted = 0;
            for (CanvassSupplier s : suppliers) { BigDecimal o = price(itemId, s.supplierId); if (o != null) { quoted++; if (o.compareTo(p) < 0) return false; } }
            return quoted > 1 || suppliers.size() == 1;
        }
        /** How much more than the cheapest quote this price is, e.g. 3.7 (%), or null. */
        public BigDecimal percentAboveLowest(Long itemId, Long supplierId) {
            BigDecimal p = price(itemId, supplierId), low = null;
            if (p == null) return null;
            for (CanvassSupplier s : suppliers) { BigDecimal o = price(itemId, s.supplierId); if (o != null && (low == null || o.compareTo(low) < 0)) low = o; }
            if (low == null || low.signum() == 0 || p.compareTo(low) == 0) return null;
            return p.subtract(low).multiply(BigDecimal.valueOf(100)).divide(low, 1, RoundingMode.HALF_UP);
        }
        public int quoted(Long supplierId) { return quotedCount.getOrDefault(supplierId, 0); }
        public boolean isComplete(Long supplierId) { return quoted(supplierId) == items.size() && !items.isEmpty(); }
        public BigDecimal chosenTotal(Long supplierId) { return chosenBySupplier.getOrDefault(supplierId, BigDecimal.ZERO); }
        /** Saved by the choices made, against buying every item at its highest quote. */
        public BigDecimal savings() { return highestTotal.subtract(awardedTotal).max(BigDecimal.ZERO); }
        /** True when every priced item is with its cheapest supplier. */
        public boolean allCheapest() { return awardedTotal.compareTo(cheapestTotal) <= 0; }
    }

    private final CanvassRepository canvasses;
    private final CanvassItemRepository items;
    private final CanvassSupplierRepository canvassSuppliers;
    private final CanvassQuoteRepository quotes;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository lines;
    private final SupplierRepository suppliers;
    private final StockReceiptRepository receipts;
    private final InventoryService inventory;

    public PurchasingService(CanvassRepository canvasses, CanvassItemRepository items, CanvassSupplierRepository canvassSuppliers,
                             CanvassQuoteRepository quotes, PurchaseOrderRepository orders, PurchaseOrderLineRepository lines,
                             SupplierRepository suppliers, StockReceiptRepository receipts, InventoryService inventory) {
        this.canvasses = canvasses; this.items = items; this.canvassSuppliers = canvassSuppliers; this.quotes = quotes;
        this.orders = orders; this.lines = lines; this.suppliers = suppliers; this.receipts = receipts; this.inventory = inventory;
    }

    // ---------------------------------------------------------------- canvass

    public Grid grid(Canvass c) {
        List<CanvassItem> its = items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id);
        List<CanvassSupplier> sups = canvassSuppliers.findByCanvassIdOrderByIdAsc(c.id);
        Map<String, BigDecimal> prices = new HashMap<>();
        Map<String, String> brands = new HashMap<>();
        Set<String> na = new HashSet<>();
        for (CanvassQuote q : quotes.findByCanvassId(c.id)) {
            String k = q.itemId + "|" + q.supplierId;
            if (q.brand != null && !q.brand.isBlank()) brands.put(k, q.brand);
            if (q.isNotAvailable()) na.add(k); else prices.put(k, q.unitPrice);
        }
        Map<Long, Long> winners = new HashMap<>();
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, Integer> count = new HashMap<>();
        Map<Long, BigDecimal> chosen = new HashMap<>();
        for (CanvassSupplier s : sups) totals.put(s.supplierId, BigDecimal.ZERO);
        BigDecimal awarded = BigDecimal.ZERO, cheapest = BigDecimal.ZERO, highest = BigDecimal.ZERO;
        int unpriced = 0;
        for (CanvassItem it : its) {
            BigDecimal low = null, high = null;
            Long lowSup = null;
            for (CanvassSupplier s : sups) {
                BigDecimal p = prices.get(it.id + "|" + s.supplierId);
                if (p == null) continue;
                totals.merge(s.supplierId, p.multiply(it.qty), BigDecimal::add);
                count.merge(s.supplierId, 1, Integer::sum);
                if (low == null || p.compareTo(low) < 0) { low = p; lowSup = s.supplierId; }
                if (high == null || p.compareTo(high) > 0) high = p;
            }
            if (low == null) { unpriced++; continue; }
            cheapest = cheapest.add(low.multiply(it.qty));
            highest = highest.add(high.multiply(it.qty));
            Long win = it.awardedSupplierId != null && prices.containsKey(it.id + "|" + it.awardedSupplierId) ? it.awardedSupplierId : lowSup;
            winners.put(it.id, win);
            BigDecimal amt = prices.get(it.id + "|" + win).multiply(it.qty);
            awarded = awarded.add(amt);
            chosen.merge(win, amt.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        }
        totals.replaceAll((k, v) -> v.setScale(2, RoundingMode.HALF_UP));
        Long best = null;
        BigDecimal bestTotal = null;
        for (CanvassSupplier s : sups) {
            if (count.getOrDefault(s.supplierId, 0) != its.size() || its.isEmpty()) continue;
            BigDecimal t = totals.get(s.supplierId);
            if (bestTotal == null || t.compareTo(bestTotal) < 0) { bestTotal = t; best = s.supplierId; }
        }
        return new Grid(c, its, sups, prices, winners, totals, awarded.setScale(2, RoundingMode.HALF_UP), count, chosen,
                cheapest.setScale(2, RoundingMode.HALF_UP), highest.setScale(2, RoundingMode.HALF_UP), best, bestTotal, unpriced, brands, na);
    }

    /**
     * Saves one supplier's quotation: their reference, delivery days, terms and a unit price per item
     * (a blank price = they don't offer that item). Adds the supplier to the canvass if needed.
     */
    @Transactional
    public void saveSupplierQuote(Canvass c, Long supplierId, String quoteRef, LocalDate quoteDate, Integer deliveryDays,
                                  LocalDate validUntil, String terms, Map<Long, BigDecimal> itemPrices, String by) {
        saveSupplierQuote(c, supplierId, quoteRef, quoteDate, deliveryDays, validUntil, terms, null, null, null, false, itemPrices, Map.of(), Set.of(), by);
    }

    /**
     * One supplier's quote as on the canvass sheet: VAT or not, mode of payment (CASH/CHEQUE), terms (days) and PDC,
     * and per item a unit price and brand, or "not available".
     */
    @Transactional
    public void saveSupplierQuote(Canvass c, Long supplierId, String quoteRef, LocalDate quoteDate, Integer deliveryDays,
                                  LocalDate validUntil, String terms, Boolean vatInclusive, String paymentMode, Integer termDays, boolean pdc,
                                  Map<Long, BigDecimal> itemPrices, Map<Long, String> itemBrands, Set<Long> notAvailableItems, String by) {
        requireDraft(c);
        CanvassSupplier cs = canvassSuppliers.findByCanvassIdOrderByIdAsc(c.id).stream().filter(x -> x.supplierId.equals(supplierId)).findFirst().orElse(null);
        if (cs == null) { cs = new CanvassSupplier(); cs.canvassId = c.id; cs.supplierId = supplierId; }
        cs.quoteRef = cut(quoteRef, 60); cs.quoteDate = quoteDate; cs.deliveryDays = deliveryDays == null || deliveryDays < 0 ? null : deliveryDays;
        cs.validUntil = validUntil; cs.terms = cut(terms, 300);
        cs.vatInclusive = vatInclusive;
        cs.paymentMode = "CASH".equals(paymentMode) || "CHEQUE".equals(paymentMode) ? paymentMode : null;
        cs.termDays = termDays == null || termDays < 0 ? null : termDays;
        cs.pdc = pdc ? Boolean.TRUE : null;
        canvassSuppliers.save(cs);
        Map<Long, CanvassQuote> mine = new HashMap<>();
        for (CanvassQuote q : quotes.findByCanvassId(c.id)) if (q.supplierId.equals(supplierId)) mine.put(q.itemId, q);
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id)) {
            BigDecimal p = itemPrices.get(it.id);
            CanvassQuote q = mine.get(it.id);
            String brand = cut(itemBrands.get(it.id), 80);
            if (notAvailableItems.contains(it.id)) {
                if (q == null) { q = new CanvassQuote(); q.canvassId = c.id; q.itemId = it.id; q.supplierId = supplierId; }
                q.unitPrice = BigDecimal.ZERO; q.notAvailable = true; q.brand = brand;
                quotes.save(q);
                if (supplierId.equals(it.awardedSupplierId)) { it.awardedSupplierId = null; items.save(it); }
                continue;
            }
            if (p == null || p.signum() <= 0) {
                if (q != null) quotes.delete(q);
                if (supplierId.equals(it.awardedSupplierId)) { it.awardedSupplierId = null; items.save(it); }
                continue;
            }
            if (q == null) { q = new CanvassQuote(); q.canvassId = c.id; q.itemId = it.id; q.supplierId = supplierId; }
            q.unitPrice = p.setScale(2, RoundingMode.HALF_UP); q.notAvailable = null; q.brand = brand;
            quotes.save(q);
            inventory.recordPrice(it.materialId, it.description, supplierId, q.unitPrice, it.unit, quoteDate == null ? LocalDate.now() : quoteDate, "CANVASS", c.number, by);
        }
    }

    /** Chooses a supplier for one item (their price must exist). */
    @Transactional
    public void choose(Canvass c, Long itemId, Long supplierId) {
        requireDraft(c);
        CanvassItem it = items.findById(itemId).filter(x -> x.canvassId.equals(c.id)).orElseThrow(() -> new IllegalStateException("That item is no longer on the canvass."));
        boolean priced = quotes.findByCanvassId(c.id).stream().anyMatch(q -> q.itemId.equals(itemId) && q.supplierId.equals(supplierId) && !q.isNotAvailable());
        if (!priced) throw new IllegalStateException("That supplier has no price for this item.");
        it.awardedSupplierId = supplierId;
        items.save(it);
    }

    /** Every item goes to its cheapest quote. */
    @Transactional
    public void chooseCheapest(Canvass c) {
        requireDraft(c);
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id)) { it.awardedSupplierId = null; items.save(it); }
    }

    /** One supplier for every item they quoted (fewer deliveries); other items keep their choice. Returns items moved. */
    @Transactional
    public int chooseSupplierForAll(Canvass c, Long supplierId) {
        requireDraft(c);
        Set<Long> quoted = new HashSet<>();
        for (CanvassQuote q : quotes.findByCanvassId(c.id)) if (q.supplierId.equals(supplierId) && !q.isNotAvailable()) quoted.add(q.itemId);
        int n = 0;
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id))
            if (quoted.contains(it.id)) { it.awardedSupplierId = supplierId; items.save(it); n++; }
        return n;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PoPaymentGate paymentGate;

    @Transactional
    public Canvass createCanvass(String title, Long jobOrderId, LocalDate neededBy, String notes, String by) {
        return createCanvass(title, jobOrderId, neededBy, notes, null, by);
    }

    @Transactional
    public Canvass createCanvass(String title, Long jobOrderId, LocalDate neededBy, String notes, String mrNo, String by) {
        Canvass c = new Canvass();
        c.number = nextCanvassNumber();
        c.title = title; c.jobOrderId = jobOrderId; c.neededBy = neededBy; c.notes = notes; c.createdBy = by;
        c.mrNo = blank(mrNo) ? null : cut(mrNo.trim(), 40);
        return canvasses.save(c);
    }

    /** First number for canvass sheets made in the portal: continues the paper series (last paper form was 2285). */
    @org.springframework.beans.factory.annotation.Value("${app.canvass.first-number:2286}")
    private long firstCanvassNumber;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** "Canvass form seq #": a plain running number, one more than the highest so far (never below app.canvass.first-number). */
    public synchronized String nextCanvassNumber() {
        Long max = jdbc.queryForObject("select max(cast(number as bigint)) from pur_canvass where number ~ '^[0-9]{1,15}$'", Long.class);
        return String.valueOf(Math.max(firstCanvassNumber, max == null ? 0 : max + 1));
    }

    /** MR # and the job order / title of a draft canvass. */
    @Transactional
    public void saveHeader(Canvass c, String title, Long jobOrderId, LocalDate neededBy, String mrNo) {
        requireDraft(c);
        if (!blank(title)) c.title = cut(title.trim(), 200);
        c.jobOrderId = jobOrderId; c.neededBy = neededBy;
        c.mrNo = blank(mrNo) ? null : cut(mrNo.trim(), 40);
        canvasses.save(c);
    }

    /** Remarks per item (REMARKS column), by item id. */
    @Transactional
    public void saveRemarks(Canvass c, Map<Long, String> remarks) {
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id))
            if (remarks.containsKey(it.id)) { it.remarks = blank(remarks.get(it.id)) ? null : cut(remarks.get(it.id).trim(), 200); items.save(it); }
    }

    /** Replaces the item list. Quotes for items that remain are kept. */
    @Transactional
    public void saveItems(Canvass c, List<Long> ids, List<LineInput> in) { saveItems(c, ids, in, null); }

    @Transactional
    public void saveItems(Canvass c, List<Long> ids, List<LineInput> in, List<String> remarks) {
        requireDraft(c);
        Map<Long, CanvassItem> existing = new HashMap<>();
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id)) existing.put(it.id, it);
        Set<Long> keep = new HashSet<>();
        int order = 0;
        for (int i = 0; i < in.size(); i++) {
            LineInput l = in.get(i);
            if (l.description() == null || l.description().isBlank() || l.qty() == null || l.qty().signum() <= 0) continue;
            Long id = i < ids.size() ? ids.get(i) : null;
            CanvassItem it = id == null ? null : existing.get(id);
            if (it == null) { it = new CanvassItem(); it.canvassId = c.id; }
            it.materialId = l.materialId(); it.description = cut(l.description(), 200); it.qty = l.qty();
            it.unit = blank(l.unit()) ? "pc" : cut(l.unit(), 20); it.sortOrder = order++;
            if (remarks != null) { String r = i < remarks.size() ? remarks.get(i) : null; it.remarks = blank(r) ? null : cut(r.trim(), 200); }
            items.save(it);
            keep.add(it.id);
        }
        Set<Long> gone = new HashSet<>(existing.keySet()); gone.removeAll(keep);
        if (!gone.isEmpty()) {
            for (CanvassQuote q : quotes.findByCanvassId(c.id)) if (gone.contains(q.itemId)) quotes.delete(q);
            items.deleteAllById(gone);
        }
    }

    @Transactional
    public void addSupplier(Canvass c, Long supplierId) {
        requireDraft(c);
        if (canvassSuppliers.findByCanvassIdOrderByIdAsc(c.id).stream().anyMatch(s -> s.supplierId.equals(supplierId))) return;
        CanvassSupplier s = new CanvassSupplier(); s.canvassId = c.id; s.supplierId = supplierId;
        canvassSuppliers.save(s);
    }

    @Transactional
    public void removeSupplier(Canvass c, Long supplierId) {
        requireDraft(c);
        for (CanvassSupplier s : canvassSuppliers.findByCanvassIdOrderByIdAsc(c.id)) if (s.supplierId.equals(supplierId)) canvassSuppliers.delete(s);
        for (CanvassQuote q : quotes.findByCanvassId(c.id)) if (q.supplierId.equals(supplierId)) quotes.delete(q);
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id)) if (supplierId.equals(it.awardedSupplierId)) { it.awardedSupplierId = null; items.save(it); }
    }

    /** Saves the price grid ("itemId|supplierId" → price), the chosen winners and each supplier's terms. */
    @Transactional
    public void saveQuotes(Canvass c, Map<String, BigDecimal> prices, Map<Long, Long> awards, Map<Long, String> terms, String by) {
        requireDraft(c);
        Map<String, CanvassQuote> existing = new HashMap<>();
        for (CanvassQuote q : quotes.findByCanvassId(c.id)) existing.put(q.itemId + "|" + q.supplierId, q);
        Map<Long, CanvassItem> its = new HashMap<>();
        for (CanvassItem it : items.findByCanvassIdOrderBySortOrderAscIdAsc(c.id)) its.put(it.id, it);
        Set<Long> sups = new HashSet<>();
        for (CanvassSupplier s : canvassSuppliers.findByCanvassIdOrderByIdAsc(c.id)) {
            sups.add(s.supplierId);
            if (terms.containsKey(s.supplierId)) { s.terms = cut(terms.get(s.supplierId), 300); canvassSuppliers.save(s); }
        }
        for (var e : prices.entrySet()) {
            String[] k = e.getKey().split("\\|");
            Long itemId = Long.valueOf(k[0]), supId = Long.valueOf(k[1]);
            if (!its.containsKey(itemId) || !sups.contains(supId)) continue;
            CanvassQuote q = existing.get(e.getKey());
            BigDecimal p = e.getValue();
            if (p == null || p.signum() <= 0) { if (q != null) quotes.delete(q); continue; }
            if (q == null) { q = new CanvassQuote(); q.canvassId = c.id; q.itemId = itemId; q.supplierId = supId; }
            q.unitPrice = p.setScale(2, RoundingMode.HALF_UP);
            quotes.save(q);
            CanvassItem it = its.get(itemId);
            inventory.recordPrice(it.materialId, it.description, supId, q.unitPrice, it.unit, LocalDate.now(), "CANVASS", c.number, by);
        }
        for (CanvassItem it : its.values()) {
            Long w = awards.get(it.id);
            it.awardedSupplierId = w != null && sups.contains(w) ? w : null;
            items.save(it);
        }
    }

    /** Checks a draft canvass can go for sign-off and fixes each item's chosen supplier. */
    @Transactional
    public void lockChoices(Canvass c) {
        requireDraft(c);
        Grid g = grid(c);
        if (g.items().isEmpty()) throw new IllegalStateException("Add the items first.");
        if (g.suppliers().isEmpty()) throw new IllegalStateException("Add at least one supplier quote first.");
        List<String> missing = new ArrayList<>();
        for (CanvassItem it : g.items()) if (!g.winners().containsKey(it.id)) missing.add(it.description);
        if (!missing.isEmpty()) throw new IllegalStateException("No price yet for: " + String.join(", ", missing) + ". Every item needs at least one supplier price.");
        for (CanvassItem it : g.items()) { it.awardedSupplierId = g.winners().get(it.id); items.save(it); }
    }

    /** After the "Noted by" sign-off: purchase orders can now be made. */
    @Transactional
    public void markApproved(Canvass c, String by) {
        c.status = "APPROVED"; c.approvedBy = by; c.approvedAt = LocalDateTime.now();
        canvasses.save(c);
    }

    @Transactional
    public void approve(Canvass c, String by) {
        requireDraft(c);
        Grid g = grid(c);
        if (g.items().isEmpty()) throw new IllegalStateException("Add the items first.");
        List<String> missing = new ArrayList<>();
        for (CanvassItem it : g.items()) if (!g.winners().containsKey(it.id)) missing.add(it.description);
        if (!missing.isEmpty()) throw new IllegalStateException("No price yet for: " + String.join(", ", missing) + ".");
        for (CanvassItem it : g.items()) { it.awardedSupplierId = g.winners().get(it.id); items.save(it); }
        c.status = "APPROVED"; c.approvedBy = by; c.approvedAt = LocalDateTime.now();
        canvasses.save(c);
    }

    @Transactional
    public void reopen(Canvass c) {
        if (!"APPROVED".equals(c.status)) throw new IllegalStateException("Only an approved canvass without purchase orders can be reopened.");
        c.status = "DRAFT"; c.approvedBy = null; c.approvedAt = null;
        canvasses.save(c);
    }

    /** One draft purchase order per winning supplier, with that supplier's items and prices. */
    @Transactional
    public List<PurchaseOrder> createOrders(Canvass c, String by) {
        if (!"APPROVED".equals(c.status)) throw new IllegalStateException("Approve the canvass first.");
        Grid g = grid(c);
        Map<Long, List<CanvassItem>> bySupplier = new LinkedHashMap<>();
        for (CanvassItem it : g.items()) bySupplier.computeIfAbsent(g.winners().get(it.id), k -> new ArrayList<>()).add(it);
        Map<Long, String> terms = new HashMap<>();
        for (CanvassSupplier s : g.suppliers()) terms.put(s.supplierId, s.terms);
        List<PurchaseOrder> made = new ArrayList<>();
        for (var e : bySupplier.entrySet()) {
            PurchaseOrder po = new PurchaseOrder();
            po.number = inventory.nextNumber("pur_purchase_order", "PO");
            po.supplierId = e.getKey(); po.jobOrderId = c.jobOrderId; po.canvassId = c.id;
            po.orderDate = LocalDate.now(); po.expectedDate = c.neededBy; po.createdBy = by;
            Supplier s = suppliers.findById(e.getKey()).orElse(null);
            po.paymentTerms = s == null ? null : s.paymentTerms;
            CanvassSupplier csRow = g.supplierRow(e.getKey());
            if (csRow != null) {
                po.paymentMode = csRow.paymentMode; po.termDays = csRow.termDays; po.pdc = csRow.pdc;
                po.vatInclusive = csRow.vatInclusive != null ? csRow.vatInclusive : (s != null && s.isVat() ? Boolean.TRUE : null);
            }
            CanvassSupplier row = g.supplierRow(e.getKey());
            String pay = row == null ? "" : row.paymentSummary();
            String vat = row == null || row.vatInclusive == null ? "" : (row.vatInclusive ? "VAT inclusive" : "Non-VAT");
            po.notes = "From canvass " + c.number + (c.mrNo == null ? "" : " (MR # " + c.mrNo + ")")
                    + (pay.isEmpty() ? "" : ". Payment: " + pay) + (vat.isEmpty() ? "" : ". " + vat)
                    + (blank(terms.get(e.getKey())) ? "" : ". Supplier terms: " + terms.get(e.getKey()));
            orders.save(po);
            int n = 0;
            for (CanvassItem it : e.getValue()) {
                PurchaseOrderLine l = new PurchaseOrderLine();
                l.poId = po.id; l.materialId = it.materialId; l.description = it.description; l.qty = it.qty; l.unit = it.unit;
                l.unitPrice = g.price(it.id, e.getKey()); l.sortOrder = n++;
                lines.save(l);
            }
            made.add(po);
        }
        c.status = "ORDERED";
        canvasses.save(c);
        return made;
    }

    private static void requireDraft(Canvass c) {
        if (!"DRAFT".equals(c.status)) throw new IllegalStateException("This canvass is " + c.status.toLowerCase() + ". Reopen it to change it.");
    }

    // ---------------------------------------------------------------- purchase orders

    @Transactional
    public PurchaseOrder savePo(PurchaseOrder po, List<LineInput> in, String by) {
        if (po.id != null && !po.isEditable()) throw new IllegalStateException("Only a draft purchase order can be changed.");
        if (po.id == null) { po.number = inventory.nextNumber("pur_purchase_order", "PO"); po.createdBy = by; }
        orders.save(po);
        Map<Long, PurchaseOrderLine> old = new HashMap<>();
        for (PurchaseOrderLine l : lines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) old.put(l.id, l);
        lines.deleteAll(old.values());
        lines.flush();
        int n = 0;
        for (LineInput l : in) {
            if (blank(l.description()) || l.qty() == null || l.qty().signum() <= 0) continue;
            PurchaseOrderLine pl = new PurchaseOrderLine();
            pl.poId = po.id; pl.materialId = l.materialId(); pl.description = cut(l.description(), 200); pl.qty = l.qty();
            pl.unit = blank(l.unit()) ? "pc" : cut(l.unit(), 20);
            pl.unitPrice = l.unitPrice() == null ? BigDecimal.ZERO : l.unitPrice().setScale(2, RoundingMode.HALF_UP);
            pl.sortOrder = n++;
            lines.save(pl);
        }
        return po;
    }

    @Transactional
    public void placeOrder(PurchaseOrder po, String by) {
        if (!po.isEditable()) throw new IllegalStateException("This purchase order was already placed.");
        List<PurchaseOrderLine> ls = lines.findByPoIdOrderBySortOrderAscIdAsc(po.id);
        if (ls.isEmpty()) throw new IllegalStateException("Add at least one item.");
        if (ls.stream().anyMatch(l -> l.unitPrice.signum() <= 0)) throw new IllegalStateException("Every item needs a unit price.");
        if (paymentGate != null) { String why = paymentGate.blockReason(po); if (why != null) throw new IllegalStateException(why); }
        po.status = "ORDERED"; po.orderedBy = by; po.orderedAt = LocalDateTime.now();
        orders.save(po);
        for (PurchaseOrderLine l : ls) inventory.recordPrice(l.materialId, l.description, po.supplierId, l.unitPrice, l.unit, po.orderDate, "PO", po.number, by);
    }

    @Transactional
    public void cancel(PurchaseOrder po) {
        if ("RECEIVED".equals(po.status) || "PARTIAL".equals(po.status))
            throw new IllegalStateException("Deliveries were already received on this PO. Void those receipts first.");
        po.status = "CANCELLED";
        orders.save(po);
    }

    /** Back to draft (from ordered with nothing received, or cancelled). */
    @Transactional
    public void backToDraft(PurchaseOrder po) {
        boolean received = receipts.findByPoIdOrderByIdDesc(po.id).stream().anyMatch(r -> !r.voided);
        if (received) throw new IllegalStateException("Deliveries were already received on this PO.");
        po.status = "DRAFT"; po.orderedAt = null; po.orderedBy = null;
        orders.save(po);
    }

    public static BigDecimal subtotal(List<PurchaseOrderLine> ls) {
        BigDecimal t = BigDecimal.ZERO;
        for (PurchaseOrderLine l : ls) t = t.add(l.getAmount());
        return t;
    }

    static boolean blank(String s) { return s == null || s.isBlank(); }
    static String cut(String s, int n) { if (blank(s)) return null; String t = s.trim(); return t.length() > n ? t.substring(0, n) : t; }
}
