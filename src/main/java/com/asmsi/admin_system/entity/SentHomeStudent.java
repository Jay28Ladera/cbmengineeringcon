package com.asmsi.admin_system.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "sent_home_students",
        indexes = {
                @Index(name = "idx_sent_home_id_number", columnList = "id_number"),
                @Index(name = "idx_sent_home_sent_at", columnList = "sent_home_at")
        }
)
public class SentHomeStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_student_id")
    private Long originalStudentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "id_number", nullable = false, length = 100)
    private String idNumber;

    @Column(name = "lrn", length = 100)
    private String lrn;

    @Column(name = "name", length = 255)
    private String name;

    private LocalDate birthday;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "place_of_birth", length = 255)
    private String placeOfBirth;

    @Column(name = "elementary_school", length = 255)
    private String elementarySchool;

    @Column(length = 150)
    private String via;

    @Column(length = 150)
    private String campus;

    @Column(name = "family_saint", length = 255)
    private String familySaint;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    // Admission details preserved for the Sent Home archive.
    @Column(name = "last_name", length = 150)
    private String lastName;

    @Column(name = "first_name", length = 150)
    private String firstName;

    @Column(name = "middle_name", length = 150)
    private String middleName;

    @Column(length = 50)
    private String suffix;

    @Column(name = "exam_place", length = 255)
    private String examPlace;

    @Column(name = "guardian_contact", length = 100)
    private String guardianContact;

    @Column(name = "school_year", length = 100)
    private String schoolYear;

    @Column(name = "sent_home_at", nullable = false)
    private LocalDateTime sentHomeAt;

    @Column(length = 50, nullable = false)
    private String status;

    @PrePersist
    public void prePersist() {
        if (sentHomeAt == null) {
            sentHomeAt = LocalDateTime.now();
        }

        if (status == null || status.isBlank()) {
            status = "Sent Home";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginalStudentId() {
        return originalStudentId;
    }

    public void setOriginalStudentId(Long originalStudentId) {
        this.originalStudentId = originalStudentId;
    }

    public Long getAdmissionId() {
        return admissionId;
    }

    public void setAdmissionId(Long admissionId) {
        this.admissionId = admissionId;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getLrn() {
        return lrn;
    }

    public void setLrn(String lrn) {
        this.lrn = lrn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getElementarySchool() {
        return elementarySchool;
    }

    public void setElementarySchool(String elementarySchool) {
        this.elementarySchool = elementarySchool;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public String getFamilySaint() {
        return familySaint;
    }

    public void setFamilySaint(String familySaint) {
        this.familySaint = familySaint;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getExamPlace() {
        return examPlace;
    }

    public void setExamPlace(String examPlace) {
        this.examPlace = examPlace;
    }

    public String getGuardianContact() {
        return guardianContact;
    }

    public void setGuardianContact(String guardianContact) {
        this.guardianContact = guardianContact;
    }

    public String getSchoolYear() {
        return schoolYear;
    }

    public void setSchoolYear(String schoolYear) {
        this.schoolYear = schoolYear;
    }

    public LocalDateTime getSentHomeAt() {
        return sentHomeAt;
    }

    public void setSentHomeAt(LocalDateTime sentHomeAt) {
        this.sentHomeAt = sentHomeAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
