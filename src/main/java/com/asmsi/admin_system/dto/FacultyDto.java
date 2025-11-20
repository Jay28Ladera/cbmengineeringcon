package com.asmsi.admin_system.dto;

public class FacultyDto {

    private String employeeId;   // employee_id
    private String name;         // name
    private String position;     // position
    private String department;   // department
    private String imageUrl;     // image_url

    public FacultyDto() {
    }

    // ---------- GETTERS ----------

    public String getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public String getPosition() {
        return position;
    }

    public String getDepartment() {
        return department;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    // ---------- SETTERS ----------

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
