package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.ElibraryBookingPcUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElibraryBookingPcUserRepository extends JpaRepository<ElibraryBookingPcUser, Long> {

    List<ElibraryBookingPcUser> findByBookingOrderByPcNumberAsc(ElibraryBooking booking);

    List<ElibraryBookingPcUser> findByBookingInOrderByPcNumberAsc(List<ElibraryBooking> bookings);
}