// src/main/java/com/asmsi/admin_system/entity/AsmsiAlumni.java
package com.asmsi.admin_system.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "asmsi") // <— table name; change if you prefer
public class AsmsiAlumni {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String idNumber;

    @Column(nullable = false, length = 32)
    private String batchNumber;

    @Column(nullable = false, length = 128)
    private String lastName;

    @Column(nullable = false, length = 128)
    private String firstName;

    @Column(length = 128)
    private String middleName;

    @Column(length = 128)
    private String father;

    @Column(length = 128)
    private String mother;

    private LocalDate dateOfBirth;

    @Column(length = 256)
    private String address;

    @Column(length = 128)
    private String highestEducationalAttainment;

    // Keep as String ("Yes"/"No") to match your form options
    @Column(length = 8)
    private String selfEmployed;

    @Column(length = 8)
    private String employed;

    @Column(length = 160)
    private String employerName;

    @Column(length = 64)
    private String contactNumber;

    @Column(length = 160)
    private String email;

    @Column(length = 160)
    private String fbAccount;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @Column(length = 512)
private String imageUrl;

public String getImageUrl() { return imageUrl; }
public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    // getters/setters
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getIdNumber() { return idNumber; } public void setIdNumber(String v) { this.idNumber = v; }
    public String getBatchNumber() { return batchNumber; } public void setBatchNumber(String v) { this.batchNumber = v; }
    public String getLastName() { return lastName; } public void setLastName(String v) { this.lastName = v; }
    public String getFirstName() { return firstName; } public void setFirstName(String v) { this.firstName = v; }
    public String getMiddleName() { return middleName; } public void setMiddleName(String v) { this.middleName = v; }
    public String getFather() { return father; } public void setFather(String v) { this.father = v; }
    public String getMother() { return mother; } public void setMother(String v) { this.mother = v; }
    public LocalDate getDateOfBirth() { return dateOfBirth; } public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; }
    public String getAddress() { return address; } public void setAddress(String v) { this.address = v; }
    public String getHighestEducationalAttainment() { return highestEducationalAttainment; } public void setHighestEducationalAttainment(String v) { this.highestEducationalAttainment = v; }
    public String getSelfEmployed() { return selfEmployed; } public void setSelfEmployed(String v) { this.selfEmployed = v; }
    public String getEmployed() { return employed; } public void setEmployed(String v) { this.employed = v; }
    public String getEmployerName() { return employerName; } public void setEmployerName(String v) { this.employerName = v; }
    public String getContactNumber() { return contactNumber; } public void setContactNumber(String v) { this.contactNumber = v; }
    public String getEmail() { return email; } public void setEmail(String v) { this.email = v; }
    public String getFbAccount() { return fbAccount; } public void setFbAccount(String v) { this.fbAccount = v; }
}
