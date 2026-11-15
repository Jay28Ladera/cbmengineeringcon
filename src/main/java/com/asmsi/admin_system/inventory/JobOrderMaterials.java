package com.asmsi.admin_system.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** What purchasing and inventory recorded against one job order: its POs, materials issued and tools out. */
@Service
public class JobOrderMaterials {

    public record PoLine(PurchaseOrder po, Supplier supplier, BigDecimal total) {}
    public record IssueLine(StockIssue issue, BigDecimal total, int items) {}
    public record ToolLine(ToolIssue issue, Tool tool) {}
    /**
     * One quoted material: what the quotation budgeted, what was issued to the job so far, what is in the bodega,
     * what is on order for this job, and what still has to be bought. Free-text lines (not linked to inventory)
     * can't be tracked, so they show the whole quoted quantity as still to buy.
     */
    public record BudgetLine(String description, Material material, BigDecimal quotedQty, String unit, BigDecimal budget,
                             BigDecimal issued, BigDecimal onHand, BigDecimal onOrder, BigDecimal toBuy) {
        public boolean isLinked() { return material != null; }
        /** Share of the quoted quantity already issued, 0–100+. */
        public int getUsedPct() { return quotedQty.signum() == 0 ? 0 : issued.multiply(BigDecimal.valueOf(100)).divide(quotedQty, 0, java.math.RoundingMode.HALF_UP).intValue(); }
    }
    public record Summary(List<PoLine> orders, BigDecimal ordered, List<IssueLine> issues, BigDecimal materialCost, List<ToolLine> toolsOut,
                          List<BudgetLine> budgetLines, BigDecimal materialBudget, List<Canvass> canvasses) {
        public boolean isEmpty() { return orders.isEmpty() && issues.isEmpty() && toolsOut.isEmpty(); }
        public BigDecimal getBudgetLeft() { return materialBudget.subtract(materialCost); }
        public boolean isOverBudget() { return materialBudget.signum() > 0 && materialCost.compareTo(materialBudget) > 0; }
        /** Materials issued as a share of the quoted material cost, 0–100+ (0 when there is no budget). */
        public int getBudgetUsedPct() { return materialBudget.signum() == 0 ? 0 : materialCost.multiply(BigDecimal.valueOf(100)).divide(materialBudget, 0, java.math.RoundingMode.HALF_UP).intValue(); }
        public List<BudgetLine> getToBuy() { return budgetLines.stream().filter(b -> b.toBuy().signum() > 0).toList(); }
    }

    private final PurchaseOrderRepository orders;
    private final PurchaseOrderLineRepository orderLines;
    private final StockIssueRepository issues;
    private final StockIssueLineRepository issueLines;
    private final ToolIssueRepository toolIssues;
    private final ToolRepository tools;
    private final SupplierRepository suppliers;
    private final InventoryService inventory;
    private final StockMovementRepository movements;
    private final MaterialRepository materials;
    private final CanvassRepository canvasses;

    public JobOrderMaterials(PurchaseOrderRepository orders, PurchaseOrderLineRepository orderLines, StockIssueRepository issues,
                             StockIssueLineRepository issueLines, ToolIssueRepository toolIssues, ToolRepository tools,
                             SupplierRepository suppliers, InventoryService inventory, StockMovementRepository movements,
                             MaterialRepository materials, CanvassRepository canvasses) {
        this.orders = orders; this.orderLines = orderLines; this.issues = issues; this.issueLines = issueLines;
        this.toolIssues = toolIssues; this.tools = tools; this.suppliers = suppliers; this.inventory = inventory;
        this.movements = movements; this.materials = materials; this.canvasses = canvasses;
    }

    @Transactional(readOnly = true)
    public Summary forJobOrder(Long jobOrderId) { return forJobOrder(jobOrderId, null); }

