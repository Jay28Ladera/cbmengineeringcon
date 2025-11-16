package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ItemInventoryController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @Autowired
    private ShopRepository shopRepository;

    @GetMapping("/schoolitems")
    public String showSchoolItemsPage(
            @RequestParam(value = "shop", required = false) String shop,
            Model model) {

        // ------- auth / header -------
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            String username = auth.getName();
            String role = auth.getAuthorities().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("ROLE_USER");

            model.addAttribute("username", username);
            model.addAttribute("role", role);
        } else {
            model.addAttribute("username", "Guest");
            model.addAttribute("role", "Unknown");
        }

        // ------- items (filtered in Java instead of findByShops) -------
        List<SchoolItem> items = schoolItemRepository.findAll();

        if (shop != null && !shop.isBlank()) {
            String shopTrimmed = shop.trim();

            items = items.stream()
                    .filter(i -> {
                        String s = i.getShops();   // field name in SchoolItem
                        if (s == null) return false;
                        return shopTrimmed.equalsIgnoreCase(s.trim());
                    })
                    .collect(Collectors.toList());
        }

        // all shops for dropdown in schoolitems.html
        List<Shop> allShops = shopRepository.findAll();

        model.addAttribute("items", items);
        model.addAttribute("allShops", allShops);
        model.addAttribute("selectedShop", shop);   // optional if you want it
        model.addAttribute("activePage", "inventory-school-items");

        return "schoolitems";
    }
}
