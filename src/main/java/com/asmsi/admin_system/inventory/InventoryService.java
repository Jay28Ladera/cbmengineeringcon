package com.asmsi.admin_system.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Every change to stock goes through here, so on-hand quantities, average costs, stock cards,
 * purchase-order deliveries and job-order material costs always agree.
 *
 * <p>Costing is moving average: a delivery re-averages the unit cost of what is on hand; issues and
 * write-offs leave at the current average. Stock can't go below zero.
 */
@Service
public class InventoryService {

    public static class StockException extends RuntimeException {
        public StockException(String message) { super(message); }
    }

    public record ReceiptLineInput(Long poLineId, Long materialId, BigDecimal qty, BigDecimal unitCost) {}
    public record IssueLineInput(Long materialId, BigDecimal qty) {}

    private final MaterialRepository materials;
    private final StockMovementRepository movements;
    private final StockReceiptRepository receipts;
    private final StockReceiptLineRepository receiptLines;
    private final StockIssueRepository issues;
    private final StockIssueLineRepository issueLines;
    private final StockAdjustmentRepository adjustments;
    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final MarketPriceRepository prices;
    private final JdbcTemplate jdbc;

    public InventoryService(MaterialRepository materials, StockMovementRepository movements, StockReceiptRepository receipts,
                            StockReceiptLineRepository receiptLines, StockIssueRepository issues, StockIssueLineRepository issueLines,
                            StockAdjustmentRepository adjustments, PurchaseOrderRepository orders, PurchaseOrderLineRepository orderLines,
                            MarketPriceRepository prices, JdbcTemplate jdbc) {
        this.materials = materials; this.movements = movements; this.receipts = receipts; this.receiptLines = receiptLines;
        this.issues = issues; this.issueLines = issueLines; this.adjustments = adjustments; this.orders = orders;
        this.orderLines = orderLines; this.prices = prices; this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- numbering

    /** Next document number for the year, e.g. PO-2026-0007. */
    public String nextNumber(String table, String prefix) {
        String like = prefix + "-" + LocalDate.now().getYear() + "-";
        List<String> last = jdbc.queryForList("select number from " + table + " where number like ? order by number desc limit 1", String.class, like + "%");
        int n = 1;
        if (!last.isEmpty()) {
            try { n = Integer.parseInt(last.get(0).substring(like.length())) + 1; } catch (NumberFormatException ignore) { }
        }
        return like + String.format("%04d", n);
    }

    public String nextMaterialCode() {
        List<String> last = jdbc.queryForList("select code from inv_material where code ~ '^MAT-[0-9]+$' order by length(code) desc, code desc limit 1", String.class);
        int n = last.isEmpty() ? 1 : Integer.parseInt(last.get(0).substring(4)) + 1;
        return String.format("MAT-%04d", n);
    }

    // ---------------------------------------------------------------- core movement

    /**
     * Moves stock in (qty &gt; 0, at {@code unitCost}) or out (qty &lt; 0, at the average cost).
     * Locks the material row so two people issuing at once can't take the same stock.
     */
    @Transactional
    public StockMovement move(Long materialId, BigDecimal qty, BigDecimal unitCost, String kind, LocalDate on,
                              String refNo, Long jobOrderId, String note, String by) {
        Material m = materials.lockById(materialId).orElseThrow(() -> new StockException("That material no longer exists."));
        if (qty == null || qty.signum() == 0) throw new StockException("Quantity can't be zero.");
        BigDecimal cost;
        if (qty.signum() > 0) {
            cost = unitCost == null || unitCost.signum() < 0 ? m.avgCost : unitCost;
            BigDecimal newQty = m.onHand.add(qty);
            if (m.onHand.signum() <= 0) m.avgCost = cost;
            else m.avgCost = m.onHand.multiply(m.avgCost).add(qty.multiply(cost)).divide(newQty, 4, RoundingMode.HALF_UP);
            m.onHand = newQty;
            if ("RECEIPT".equals(kind)) m.lastCost = cost.setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal out = qty.negate();
            if (m.onHand.compareTo(out) < 0)
                throw new StockException(m.name + ": only " + plain(m.onHand) + " " + m.unit + " on hand, can't take out " + plain(out) + ".");
            cost = unitCost != null && unitCost.signum() >= 0 && !"ISSUE".equals(kind) && !"ADJUSTMENT".equals(kind) ? unitCost : m.avgCost;
            m.onHand = m.onHand.subtract(out);
            if (m.onHand.signum() == 0) { /* keep the last average as the reference cost */ }
        }
        materials.save(m);
        StockMovement s = new StockMovement();
        s.materialId = m.id; s.movedOn = on == null ? LocalDate.now() : on; s.kind = kind; s.qty = qty; s.unitCost = cost;
        s.balanceAfter = m.onHand; s.refNo = refNo; s.jobOrderId = jobOrderId; s.note = note; s.createdBy = by;
        return movements.save(s);
    }

    // ---------------------------------------------------------------- receiving

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;
    /** VAT-registered company: stock bought at VAT-inclusive prices is valued without the 12% VAT (it is claimed as input VAT). */
    @org.springframework.beans.factory.annotation.Value("${app.accounting.vat-registered:true}")
    private boolean vatRegistered;
    @org.springframework.beans.factory.annotation.Autowired private SupplierRepository supplierRepo;
    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.12");

    private void publish(StockEvent e) { if (events != null && e.amount() != null && e.amount().signum() != 0) events.publishEvent(e); }

    @Transactional
    public StockReceipt receive(StockReceipt r, List<ReceiptLineInput> lines, String by) {
        List<ReceiptLineInput> use = lines.stream().filter(l -> l.materialId() != null && l.qty() != null && l.qty().signum() > 0).toList();
        if (use.isEmpty()) throw new StockException("Enter at least one item with a quantity received.");
        PurchaseOrder po = r.poId == null ? null : orders.findById(r.poId).orElse(null);
        if (po != null && !po.isReceivable()) throw new StockException("PO " + po.number + " is " + po.status.toLowerCase() + " and can't be received against.");
        Map<Long, PurchaseOrderLine> poLines = new HashMap<>();
        if (po != null) for (PurchaseOrderLine l : orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id)) poLines.put(l.id, l);
        r.number = nextNumber("inv_stock_receipt", "RR");
        r.createdBy = by;
        if (po != null) r.supplierId = po.supplierId;
        receipts.save(r);
        BigDecimal total = BigDecimal.ZERO;
        boolean pricesInclVat = po != null && po.pricesIncludeVat(po.supplierId == null ? null : supplierRepo.findById(po.supplierId).orElse(null));
        for (ReceiptLineInput in : use) {
            PurchaseOrderLine pl = in.poLineId() == null ? null : poLines.get(in.poLineId());
            BigDecimal cost = in.unitCost() != null ? in.unitCost() : (pl != null ? pl.unitPrice : BigDecimal.ZERO);
            if (vatRegistered && po != null && pricesInclVat) cost = cost.divide(VAT_DIVISOR, 4, RoundingMode.HALF_UP);
            total = total.add(cost.multiply(in.qty()));
            StockReceiptLine l = new StockReceiptLine();
            l.receiptId = r.id; l.poLineId = pl == null ? null : pl.id; l.materialId = in.materialId(); l.qty = in.qty(); l.unitCost = cost;
            receiptLines.save(l);
            // Deliveries go into stock; a job order is charged when materials are issued to it.
            move(in.materialId(), in.qty(), cost, "RECEIPT", r.receivedOn, r.number, null, po == null ? null : "PO " + po.number, by);
            if (pl != null) { pl.receivedQty = pl.receivedQty.add(in.qty()); orderLines.save(pl); }
            recordPrice(in.materialId(), null, r.supplierId, cost, null, r.receivedOn, "RECEIPT", r.number, by);
        }
        if (po != null) refreshPoStatus(po);
        publish(new StockEvent("RECEIPT", r.id, r.number, r.receivedOn, null, r.supplierId, total.setScale(2, RoundingMode.HALF_UP), 1, null, by));
        return r;
    }

