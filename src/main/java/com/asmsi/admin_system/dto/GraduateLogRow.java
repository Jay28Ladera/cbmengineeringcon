package com.asmsi.admin_system.dto;

public class GraduateLogRow {
    public String imageUrl;
    public String idNumber;
    public String date;
    public String time;
    public String address;
    public String batchNumber;

    public Integer adultVisitor;
    public Integer kidVisitor;
    public Integer totalVisitors;
    public String  contactNumber;
    public String  vehicleType;

    public GraduateLogRow(String imageUrl, String idNumber, String date, String time,
                          String address, String batchNumber,
                          Integer adultVisitor, Integer kidVisitor, Integer totalVisitors,
                          String contactNumber, String vehicleType) {
        this.imageUrl = imageUrl;
        this.idNumber = idNumber;
        this.date = date;
        this.time = time;
        this.address = address;
        this.batchNumber = batchNumber;
        this.adultVisitor = adultVisitor;
        this.kidVisitor = kidVisitor;
        this.totalVisitors = totalVisitors;
        this.contactNumber = contactNumber;
        this.vehicleType = vehicleType;
    }

    // back-compat minimal ctor (if you still call it somewhere)
    public GraduateLogRow(String imageUrl, String idNumber, String date, String time,
                          String address, String batchNumber) {
        this(imageUrl, idNumber, date, time, address, batchNumber, null, null, null, null, null);
    }
}
