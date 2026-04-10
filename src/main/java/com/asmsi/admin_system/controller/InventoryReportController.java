package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.repository.SchoolItemRepository;
import com.asmsi.admin_system.service.InventoryReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Year;
import java.util.List;

@Controller
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;
    private final SchoolItemRepository schoolItemRepository;

    public InventoryReportController(InventoryReportService inventoryReportService,
                                     SchoolItemRepository schoolItemRepository) {
        this.inventoryReportService = inventoryReportService;
        this.schoolItemRepository = schoolItemRepository;
    }

   
}
