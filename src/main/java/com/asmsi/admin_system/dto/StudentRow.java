// com.asmsi.admin_system.dto.StudentRow.java
package com.asmsi.admin_system.dto;

import java.time.LocalDate;

public record StudentRow(
    Long id,
    String imageUrl,
    String lrn,
    String idNumber,
    String name,
    LocalDate birthday,
    String address,
    String placeOfBirth,
    String elementarySchool,
    String via,
    String campus,
    String familySaint,
    String batchNumber
) {}