    /** Takes a delivery back out of stock (wrong entry). Fails if the stock has already been used. */
    @Transactional
    public void voidReceipt(StockReceipt r, String reason, String by) {
        if (r.voided) return;
        for (StockReceiptLine l : receiptLines.findByReceiptId(r.id)) {
            move(l.materialId, l.qty.negate(), l.unitCost, "VOID", LocalDate.now(), r.number, null, "Void: " + reason, by);
            if (l.poLineId != null) orderLines.findById(l.poLineId).ifPresent(pl -> { pl.receivedQty = pl.receivedQty.subtract(l.qty).max(BigDecimal.ZERO); orderLines.save(pl); });
        }
        r.voided = true; r.voidReason = reason;
        receipts.save(r);
        publish(new StockEvent("RECEIPT_VOID", r.id, r.number, LocalDate.now(), null, r.supplierId, BigDecimal.ONE, -1, reason, by));
        if (r.poId != null) orders.findById(r.poId).ifPresent(this::refreshPoStatus);
    }

    @Transactional
    public void refreshPoStatus(PurchaseOrder po) {
        if ("DRAFT".equals(po.status) || "CANCELLED".equals(po.status)) return;
        List<PurchaseOrderLine> lines = orderLines.findByPoIdOrderBySortOrderAscIdAsc(po.id);
        boolean any = lines.stream().anyMatch(l -> l.receivedQty.signum() > 0);
        boolean all = !lines.isEmpty() && lines.stream().allMatch(l -> l.receivedQty.compareTo(l.qty) >= 0);
        po.status = all ? "RECEIVED" : (any ? "PARTIAL" : "ORDERED");
        orders.save(po);
    }

    // ---------------------------------------------------------------- issuing

