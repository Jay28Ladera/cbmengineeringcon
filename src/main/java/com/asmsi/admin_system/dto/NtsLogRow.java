package com.asmsi.admin_system.dto;

public class NtsLogRow {
    public String imageUrl;
    public String name;
    public String employeeId;
    public String date;
    public String time;
    public String position;
    public String department;

    public Integer adultVisitor;
    public Integer kidVisitor;
    public Integer totalVisitors;
    public String  contactNumber;
    public String  vehicleType;

    public NtsLogRow(String imageUrl, String name, String employeeId,
                     String date, String time, String position, String department,
                     Integer adultVisitor, Integer kidVisitor, Integer totalVisitors,
                     String contactNumber, String vehicleType) {
        this.imageUrl = imageUrl;
        this.name = name;
        this.employeeId = employeeId;
        this.date = date;
        this.time = time;
        this.position = position;
        this.department = department;
        this.adultVisitor = adultVisitor;
        this.kidVisitor = kidVisitor;
        this.totalVisitors = totalVisitors;
        this.contactNumber = contactNumber;
        this.vehicleType = vehicleType;
    }

    // Back-compat 7-arg ctor
    public NtsLogRow(String imageUrl, String name, String employeeId,
                     String date, String time, String position, String department) {
        this(imageUrl, name, employeeId, date, time, position, department,
             null, null, null, null, null);
    }
}
