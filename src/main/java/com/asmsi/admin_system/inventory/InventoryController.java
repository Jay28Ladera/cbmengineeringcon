package com.asmsi.admin_system.inventory;

import com.asmsi.admin_system.entity.Employee;
import com.asmsi.admin_system.repository.EmployeeRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.asmsi.admin_system.inventory.Forms.*;

/** Inventory › Categories, Materials, Stock Receiving, Stock Issuance, Stock Adjustment, Tools & Equipment. */
@Controller
@RequestMapping("/Inventory")
public class InventoryController {

    private final MaterialCategoryRepository categories;
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
    private final ToolRepository tools;
    private final ToolIssueRepository toolIssues;
    private final EmployeeRepository employees;
    private final InventoryService inventory;
    private final Lookups lookups;

    public InventoryController(MaterialCategoryRepository categories, MaterialRepository materials, StockMovementRepository movements,
                               StockReceiptRepository receipts, StockReceiptLineRepository receiptLines, StockIssueRepository issues,
                               StockIssueLineRepository issueLines, StockAdjustmentRepository adjustments, PurchaseOrderRepository orders,
                               PurchaseOrderLineRepository orderLines, MarketPriceRepository prices, ToolRepository tools,
                               ToolIssueRepository toolIssues, EmployeeRepository employees, InventoryService inventory, Lookups lookups) {
        this.categories = categories; this.materials = materials; this.movements = movements; this.receipts = receipts;
        this.receiptLines = receiptLines; this.issues = issues; this.issueLines = issueLines; this.adjustments = adjustments;
        this.orders = orders; this.orderLines = orderLines; this.prices = prices; this.tools = tools; this.toolIssues = toolIssues;
        this.employees = employees; this.inventory = inventory; this.lookups = lookups;
    }

    // ================================================================ Categories

    public record CategoryRow(MaterialCategory category, long materials) {}

    @GetMapping("/Categories")
    @Transactional(readOnly = true)
    public String categories(Model model) {
        Map<Long, Long> count = new HashMap<>();
        for (Material m : materials.findAll()) if (m.categoryId != null) count.merge(m.categoryId, 1L, Long::sum);
        List<CategoryRow> rows = new ArrayList<>();
        for (MaterialCategory c : categories.findAllByOrderByNameAsc()) rows.add(new CategoryRow(c, count.getOrDefault(c.id, 0L)));
        model.addAttribute("rows", rows);
        model.addAttribute("activePage", "inventory-categories");
        return "inv-categories";
    }

    @PostMapping("/Categories")
    @Transactional
    public String saveCategory(@RequestParam(required = false) Long id, @RequestParam String name, @RequestParam(required = false) String description, RedirectAttributes ra) {
        if (blank(name)) { ra.addFlashAttribute("error", "Enter the category name."); return "redirect:/Inventory/Categories"; }
        MaterialCategory c = id == null ? new MaterialCategory() : categories.findById(id).orElse(new MaterialCategory());
        boolean renamed = c.name == null || !c.name.equalsIgnoreCase(name.trim());
        if (renamed && categories.existsByNameIgnoreCase(name.trim())) { ra.addFlashAttribute("error", "There is already a category named " + name.trim() + "."); return "redirect:/Inventory/Categories"; }
        c.name = cut(name, 80); c.description = cut(description, 300);
        categories.save(c);
        ra.addFlashAttribute("success", "Saved " + c.name + ".");
        return "redirect:/Inventory/Categories";
    }

