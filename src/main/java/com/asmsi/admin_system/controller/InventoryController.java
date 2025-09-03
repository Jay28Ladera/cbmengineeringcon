package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Item;
import com.asmsi.admin_system.repository.ItemRepository;
import com.asmsi.admin_system.repository.SaleItemRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
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
}
