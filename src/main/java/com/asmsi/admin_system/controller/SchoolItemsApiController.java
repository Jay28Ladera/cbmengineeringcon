package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.SchoolItem;
import com.asmsi.admin_system.repository.SchoolItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schoolitems")
public class SchoolItemsApiController {

    @Autowired
    private SchoolItemRepository schoolItemRepository;

    @GetMapping("/by-shop")
    public ResponseEntity<List<SchoolItem>> getItemsByShop(@RequestParam("shop") String shop) {
        List<SchoolItem> items = schoolItemRepository.findByShops(shop);
        return ResponseEntity.ok(items);
    }
}
