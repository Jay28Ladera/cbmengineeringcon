package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Item;
import com.asmsi.admin_system.repository.ItemRepository;
import com.asmsi.admin_system.repository.SaleItemRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class InventoryController {

    private final ItemRepository itemRepo;
    private final SaleItemRepository saleItemRepo;

    public InventoryController(ItemRepository itemRepo, SaleItemRepository saleItemRepo) {
        this.itemRepo = itemRepo;
        this.saleItemRepo = saleItemRepo;
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("activePage", "inventory");
        model.addAttribute("merchandiseExpanded", true);

        List<Item> items = itemRepo.findAll();
        model.addAttribute("items", items);

        Map<Long, Long> soldCounts = new HashMap<>();
        for (Object[] row : saleItemRepo.totalSoldByItem()) {
            Long itemId = (Long) row[0];
            Long qty = row[1] == null ? 0L : ((Number) row[1]).longValue();
            soldCounts.put(itemId, qty);
        }
        model.addAttribute("soldCounts", soldCounts);

        return "iteminventory";
    }

    @PostMapping("/inventory/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String itemNumber,
                       @RequestParam String name,
                       @RequestParam Integer stocks,
                       @RequestParam BigDecimal price,
                       @RequestParam(required = false) String imageUrl) {

        Item item = (id != null) ? itemRepo.findById(id).orElse(new Item()) : new Item();

        if (item.getId() == null && itemRepo.existsByItemNumber(itemNumber)) {
            throw new IllegalArgumentException("Item number already exists: " + itemNumber);
        }

        item.setItemNumber(itemNumber.trim());
        item.setName(name.trim());
        item.setStocks(stocks);
        item.setPrice(price);
        item.setImageUrl((imageUrl == null || imageUrl.isBlank()) ? null : imageUrl.trim());

        itemRepo.save(item);
        return "redirect:/inventory";
    }

    /** Hard delete: remove sale_items first, then the item. */
    @Transactional
    @RequestMapping(value = "/inventory/delete/{id}", method = {RequestMethod.GET, RequestMethod.POST})
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        if (!itemRepo.existsById(id)) {
            ra.addFlashAttribute("error", "Item not found.");
            return "redirect:/inventory";
        }

        // 1) remove FK dependents
        saleItemRepo.deleteByItem_Id(id);

        // 2) delete the item
        itemRepo.deleteById(id);

        ra.addFlashAttribute("msg", "Item deleted.");
        return "redirect:/inventory";
    }

    // NEW: CSV Export method
    @GetMapping("/inventory/export-csv")
    public void exportInventoryCsv(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        
        // Generate filename with current date
        String filename = "inventory_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        
        // Get all items (same as the main inventory method)
        List<Item> items = itemRepo.findAll();
        
        // Calculate sold quantities (same logic as the main inventory method)
        Map<Long, Long> soldCounts = new HashMap<>();
        for (Object[] row : saleItemRepo.totalSoldByItem()) {
            Long itemId = (Long) row[0];
            Long qty = row[1] == null ? 0L : ((Number) row[1]).longValue();
            soldCounts.put(itemId, qty);
        }
        
        try (PrintWriter out = response.getWriter()) {
            // CSV Header
            out.println("item_number,name,stocks,sold,price,image_url");
            
            // CSV Data
            for (Item item : items) {
                Long soldCount = soldCounts.getOrDefault(item.getId(), 0L);
                
                out.printf("%s,%s,%d,%d,%.2f,%s%n",
                    safeCsv(item.getItemNumber()),
                    safeCsv(item.getName()),
                    item.getStocks() != null ? item.getStocks() : 0,
                    soldCount,
                    item.getPrice() != null ? item.getPrice().doubleValue() : 0.00,
                    safeCsv(item.getImageUrl())
                );
            }
        }
    }

    // Helper method for CSV safety
    private String safeCsv(String value) {
        if (value == null) return "";
        // Escape commas and quotes for proper CSV formatting
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}