// src/main/java/com/asmsi/admin_system/dto/GraduateLogRow.java
package com.asmsi.admin_system.dto;

public class GraduateLogRow {
  public String imageUrl, idNumber, date, time, address, batchNumber;
  public GraduateLogRow(String imageUrl, String idNumber, String date, String time, String address, String batchNumber) {
    this.imageUrl = imageUrl; this.idNumber = idNumber; this.date = date; this.time = time; this.address = address; this.batchNumber = batchNumber;
  }
}