    @PostMapping("/Categories/{id}/delete")
    @Transactional
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        if (materials.countByCategoryId(id) > 0) ra.addFlashAttribute("error", "Move its materials to another category first.");
        else { categories.deleteById(id); ra.addFlashAttribute("success", "Category deleted."); }
        return "redirect:/Inventory/Categories";
    }

    // ================================================================ Materials

    @GetMapping("/Materials")
    @Transactional(readOnly = true)
    public String materials(Model model) {
        List<Material> all = materials.findAllByOrderByNameAsc();
        BigDecimal value = BigDecimal.ZERO;
        for (Material m : all) if (m.active) value = value.add(m.getStockValue());
        model.addAttribute("materials", all);
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        model.addAttribute("categoryMap", lookups.categoryMap());
        model.addAttribute("stockValue", value);
        model.addAttribute("lowCount", all.stream().filter(m -> m.active && m.isLow()).count());
        model.addAttribute("activeCount", all.stream().filter(m -> m.active).count());
        model.addAttribute("nextCode", inventory.nextMaterialCode());
        model.addAttribute("activePage", "materials");
        return "inv-materials";
    }

    @PostMapping("/Materials")
    @Transactional
    public String saveMaterial(@RequestParam(required = false) Long id, @RequestParam(required = false) String code, @RequestParam String name,
                               @RequestParam(required = false) String categoryId, @RequestParam(required = false) String unit,
                               @RequestParam(required = false) String reorderLevel, @RequestParam(required = false) String location,
                               @RequestParam(required = false) String notes, @RequestParam(defaultValue = "false") boolean active,
                               @RequestParam(required = false) String openingQty, @RequestParam(required = false) String openingCost,
                               RedirectAttributes ra) {
        if (blank(name)) { ra.addFlashAttribute("error", "Enter the material name."); return "redirect:/Inventory/Materials"; }
        Material m = id == null ? new Material() : materials.findById(id).orElse(null);
        if (m == null) return "redirect:/Inventory/Materials";
        String c = blank(code) ? (m.code != null ? m.code : inventory.nextMaterialCode()) : cut(code, 40);
        if (!c.equals(m.code) && materials.existsByCode(c)) { ra.addFlashAttribute("error", "Code " + c + " is already used."); return "redirect:/Inventory/Materials"; }
        m.code = c; m.name = cut(name, 200); m.categoryId = Forms.id(categoryId); m.unit = blank(unit) ? "pc" : cut(unit, 20);
        BigDecimal r = num(reorderLevel); m.reorderLevel = r == null || r.signum() <= 0 ? null : r;
        m.location = cut(location, 120); m.notes = cut(notes, 600); m.active = id == null || active;
        materials.save(m);
        BigDecimal oq = num(openingQty);
        if (id == null && oq != null && oq.signum() > 0) {
            BigDecimal oc = num(openingCost);
            inventory.adjust(m.id, oq, oc == null ? BigDecimal.ZERO : oc, "OPENING", null, "Opening balance", LocalDate.now(), user());
        }
        ra.addFlashAttribute("success", (id == null ? "Added " : "Saved ") + m.code + " " + m.name + ".");
        return "redirect:/Inventory/Materials";
    }

    public record CardRow(StockMovement move, Lookups.JobRef job) {}

    @GetMapping("/Materials/{id}")
    @Transactional(readOnly = true)
    public String material(@PathVariable Long id, Model model) {
        Material m = materials.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        List<CardRow> card = new ArrayList<>();
        for (StockMovement s : movements.findByMaterialIdOrderByIdDesc(id)) card.add(new CardRow(s, jobs.get(s.jobOrderId)));
        model.addAttribute("m", m);
        model.addAttribute("category", m.categoryId == null ? null : lookups.categoryMap().get(m.categoryId));
        model.addAttribute("card", card);
        model.addAttribute("prices", prices.findByMaterialIdOrderByPriceDateDescIdDesc(id).stream().limit(10).toList());
        model.addAttribute("supplierMap", lookups.supplierMap());
        model.addAttribute("categories", categories.findAllByOrderByNameAsc());
        model.addAttribute("activePage", "materials");
        return "inv-material";
    }

    // ================================================================ Stock receiving

    public record ReceiptRow(StockReceipt receipt, Supplier supplier, PurchaseOrder po, BigDecimal total, int lines) {}

    @GetMapping("/StockReceiving")
    @Transactional(readOnly = true)
    public String receiving(Model model) {
        List<StockReceipt> all = receipts.findAllByOrderByIdDesc();
        Map<Long, List<StockReceiptLine>> ls = new HashMap<>();
        for (StockReceiptLine l : receiptLines.findByReceiptIdIn(all.stream().map(r -> r.id).toList())) ls.computeIfAbsent(l.receiptId, k -> new ArrayList<>()).add(l);
        Map<Long, Supplier> sup = lookups.supplierMap();
        Map<Long, PurchaseOrder> pos = new HashMap<>();
        for (PurchaseOrder p : orders.findAll()) pos.put(p.id, p);
        List<ReceiptRow> rows = new ArrayList<>();
        for (StockReceipt r : all) {
            BigDecimal t = BigDecimal.ZERO;
            for (StockReceiptLine l : ls.getOrDefault(r.id, List.of())) t = t.add(l.getAmount());
            rows.add(new ReceiptRow(r, sup.get(r.supplierId), pos.get(r.poId), t, ls.getOrDefault(r.id, List.of()).size()));
        }
        List<PurchaseOrder> waiting = orders.findByStatusInOrderByIdDesc(List.of("ORDERED", "PARTIAL"));
        model.addAttribute("rows", rows);
        model.addAttribute("waiting", waiting);
        model.addAttribute("supplierMap", sup);
        model.addAttribute("activePage", "stock-receiving");
        return "inv-receipts";
    }

    @GetMapping("/StockReceiving/new")
    @Transactional(readOnly = true)
    public String newReceipt(@RequestParam(required = false) Long po, Model model) {
        PurchaseOrder p = po == null ? null : orders.findById(po).orElse(null);
        model.addAttribute("po", p);
        model.addAttribute("poLines", p == null ? List.of() : orderLines.findByPoIdOrderBySortOrderAscIdAsc(p.id));
        model.addAttribute("supplier", p == null ? null : lookups.supplierMap().get(p.supplierId));
        model.addAttribute("job", p == null || p.jobOrderId == null ? null : lookups.jobMap().get(p.jobOrderId));
        model.addAttribute("suppliers", lookups.supplierMap().values().stream().filter(s -> s.active).sorted(Comparator.comparing(s -> s.name)).toList());
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "stock-receiving");
        return "inv-receipt-form";
    }

    @PostMapping("/StockReceiving")
    public String saveReceipt(@RequestParam(required = false) String poId, @RequestParam(required = false) String supplierId,
                              @RequestParam(required = false) String receivedOn, @RequestParam(required = false) String drNumber,
                              @RequestParam(required = false) String invoiceNumber, @RequestParam(required = false) String receivedBy,
                              @RequestParam(required = false) String notes, @RequestParam(defaultValue = "false") boolean issueToJob,
                              @RequestParam(name = "line_poline", required = false) List<String> poLine,
                              @RequestParam(name = "line_material", required = false) List<String> mat,
                              @RequestParam(name = "line_qty", required = false) List<String> qty,
                              @RequestParam(name = "line_cost", required = false) List<String> cost, RedirectAttributes ra) {
        StockReceipt r = new StockReceipt();
        r.poId = Forms.id(poId); r.supplierId = Forms.id(supplierId);
        r.receivedOn = Optional.ofNullable(date(receivedOn)).orElse(LocalDate.now());
        r.drNumber = cut(drNumber, 60); r.invoiceNumber = cut(invoiceNumber, 60); r.receivedBy = cut(receivedBy, 120); r.notes = cut(notes, 600);
        List<InventoryService.ReceiptLineInput> in = new ArrayList<>();
        for (int i = 0; i < size(mat, qty); i++)
            in.add(new InventoryService.ReceiptLineInput(Forms.id(at(poLine, i)), Forms.id(at(mat, i)), num(at(qty, i)), num(at(cost, i))));
        try {
            inventory.receive(r, in, user());
            String msg = "Received " + r.number + ". Stock is updated.";
            PurchaseOrder po = r.poId == null ? null : orders.findById(r.poId).orElse(null);
            if (issueToJob && po != null && po.jobOrderId != null) {
                StockIssue is = new StockIssue();
                is.issuedOn = r.receivedOn; is.jobOrderId = po.jobOrderId; is.issuedTo = r.receivedBy; is.purpose = "Delivered for PO " + po.number;
                List<InventoryService.IssueLineInput> out = new ArrayList<>();
                for (StockReceiptLine l : receiptLines.findByReceiptId(r.id)) out.add(new InventoryService.IssueLineInput(l.materialId, l.qty));
                inventory.issue(is, out, user());
                msg += " Issued straight to the job order as " + is.number + ".";
            }
            ra.addFlashAttribute("success", msg);
            return "redirect:/Inventory/StockReceiving/" + r.id;
        } catch (InventoryService.StockException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/Inventory/StockReceiving/new" + (r.poId == null ? "" : "?po=" + r.poId);
        }
    }

    @GetMapping("/StockReceiving/{id}")
    @Transactional(readOnly = true)
    public String receipt(@PathVariable Long id, Model model) {
        StockReceipt r = receipts.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        List<StockReceiptLine> ls = receiptLines.findByReceiptId(id);
        BigDecimal t = BigDecimal.ZERO; for (StockReceiptLine l : ls) t = t.add(l.getAmount());
        model.addAttribute("r", r);
        model.addAttribute("lines", ls);
        model.addAttribute("total", t);
        model.addAttribute("materialMap", lookups.materialMap());
        model.addAttribute("supplier", r.supplierId == null ? null : lookups.supplierMap().get(r.supplierId));
        model.addAttribute("po", r.poId == null ? null : orders.findById(r.poId).orElse(null));
        model.addAttribute("activePage", "stock-receiving");
        return "inv-receipt";
    }

    @PostMapping("/StockReceiving/{id}/void")
    public String voidReceipt(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        StockReceipt r = receipts.findById(id).orElse(null);
        if (r == null) return "redirect:/Inventory/StockReceiving";
        if (blank(reason)) { ra.addFlashAttribute("error", "Enter why it's being voided."); return "redirect:/Inventory/StockReceiving/" + id; }
        try { inventory.voidReceipt(r, cut(reason, 300), user()); ra.addFlashAttribute("success", r.number + " voided; the stock was taken back out."); }
        catch (InventoryService.StockException ex) { ra.addFlashAttribute("error", "Can't void: " + ex.getMessage() + " Some of it was already issued or adjusted."); }
        return "redirect:/Inventory/StockReceiving/" + id;
    }

    // ================================================================ Stock issuance

    public record IssueRow(StockIssue issue, Lookups.JobRef job, BigDecimal total, int lines) {}

    @GetMapping("/StockIssuance")
    @Transactional(readOnly = true)
    public String issuance(Model model) {
        List<StockIssue> all = issues.findAllByOrderByIdDesc();
        Map<Long, List<StockIssueLine>> ls = new HashMap<>();
        for (StockIssueLine l : issueLines.findByIssueIdIn(all.stream().map(i -> i.id).toList())) ls.computeIfAbsent(l.issueId, k -> new ArrayList<>()).add(l);
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        List<IssueRow> rows = new ArrayList<>();
        for (StockIssue i : all) {
            BigDecimal t = BigDecimal.ZERO;
            for (StockIssueLine l : ls.getOrDefault(i.id, List.of())) t = t.add(l.getAmount());
            rows.add(new IssueRow(i, jobs.get(i.jobOrderId), t, ls.getOrDefault(i.id, List.of()).size()));
        }
        model.addAttribute("rows", rows);
        model.addAttribute("costByJob", inventory.materialCostByJobOrder());
        model.addAttribute("activePage", "stock-issuance");
        return "inv-issues";
    }

    @GetMapping("/StockIssuance/new")
    @Transactional(readOnly = true)
    public String newIssue(@RequestParam(required = false) Long jobOrder, Model model) {
        model.addAttribute("jobOrders", lookups.openJobOrders());
        model.addAttribute("jobOrderId", jobOrder);
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc().stream().filter(m -> m.onHand.signum() > 0).toList());
        model.addAttribute("employees", employees.findByActiveTrue().stream().sorted(Comparator.comparing(Employee::getFullName)).toList());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "stock-issuance");
        return "inv-issue-form";
    }

    @PostMapping("/StockIssuance")
    public String saveIssue(@RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String issuedOn,
                            @RequestParam(required = false) String issuedTo, @RequestParam(required = false) String purpose,
                            @RequestParam(name = "line_material", required = false) List<String> mat,
                            @RequestParam(name = "line_qty", required = false) List<String> qty, RedirectAttributes ra) {
        StockIssue i = new StockIssue();
        i.jobOrderId = Forms.id(jobOrderId);
        i.issuedOn = Optional.ofNullable(date(issuedOn)).orElse(LocalDate.now());
        i.issuedTo = cut(issuedTo, 120); i.purpose = cut(purpose, 300);
        if (i.jobOrderId == null && blank(purpose)) { ra.addFlashAttribute("error", "Choose the job order, or say what the materials are for."); return "redirect:/Inventory/StockIssuance/new"; }
        List<InventoryService.IssueLineInput> in = new ArrayList<>();
        for (int k = 0; k < size(mat, qty); k++) in.add(new InventoryService.IssueLineInput(Forms.id(at(mat, k)), num(at(qty, k))));
        try {
            inventory.issue(i, in, user());
            ra.addFlashAttribute("success", "Issued " + i.number + ".");
            return "redirect:/Inventory/StockIssuance/" + i.id;
        } catch (InventoryService.StockException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/Inventory/StockIssuance/new" + (i.jobOrderId == null ? "" : "?jobOrder=" + i.jobOrderId);
        }
    }

    @GetMapping("/StockIssuance/{id}")
    @Transactional(readOnly = true)
    public String issue(@PathVariable Long id, Model model) {
        StockIssue i = issues.findById(id).orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        List<StockIssueLine> ls = issueLines.findByIssueId(id);
        BigDecimal t = BigDecimal.ZERO; for (StockIssueLine l : ls) t = t.add(l.getAmount());
        model.addAttribute("i", i);
        model.addAttribute("lines", ls);
        model.addAttribute("total", t);
        model.addAttribute("materialMap", lookups.materialMap());
        model.addAttribute("job", i.jobOrderId == null ? null : lookups.jobMap().get(i.jobOrderId));
        model.addAttribute("activePage", "stock-issuance");
        return "inv-issue";
    }

    @PostMapping("/StockIssuance/{id}/void")
    public String voidIssue(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        StockIssue i = issues.findById(id).orElse(null);
        if (i == null) return "redirect:/Inventory/StockIssuance";
        if (blank(reason)) { ra.addFlashAttribute("error", "Enter why it's being voided."); return "redirect:/Inventory/StockIssuance/" + id; }
        inventory.voidIssue(i, cut(reason, 300), user());
        ra.addFlashAttribute("success", i.number + " voided; the materials are back in stock and off the job order's cost.");
        return "redirect:/Inventory/StockIssuance/" + id;
    }

    // ================================================================ Stock adjustment

    public record AdjRow(StockAdjustment adj, Material material, Lookups.JobRef job) {}

    @GetMapping("/StockAdjustment")
    @Transactional(readOnly = true)
    public String adjustments(Model model) {
        Map<Long, Material> mats = lookups.materialMap();
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        List<AdjRow> rows = new ArrayList<>();
        for (StockAdjustment a : adjustments.findAllByOrderByIdDesc()) rows.add(new AdjRow(a, mats.get(a.materialId), jobs.get(a.jobOrderId)));
        model.addAttribute("rows", rows);
        model.addAttribute("materials", materials.findByActiveTrueOrderByNameAsc());
        model.addAttribute("jobOrders", lookups.jobOrders());
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("activePage", "stock-adjustment");
        return "inv-adjustments";
    }

    @PostMapping("/StockAdjustment")
    public String adjust(@RequestParam String materialId, @RequestParam String direction, @RequestParam String qty,
                         @RequestParam String reason, @RequestParam(required = false) String unitCost,
                         @RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String adjustedOn, RedirectAttributes ra) {
        Long mid = Forms.id(materialId);
        BigDecimal q = num(qty);
        if (mid == null || q == null || q.signum() <= 0) { ra.addFlashAttribute("error", "Choose the material and enter a quantity above zero."); return "redirect:/Inventory/StockAdjustment"; }
        String r = List.of("OPENING", "RECOUNT", "DAMAGED", "LOST", "RETURNED", "OTHER").contains(reason) ? reason : "OTHER";
        if (blank(note) && !"OPENING".equals(r)) { ra.addFlashAttribute("error", "Enter a short note on what happened."); return "redirect:/Inventory/StockAdjustment"; }
        BigDecimal signed = "out".equals(direction) ? q.negate() : q;
        try {
            StockAdjustment a = inventory.adjust(mid, signed, num(unitCost), r, Forms.id(jobOrderId), cut(note, 300), date(adjustedOn), user());
            ra.addFlashAttribute("success", a.number + " saved; stock is updated.");
        } catch (InventoryService.StockException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/Inventory/StockAdjustment";
    }

    // ================================================================ Tools & equipment

    public record ToolRow(Tool tool, ToolIssue out, Lookups.JobRef job) {}

    @GetMapping("/ToolsAndEquipments")
    @Transactional(readOnly = true)
    public String tools(@RequestParam(required = false) Long history, Model model) {
        Map<Long, ToolIssue> out = new HashMap<>();
        for (ToolIssue t : toolIssues.findByReturnedOnIsNull()) out.put(t.toolId, t);
        Map<Long, Lookups.JobRef> jobs = lookups.jobMap();
        List<ToolRow> rows = new ArrayList<>();
        int issued = 0, overdue = 0, due = 0;
        for (Tool t : tools.findAllByOrderByTagAsc()) {
            ToolIssue o = out.get(t.id);
            rows.add(new ToolRow(t, o, o == null ? null : jobs.get(o.jobOrderId)));
            if (o != null) { issued++; if (o.isOverdue()) overdue++; }
            if (t.isMaintenanceDue()) due++;
        }
        model.addAttribute("rows", rows);
        model.addAttribute("issuedCount", issued);
        model.addAttribute("overdueCount", overdue);
        model.addAttribute("maintenanceCount", due);
        model.addAttribute("jobOrders", lookups.openJobOrders());
        model.addAttribute("employees", employees.findByActiveTrue().stream().sorted(Comparator.comparing(Employee::getFullName)).toList());
        model.addAttribute("today", LocalDate.now().toString());
        if (history != null) {
            model.addAttribute("historyTool", tools.findById(history).orElse(null));
            model.addAttribute("historyRows", toolIssues.findByToolIdOrderByIdDesc(history));
            model.addAttribute("jobMap", jobs);
        }
        model.addAttribute("activePage", "tools-equipment");
        return "inv-tools";
    }

    @PostMapping("/ToolsAndEquipments")
    @Transactional
    public String saveTool(@RequestParam(required = false) Long id, @RequestParam String tag, @RequestParam String name,
                           @RequestParam(required = false) String category, @RequestParam(required = false) String brandModel,
                           @RequestParam(required = false) String serialNumber, @RequestParam(required = false) String purchaseDate,
                           @RequestParam(required = false) String cost, @RequestParam(required = false) String condition,
                           @RequestParam(required = false) String location, @RequestParam(required = false) String nextMaintenance,
                           @RequestParam(required = false) String status, @RequestParam(required = false) String notes, RedirectAttributes ra) {
        if (blank(tag) || blank(name)) { ra.addFlashAttribute("error", "Enter the asset tag and the name."); return "redirect:/Inventory/ToolsAndEquipments"; }
        Tool t = id == null ? new Tool() : tools.findById(id).orElse(null);
        if (t == null) return "redirect:/Inventory/ToolsAndEquipments";
        String tg = cut(tag, 40).toUpperCase();
        if (!tg.equals(t.tag) && tools.existsByTag(tg)) { ra.addFlashAttribute("error", "Asset tag " + tg + " is already used."); return "redirect:/Inventory/ToolsAndEquipments"; }
        t.tag = tg; t.name = cut(name, 160); t.category = cut(category, 80); t.brandModel = cut(brandModel, 120); t.serialNumber = cut(serialNumber, 80);
        t.purchaseDate = date(purchaseDate); t.cost = num(cost); t.location = cut(location, 120); t.nextMaintenance = date(nextMaintenance); t.notes = cut(notes, 600);
        if (List.of("GOOD", "FAIR", "NEEDS_REPAIR").contains(condition)) t.condition = condition;
        if (!"ISSUED".equals(t.status) && List.of("AVAILABLE", "MAINTENANCE", "RETIRED").contains(status)) t.status = status;
        tools.save(t);
        ra.addFlashAttribute("success", (id == null ? "Added " : "Saved ") + t.tag + " " + t.name + ".");
        return "redirect:/Inventory/ToolsAndEquipments";
    }

    @PostMapping("/ToolsAndEquipments/{id}/issue")
    @Transactional
    public String issueTool(@PathVariable Long id, @RequestParam(required = false) String employeeId, @RequestParam(required = false) String issuedTo,
                            @RequestParam(required = false) String jobOrderId, @RequestParam(required = false) String issuedOn,
                            @RequestParam(required = false) String expectedReturn, @RequestParam(required = false) String notes, RedirectAttributes ra) {
        Tool t = tools.findById(id).orElse(null);
        if (t == null) return "redirect:/Inventory/ToolsAndEquipments";
        if (!"AVAILABLE".equals(t.status)) { ra.addFlashAttribute("error", t.tag + " is " + t.status.toLowerCase().replace('_', ' ') + " and can't be issued."); return "redirect:/Inventory/ToolsAndEquipments"; }
        Long eid = Forms.id(employeeId);
        String who = eid != null ? employees.findById(eid).map(Employee::getFullName).orElse(null) : cut(issuedTo, 160);
        if (blank(who)) { ra.addFlashAttribute("error", "Choose who receives it."); return "redirect:/Inventory/ToolsAndEquipments"; }
        ToolIssue ti = new ToolIssue();
        ti.toolId = id; ti.employeeId = eid; ti.issuedTo = who; ti.jobOrderId = Forms.id(jobOrderId);
        ti.issuedOn = Optional.ofNullable(date(issuedOn)).orElse(LocalDate.now()); ti.expectedReturn = date(expectedReturn);
        ti.notes = cut(notes, 300); ti.issuedBy = user();
        toolIssues.save(ti);
        t.status = "ISSUED"; tools.save(t);
        ra.addFlashAttribute("success", t.tag + " issued to " + who + ".");
        return "redirect:/Inventory/ToolsAndEquipments";
    }

    @PostMapping("/ToolsAndEquipments/{id}/return")
    @Transactional
    public String returnTool(@PathVariable Long id, @RequestParam(required = false) String returnedOn, @RequestParam(defaultValue = "GOOD") String condition,
                             @RequestParam(required = false) String notes, RedirectAttributes ra) {
        Tool t = tools.findById(id).orElse(null);
        if (t == null) return "redirect:/Inventory/ToolsAndEquipments";
        ToolIssue ti = toolIssues.findByToolIdOrderByIdDesc(id).stream().filter(ToolIssue::isOut).findFirst().orElse(null);
        if (ti == null) { ra.addFlashAttribute("error", t.tag + " isn't checked out."); return "redirect:/Inventory/ToolsAndEquipments"; }
        ti.returnedOn = Optional.ofNullable(date(returnedOn)).orElse(LocalDate.now());
        ti.returnCondition = List.of("GOOD", "FAIR", "NEEDS_REPAIR").contains(condition) ? condition : "GOOD";
        if (!blank(notes)) ti.notes = cut((ti.notes == null ? "" : ti.notes + " · ") + "Return: " + notes.trim(), 300);
        ti.receivedBy = user();
        toolIssues.save(ti);
        t.condition = ti.returnCondition;
        t.status = "NEEDS_REPAIR".equals(ti.returnCondition) ? "MAINTENANCE" : "AVAILABLE";
        tools.save(t);
        ra.addFlashAttribute("success", t.tag + " returned" + ("MAINTENANCE".equals(t.status) ? " and set to maintenance." : "."));
        return "redirect:/Inventory/ToolsAndEquipments";
    }
}
