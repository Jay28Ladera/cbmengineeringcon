package com.asmsi.admin_system.dto;

public record SchoolItemLiteDTO(
        Long id,
        String imageUrl,
        String itemCode,
        String itemName,
        Integer quantityOnHand,
        String shops,
        String status
) {}
