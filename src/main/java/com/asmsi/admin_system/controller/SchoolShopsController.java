package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Shop;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.ShopRepository;
import com.asmsi.admin_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
public class SchoolShopsController {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    // ----------------------------------------------------
    // LOAD PAGE + SHOW SHOPS (ADMIN = all, USER = assigned only)
    // ----------------------------------------------------
    @GetMapping("/schoolshops")
    public String viewSchoolShops(Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String username = "Guest";
        String role = "ROLE_USER";

        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            username = auth.getName();
            role = auth.getAuthorities().stream()
                    .findFirst().map(Object::toString).orElse("ROLE_USER");
        }

        model.addAttribute("username", username);
        model.addAttribute("role", role);

        List<Shop> shops;

        boolean isAdmin = role != null && role.contains("ADMIN");

        if (isAdmin) {
            // ADMIN sees all
            shops = shopRepository.findAll();

            // Provide users list for dropdown in modal
            List<User> users = userRepository.findAll().stream()
                    .filter(User::isEnabled) // isApproved == true (because isEnabled uses that)
                    .toList();

            model.addAttribute("users", users);
        } else {
            // USER sees assigned only
            User current = userRepository.findByUsername(username).orElse(null);
            if (current == null) {
                shops = Collections.emptyList();
            } else {
                shops = shopRepository.findByAssignedUserId(current.getId());
            }
        }

        model.addAttribute("shops", shops);
        model.addAttribute("activePage", "inventory-shops");

        return "schoolshops";
    }

    // ----------------------------------------------------
    // ADD / EDIT SHOP (ADMIN should be the only one allowed ideally)
    // ----------------------------------------------------
    @PostMapping("/schoolshops/save")
    public String saveShop(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("name") String name,
            @RequestParam("teacher") String teacher,
            @RequestParam(value = "teacherAide", required = false) String teacherAide,
            @RequestParam("category") String category,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "assignedUserId", required = false) Long assignedUserId
    ) {
        Shop shop;

        if (id != null) {
            shop = shopRepository.findById(id).orElse(new Shop());
            shop.setId(id);
        } else {
            shop = new Shop();
        }

        shop.setName(name);
        shop.setTeacher(teacher);
        shop.setTeacherAide(teacherAide);
        shop.setCategory(category);
        shop.setImageUrl(imageUrl);

        // ✅ assignment
        shop.setAssignedUserId(assignedUserId);

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