    @Transactional
    public StockIssue issue(StockIssue i, List<IssueLineInput> lines, String by) {
        Map<Long, BigDecimal> want = new LinkedHashMap<>();
        for (IssueLineInput l : lines) if (l.materialId() != null && l.qty() != null && l.qty().signum() > 0) want.merge(l.materialId(), l.qty(), BigDecimal::add);
        if (want.isEmpty()) throw new StockException("Enter at least one material and quantity.");
        i.number = nextNumber("inv_stock_issue", "IS");
        i.createdBy = by;
        issues.save(i);
        BigDecimal total = BigDecimal.ZERO;
        for (var e : want.entrySet()) {
            StockMovement mv = move(e.getKey(), e.getValue().negate(), null, "ISSUE", i.issuedOn, i.number, i.jobOrderId, i.purpose, by);
            StockIssueLine l = new StockIssueLine();
            l.issueId = i.id; l.materialId = e.getKey(); l.qty = e.getValue(); l.unitCost = mv.unitCost;
            issueLines.save(l);
            total = total.add(mv.unitCost.multiply(e.getValue()));
        }
        publish(new StockEvent("ISSUE", i.id, i.number, i.issuedOn, i.jobOrderId, null, total.setScale(2, RoundingMode.HALF_UP), -1, i.purpose, by));
        return i;
    }

    /** Puts issued materials back into stock at the cost they left with, and takes the cost off the job order. */
    @Transactional
    public void voidIssue(StockIssue i, String reason, String by) {
        if (i.voided) return;
        for (StockIssueLine l : issueLines.findByIssueId(i.id))
            move(l.materialId, l.qty, l.unitCost, "VOID", LocalDate.now(), i.number, i.jobOrderId, "Void: " + reason, by);
        i.voided = true; i.voidReason = reason;
        issues.save(i);
        publish(new StockEvent("ISSUE_VOID", i.id, i.number, LocalDate.now(), i.jobOrderId, null, BigDecimal.ONE, 1, reason, by));
    }

    // ---------------------------------------------------------------- adjustments

    @Transactional
    public StockAdjustment adjust(Long materialId, BigDecimal qty, BigDecimal unitCost, String reason, Long jobOrderId,
                                  String note, LocalDate on, String by) {
        StockAdjustment a = new StockAdjustment();
        a.number = nextNumber("inv_stock_adjustment", "ADJ");
        a.adjustedOn = on == null ? LocalDate.now() : on;
        a.materialId = materialId; a.qty = qty; a.reason = reason; a.jobOrderId = jobOrderId; a.note = note; a.createdBy = by;
        // Returns from a site come back at average cost and reduce that job order's material cost.
        StockMovement mv = move(materialId, qty, qty.signum() > 0 ? unitCost : null, "ADJUSTMENT", a.adjustedOn, a.number,
                "RETURNED".equals(reason) ? jobOrderId : null, reason + (note == null ? "" : ": " + note), by);
        a.unitCost = mv.unitCost;
        adjustments.save(a);
        publish(new StockEvent("ADJUSTMENT", a.id, a.number, a.adjustedOn, "RETURNED".equals(reason) ? jobOrderId : null, null,
                mv.unitCost.multiply(qty.abs()).setScale(2, RoundingMode.HALF_UP), qty.signum(), reason, by));
        return a;
    }

    // ---------------------------------------------------------------- prices

    @Transactional
    public void recordPrice(Long materialId, String description, Long supplierId, BigDecimal price, String unit, LocalDate on,
                            String source, String refNo, String by) {
        if (price == null || price.signum() <= 0) return;
        if (refNo != null) {
            // One price per document, material and supplier; a corrected quote updates it.
            Optional<MarketPrice> same = materialId != null
                    ? prices.findFirstBySourceAndRefNoAndMaterialIdAndSupplierId(source, refNo, materialId, supplierId)
                    : (description == null ? Optional.empty() : prices.findFirstBySourceAndRefNoAndMaterialIdIsNullAndDescriptionAndSupplierId(source, refNo, description, supplierId));
            if (same.isPresent()) {
                MarketPrice p = same.get();
                p.unitPrice = price.setScale(2, RoundingMode.HALF_UP);
                if (on != null) p.priceDate = on;
                prices.save(p);
                return;
            }
        }
        MarketPrice p = new MarketPrice();
        Material m = materialId == null ? null : materials.findById(materialId).orElse(null);
        p.materialId = materialId;
        p.description = description != null && !description.isBlank() ? description : (m != null ? m.name : "Item");
        p.unit = unit != null && !unit.isBlank() ? unit : (m != null ? m.unit : "pc");
        p.supplierId = supplierId; p.unitPrice = price.setScale(2, RoundingMode.HALF_UP);
        p.priceDate = on == null ? LocalDate.now() : on; p.source = source; p.refNo = refNo; p.createdBy = by;
        prices.save(p);
    }

    /** Material cost charged to each job order (issues less returns). */
    public Map<Long, BigDecimal> materialCostByJobOrder() {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : movements.costByJobOrder()) m.put((Long) r[0], ((BigDecimal) r[1]).negate().setScale(2, RoundingMode.HALF_UP));
        return m;
    }

    static String plain(BigDecimal v) { return v == null ? "0" : v.stripTrailingZeros().toPlainString(); }
}
