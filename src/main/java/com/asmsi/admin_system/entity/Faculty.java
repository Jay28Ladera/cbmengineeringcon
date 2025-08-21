// src/main/java/com/asmsi/admin_system/entity/Faculty.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "faculty", uniqueConstraints = @UniqueConstraint(columnNames = "employeeId"))
public class Faculty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, length=64)
    private String employeeId;

    @Column(nullable=false, length=128)
    private String name;

    @Column(nullable=false, length=128)
    private String position;

    @Column(nullable=false, length=128)
    private String department;

    @Column(length = 512)
    private String imageUrl;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate void touch(){ this.updatedAt = Instant.now(); }

    // getters/setters
    public Long getId(){return id;}                 public void setId(Long id){this.id=id;}
    public String getEmployeeId(){return employeeId;} public void setEmployeeId(String v){this.employeeId=v;}
    public String getName(){return name;}           public void setName(String v){this.name=v;}
    public String getPosition(){return position;}   public void setPosition(String v){this.position=v;}
    public String getDepartment(){return department;} public void setDepartment(String v){this.department=v;}
    public String getImageUrl(){return imageUrl;}   public void setImageUrl(String v){this.imageUrl=v;}
}
