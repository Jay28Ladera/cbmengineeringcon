package com.asmsi.admin_system.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "school_shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_name", nullable = false, length = 150)
    private String name;

    @Column(name = "teacher", nullable = false, length = 150)
    private String teacher;

    @Column(name = "teacher_aide", length = 150)
    private String teacherAide;

    @Column(name = "category", nullable = false, length = 100)
    private String category; // ICT, Home Economics, etc.

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "assigned_user_id")
private Long assignedUserId;

public Long getAssignedUserId() {
    return assignedUserId;

    
}



public void setAssignedUserId(Long assignedUserId) {
    this.assignedUserId = assignedUserId;
}





    // ---- getters & setters ----
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getTeacherAide() {
        return teacherAide;
    }

    public void setTeacherAide(String teacherAide) {
        this.teacherAide = teacherAide;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
