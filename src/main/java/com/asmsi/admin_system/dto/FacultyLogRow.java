// src/main/java/com/asmsi/admin_system/dto/FacultyLogRow.java
package com.asmsi.admin_system.dto;

public class FacultyLogRow {
  public String imageUrl, name, employeeId, date, time, position, department;
  public FacultyLogRow(String imageUrl, String name, String employeeId, String date, String time, String position, String department) {
    this.imageUrl = imageUrl; this.name = name; this.employeeId = employeeId;
    this.date = date; this.time = time; this.position = position; this.department = department;
  }
}
