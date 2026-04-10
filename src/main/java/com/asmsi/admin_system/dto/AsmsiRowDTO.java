package com.asmsi.admin_system.dto;

// simple DTO for the DataTables JSON
public class AsmsiRowDTO {

    private Long id;
    private String idNumber;
    private String batchNumber;
    private String lastName;
    private String firstName;
    private String middleName;

    // extra fields for the PREVIEW panel
    private String dateOfBirth;
    private String address;
    private String father;
    private String mother;
    private String highestEducationalAttainment;
    private String employerName;
    private String selfEmployed;
    private String employed;
    private String contactNumber;
    private String email;
    private String fbAccount;
    private String imageUrl;

    // ===== getters & setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getFather() { return father; }
    public void setFather(String father) { this.father = father; }

    public String getMother() { return mother; }
    public void setMother(String mother) { this.mother = mother; }

    public String getHighestEducationalAttainment() { return highestEducationalAttainment; }
    public void setHighestEducationalAttainment(String highestEducationalAttainment) {
        this.highestEducationalAttainment = highestEducationalAttainment;
    }

    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }

    public String getSelfEmployed() { return selfEmployed; }
    public void setSelfEmployed(String selfEmployed) { this.selfEmployed = selfEmployed; }

    public String getEmployed() { return employed; }
    public void setEmployed(String employed) { this.employed = employed; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFbAccount() { return fbAccount; }
    public void setFbAccount(String fbAccount) { this.fbAccount = fbAccount; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}