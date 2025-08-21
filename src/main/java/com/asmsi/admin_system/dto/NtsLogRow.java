// src/main/java/com/asmsi/admin_system/dto/NtsLogRow.java
package com.asmsi.admin_system.dto;

public class NtsLogRow {
    public String imageUrl;
    public String name;
    public String employeeId;
    public String date;      // e.g. 2025-08-17
    public String time;      // e.g. 21:07:33
    public String position;
    public String department;

    public NtsLogRow(String imageUrl, String name, String employeeId,
                     String date, String time, String position, String department) {
        this.imageUrl = imageUrl;
        this.name = name;
        this.employeeId = employeeId;
        this.date = date;
        this.time = time;
        this.position = position;
        this.department = department;
    }
}
