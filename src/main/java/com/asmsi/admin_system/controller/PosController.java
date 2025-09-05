package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Item;
import com.asmsi.admin_system.entity.Sale;
import com.asmsi.admin_system.entity.SaleItem;
import com.asmsi.admin_system.repository.ItemRepository;
import com.asmsi.admin_system.repository.SaleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class POSController {

    private final ItemRepository itemRepo;
    private final SaleRepository saleRepo;
    private final ObjectMapper objectMapper;

    public POSController(ItemRepository itemRepo, SaleRepository saleRepo, ObjectMapper objectMapper) {
        this.itemRepo = itemRepo;
        this.saleRepo = saleRepo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/pos")
    public String posPage(Model model, Authentication auth) {
        // Load all items for the frontend
        List<Item> items = itemRepo.findAll();
        model.addAttribute("items", items);
        
        // Add authentication info
        model.addAttribute("username", auth != null ? auth.getName() : "Unknown");
        
        // Sidebar state
        model.addAttribute("merchandiseExpanded", true);
        model.addAttribute("activePage", "pos");
        
        return "pos";
    }

    @PostMapping("/pos/checkout")
    public String checkout(
            @RequestParam("payload") String payload,
            @RequestParam("cash") BigDecimal cash,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Parse the cart items from JSON
            JsonNode root = objectMapper.readTree(payload);
            JsonNode itemsNode = root.get("items");
            
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() == 0) {
                redirectAttributes.addFlashAttribute("error", "No items in cart");
                return "redirect:/pos";
            }

            // Create the Sale record
            Sale sale = new Sale();
            sale.setCashierName(auth != null ? auth.getName() : "Unknown");
            sale.setCash(cash);
            
            // Generate receipt number
            String receiptNo = generateReceiptNumber();
            sale.setReceiptNo(receiptNo);

            List<SaleItem> saleItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;

            // Process each cart item
            for (JsonNode itemNode : itemsNode) {
                String itemNumber = itemNode.get("itemNumber").asText();
                int quantity = itemNode.get("qty").asInt();

                // Find the item in database
                Optional<Item> optItem = itemRepo.findByItemNumber(itemNumber);
                if (optItem.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Item not found: " + itemNumber);
                    return "redirect:/pos";
                }

                Item item = optItem.get();
                
                // Check stock availability
                if (item.getStocks() == null || item.getStocks() < quantity) {
                    redirectAttributes.addFlashAttribute("error", 
                        "Insufficient stock for item: " + item.getName() + ". Available: " + (item.getStocks() != null ? item.getStocks() : 0));
                    return "redirect:/pos";
                }

                // Create SaleItem with product name captured
                BigDecimal priceAtSale = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                SaleItem saleItem = new SaleItem();
                saleItem.setSale(sale);
                saleItem.setItem(item);
                saleItem.setQuantity(quantity);
                saleItem.setPriceAtSale(priceAtSale);
                
                // IMPORTANT: Capture product details at time of sale
                saleItem.setProductName(item.getName());
                saleItem.setItemNumber(item.getItemNumber());
                
                // Calculate subtotal
                BigDecimal subtotal = priceAtSale.multiply(BigDecimal.valueOf(quantity));
                saleItem.setSubTotal(subtotal);
                
                saleItems.add(saleItem);
                totalAmount = totalAmount.add(subtotal);

                // Update item stock
                item.setStocks(item.getStocks() - quantity);
                itemRepo.save(item);
            }

            // Set totals on the sale
            sale.setTotal(totalAmount);
            sale.setChangeDue(cash.subtract(totalAmount));
            sale.setItems(saleItems);

            // Save the sale (this will cascade to save the sale items)
            Sale savedSale = saleRepo.save(sale);

            // Add success message and sale info for redirect
            redirectAttributes.addFlashAttribute("saleId", savedSale.getId());
            redirectAttributes.addFlashAttribute("recentSale", savedSale);

            return "redirect:/pos";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Checkout failed: " + e.getMessage());
            return "redirect:/pos";
        }
    }

    private String generateReceiptNumber() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // Find the highest receipt number for today
        String prefix = "POS-" + today + "-";
        List<Sale> todaysSales = saleRepo.findByReceiptNoStartingWith(prefix);
        
        int nextNumber = 1;
        if (!todaysSales.isEmpty()) {
            int maxNumber = todaysSales.stream()
                .mapToInt(sale -> {
                    try {
                        String receiptNo = sale.getReceiptNo();
                        String numberPart = receiptNo.substring(prefix.length());
                        return Integer.parseInt(numberPart);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
            nextNumber = maxNumber + 1;
        }
        
        return String.format("%s%06d", prefix, nextNumber);
    }
}