    @Transactional(readOnly = true)
    public Summary forJobOrder(Long jobOrderId, com.asmsi.admin_system.entity.Quotation quote) {
        List<PoLine> pos = new ArrayList<>();
        BigDecimal ordered = BigDecimal.ZERO;
        List<PurchaseOrder> poList = orders.findByJobOrderIdOrderByIdDesc(jobOrderId);
        Map<Long, BigDecimal> poTotals = new HashMap<>();
        for (PurchaseOrderLine l : orderLines.findByPoIdIn(poList.stream().map(p -> p.id).toList())) poTotals.merge(l.poId, l.getAmount(), BigDecimal::add);
        for (PurchaseOrder p : poList) {
            BigDecimal t = poTotals.getOrDefault(p.id, BigDecimal.ZERO);
            pos.add(new PoLine(p, p.supplierId == null ? null : suppliers.findById(p.supplierId).orElse(null), t));
            if (!"DRAFT".equals(p.status) && !"CANCELLED".equals(p.status)) ordered = ordered.add(t);
        }
        List<StockIssue> isList = issues.findByJobOrderIdOrderByIdDesc(jobOrderId);
        Map<Long, BigDecimal> isTotals = new HashMap<>();
        Map<Long, Integer> isCount = new HashMap<>();
        for (StockIssueLine l : issueLines.findByIssueIdIn(isList.stream().map(i -> i.id).toList())) { isTotals.merge(l.issueId, l.getAmount(), BigDecimal::add); isCount.merge(l.issueId, 1, Integer::sum); }
        List<IssueLine> is = new ArrayList<>();
        for (StockIssue i : isList) is.add(new IssueLine(i, isTotals.getOrDefault(i.id, BigDecimal.ZERO), isCount.getOrDefault(i.id, 0)));
        List<ToolLine> out = new ArrayList<>();
        for (ToolIssue t : toolIssues.findByJobOrderIdOrderByIdDesc(jobOrderId)) if (t.isOut()) out.add(new ToolLine(t, tools.findById(t.toolId).orElse(null)));
        BigDecimal cost = inventory.materialCostByJobOrder().getOrDefault(jobOrderId, BigDecimal.ZERO);
        // ---- quoted materials vs what happened
        List<BudgetLine> budget = new ArrayList<>();
        BigDecimal materialBudget = BigDecimal.ZERO;
        if (quote != null) {
            Map<Long, BigDecimal> issued = new HashMap<>();
            for (Object[] r : movements.netQtyByMaterialForJob(jobOrderId))
                if (r[0] != null) issued.put((Long) r[0], ((BigDecimal) r[1]).negate().max(BigDecimal.ZERO));
            Map<Long, BigDecimal> onOrder = new HashMap<>();
            Set<Long> openPos = new HashSet<>();
            for (PurchaseOrder p : poList) if ("ORDERED".equals(p.status) || "PARTIAL".equals(p.status)) openPos.add(p.id);
            for (PurchaseOrderLine l : orderLines.findByPoIdIn(openPos))
                if (l.materialId != null) onOrder.merge(l.materialId, l.qty.subtract(l.receivedQty).max(BigDecimal.ZERO), BigDecimal::add);
            // Linked lines are grouped by material so a material quoted twice is tracked once.
            Map<Long, BigDecimal[]> byMaterial = new LinkedHashMap<>();   // qty, budget
            Map<Long, String> firstDesc = new HashMap<>(), firstUnit = new HashMap<>();
            List<com.asmsi.admin_system.entity.QuotationLineItem> freeText = new ArrayList<>();
            for (var it : quote.getLineItems()) {
                if (!it.isMaterial()) continue;
                materialBudget = materialBudget.add(it.getLineCost());
                if (it.getMaterialId() == null) { freeText.add(it); continue; }
                BigDecimal[] acc = byMaterial.computeIfAbsent(it.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                acc[0] = acc[0].add(it.getQuantity()); acc[1] = acc[1].add(it.getLineCost());
                firstDesc.putIfAbsent(it.getMaterialId(), it.getDescription()); firstUnit.putIfAbsent(it.getMaterialId(), it.getUnit());
            }
            Map<Long, Material> mats = new HashMap<>();
            for (Material m : materials.findAllById(byMaterial.keySet())) mats.put(m.id, m);
            for (var e : byMaterial.entrySet()) {
                Material m = mats.get(e.getKey());
                BigDecimal q = e.getValue()[0], used = issued.getOrDefault(e.getKey(), BigDecimal.ZERO);
                BigDecimal have = m == null ? BigDecimal.ZERO : m.onHand.max(BigDecimal.ZERO), coming = onOrder.getOrDefault(e.getKey(), BigDecimal.ZERO);
                BigDecimal buy = q.subtract(used).subtract(have).subtract(coming).max(BigDecimal.ZERO);
                budget.add(new BudgetLine(m != null ? m.name : firstDesc.get(e.getKey()), m, q, m != null ? m.unit : firstUnit.get(e.getKey()),
                        e.getValue()[1], used, have, coming, buy));
            }
            for (var it : freeText)
                budget.add(new BudgetLine(it.getDescription(), null, it.getQuantity(), it.getUnit(), it.getLineCost(),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, it.getQuantity()));
        }
        return new Summary(pos, ordered, is, cost, out, budget, materialBudget, canvasses.findByJobOrderIdOrderByIdDesc(jobOrderId));
    }
}
