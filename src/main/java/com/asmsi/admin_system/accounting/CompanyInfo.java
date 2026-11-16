package com.asmsi.admin_system.accounting;

import jakarta.persistence.*;

/** CBM's own registration details, printed on BIR forms and billings (one row). */
@Entity
@Table(name = "acc_company")
public class CompanyInfo {
    @Id public Long id = 1L;
    @Column(length = 160) public String name = "CBM Engineering & Construction";
    @Column(length = 40) public String tin;
    @Column(name = "rdo_code", length = 10) public String rdoCode;
    @Column(length = 300) public String address;
    @Column(name = "zip_code", length = 10) public String zipCode;
    @Column(name = "signatory", length = 160) public String signatory;
    @Column(name = "signatory_title", length = 120) public String signatoryTitle;
    @Column(name = "signatory_tin", length = 40) public String signatoryTin;
    @Column(length = 60) public String phone;
    @Column(length = 160) public String email;
    @Column(length = 160) public String website;
    /** Name on client cheques; blank = the registered name. */
    @Column(name = "payable_to", length = 160) public String payableTo;

    public String getPayee() { return payableTo == null || payableTo.isBlank() ? name : payableTo; }
    /** One line for document headers: address · phone · email · TIN. */
    public String getContactLine() {
        java.util.List<String> p = new java.util.ArrayList<>();
        if (address != null && !address.isBlank()) p.add(address.trim() + (zipCode == null || zipCode.isBlank() ? "" : " " + zipCode.trim()));
        if (phone != null && !phone.isBlank()) p.add("Tel. " + phone.trim());
        if (email != null && !email.isBlank()) p.add(email.trim());
        if (website != null && !website.isBlank()) p.add(website.trim());
        if (tin != null && !tin.isBlank()) p.add("TIN " + tin.trim());
        return String.join(" · ", p);
    }
    public boolean isComplete() { return tin != null && !tin.isBlank() && address != null && !address.isBlank(); }
}
