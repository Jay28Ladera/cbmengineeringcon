package com.asmsi.admin_system.dto;

import java.math.BigDecimal;

public record SchoolItemRowDTO(
        Long id,
        String imageUrl,
        String itemCode,
        String itemName,
        Integer quantityOnHand,
        String shops,
        String status,

        // preview-only fields (not shown in table)
        String description,
        String specification,
        String brandModel,
        String category,
        String condition,
        String supplier,
        String purchaseDate,
        BigDecimal purchaseCost
) {}
