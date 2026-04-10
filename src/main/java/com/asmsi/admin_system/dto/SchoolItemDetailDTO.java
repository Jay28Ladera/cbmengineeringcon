package com.asmsi.admin_system.dto;

import java.math.BigDecimal;

public record SchoolItemDetailDTO(
        Long id,
        String imageUrl,
        String itemCode,
        String itemName,
        String description,
        String specification,
        String brandModel,
        Integer quantityOnHand,
        String category,
        String condition,
        String status,
        String shops,
        String supplier,
        String purchaseDate,     // keep as String for easy JSON + your UI
        BigDecimal purchaseCost
) {}
