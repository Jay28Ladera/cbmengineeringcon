package com.asmsi.admin_system.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "elibrary_booking_pc_user")
public class ElibraryBookingPcUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private ElibraryBooking booking;

    @Column(name = "pc_number", nullable = false)
    private Integer pcNumber;

    @Column(name = "student_id_number", nullable = false, length = 11)
    private String studentIdNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ElibraryBooking getBooking() {
        return booking;
    }

    public void setBooking(ElibraryBooking booking) {
        this.booking = booking;
    }

    public Integer getPcNumber() {
        return pcNumber;
    }

    public void setPcNumber(Integer pcNumber) {
        this.pcNumber = pcNumber;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
    }
}