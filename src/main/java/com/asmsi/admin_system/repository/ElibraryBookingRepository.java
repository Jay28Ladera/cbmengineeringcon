package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.ElibraryRoom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ElibraryBookingRepository extends JpaRepository<ElibraryBooking, Long> {

    List<ElibraryBooking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    List<ElibraryBooking> findByRequestedByUsernameOrderByCreatedAtDesc(String requestedByUsername);

    List<ElibraryBooking> findAllByOrderByCreatedAtDesc();

    List<ElibraryBooking> findByRoomAndBookingDate(
            ElibraryRoom room,
            LocalDate bookingDate
    );

    List<ElibraryBooking> findByBookingDateAndStatusOrderByCreatedAtDesc(
            LocalDate bookingDate,
            BookingStatus status
    );

    List<ElibraryBooking> findByRoomAndBookingDateAndStatusIn(
            ElibraryRoom room,
            LocalDate bookingDate,
            List<BookingStatus> statuses
    );

    List<ElibraryBooking> findByRoomAndBookingDateAndStartTimeLessThanAndEndTimeGreaterThanAndStatusIn(
            ElibraryRoom room,
            LocalDate bookingDate,
            LocalTime endTime,
            LocalTime startTime,
            List<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"room"})
    List<ElibraryBooking> findByStatusOrderByBookingDateAscStartTimeAsc(BookingStatus status);

    @EntityGraph(attributePaths = {"room"})
    @Query("""
        SELECT b
        FROM ElibraryBooking b
        WHERE b.status = :status
          AND (:dateFrom IS NULL OR b.bookingDate >= :dateFrom)
          AND (:dateTo IS NULL OR b.bookingDate <= :dateTo)
          AND (:room IS NULL OR b.room = :room)
          AND (:requestedByUsername IS NULL OR :requestedByUsername = '' OR
               LOWER(b.requestedByUsername) LIKE LOWER(CONCAT('%', :requestedByUsername, '%')))
        ORDER BY b.bookingDate DESC, b.startTime DESC, b.createdAt DESC
    """)
    List<ElibraryBooking> findApprovedReportBookings(
            @Param("status") BookingStatus status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("room") ElibraryRoom room,
            @Param("requestedByUsername") String requestedByUsername
    );

    // ============================================================
    // OPTIMIZED MY BOOKING - PAGINATED
    // ============================================================

    @EntityGraph(attributePaths = {"room"})
    Page<ElibraryBooking> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"room"})
    Page<ElibraryBooking> findByRequestedByUsernameOrderByCreatedAtDesc(
            String requestedByUsername,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"room"})
    Page<ElibraryBooking> findByStatusOrderByCreatedAtDesc(
            BookingStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"room"})
    Page<ElibraryBooking> findByRequestedByUsernameAndStatusOrderByCreatedAtDesc(
            String requestedByUsername,
            BookingStatus status,
            Pageable pageable
    );


    // ============================================================
    // OPTIMIZED APPROVED BOOKING PAGE
    // ============================================================

    @EntityGraph(attributePaths = {"room"})
    Page<ElibraryBooking> findByStatusOrderByBookingDateDescStartTimeDescCreatedAtDesc(
            BookingStatus status,
            Pageable pageable
    );

}