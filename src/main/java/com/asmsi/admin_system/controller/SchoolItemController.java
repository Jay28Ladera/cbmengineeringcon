package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class SchoolItemController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @Autowired
    private ShopRepository shopRepository;

   
    // ---------- SAVE: Add / Edit ----------
    @PostMapping("/schoolitems/save")
    public String saveSchoolItem(@ModelAttribute SchoolItem item) {
        // safety defaults so DB doesn't get nulls where we want numbers
        if (item.getQuantityOnHand() == null) {
            item.setQuantityOnHand(0);
        }

        if (item.getPurchaseCost() == null) {
            item.setPurchaseCost(BigDecimal.ZERO);
        }

        // If imageUrl is blank, set to null so you can fall back to default image
        if (item.getImageUrl() != null && item.getImageUrl().isBlank()) {
            item.setImageUrl(null);
        }

        schoolItemRepository.save(item);
        return "redirect:/schoolitems";
    }

    // ---------- DELETE ----------
    @PostMapping("/schoolitems/delete/{id}")
    public String deleteSchoolItem(@PathVariable Long id) {
        schoolItemRepository.deleteById(id);
        return "redirect:/schoolitems";
    }
}
