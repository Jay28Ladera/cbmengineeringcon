package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.repository.ShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class SchoolShopsController {

    @Autowired
    private ShopRepository shopRepository;

    // ----------------------------------------------------
    // LOAD PAGE + SHOW ALL SHOPS
    // ----------------------------------------------------
    @GetMapping("/schoolshops")
    public String viewSchoolShops(Model model) {

        List<Shop> shops = shopRepository.findAll();
        model.addAttribute("shops", shops);

        // Sidebar highlight
        
        model.addAttribute("activePage", "inventory-shops");

        return "schoolshops"; // loads schoolshops.html
    }

    // ----------------------------------------------------
    // ADD / EDIT SHOP
    // ----------------------------------------------------
    @PostMapping("/schoolshops/save")
    public String saveShop(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("name") String name,
            @RequestParam("teacher") String teacher,
            @RequestParam(value = "teacherAide", required = false) String teacherAide,
            @RequestParam("category") String category,
            @RequestParam(value = "imageUrl", required = false) String imageUrl
    ) {
        Shop shop;

        if (id != null) {
            // Update existing
            shop = shopRepository.findById(id).orElse(new Shop());
            shop.setId(id);
        } else {
            // New shop
            shop = new Shop();
        }

        shop.setName(name);
        shop.setTeacher(teacher);
        shop.setTeacherAide(teacherAide);
        shop.setCategory(category);
        shop.setImageUrl(imageUrl);

        shopRepository.save(shop);

        return "redirect:/schoolshops";
    }

    // ----------------------------------------------------
    // DELETE SHOP
    // ----------------------------------------------------
    @PostMapping("/schoolshops/delete/{id}")
    public String deleteShop(@PathVariable("id") Long id) {
        shopRepository.deleteById(id);
        return "redirect:/schoolshops";
    }
}
