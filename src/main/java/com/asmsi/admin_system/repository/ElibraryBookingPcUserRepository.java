package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.BookingStatus;
import com.asmsi.admin_system.entity.ElibraryBooking;
import com.asmsi.admin_system.entity.ElibraryBookingPcUser;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ElibraryBookingPcUserRepository
        extends JpaRepository<ElibraryBookingPcUser, Long> {

    // ============================================================
    // EXISTING METHODS - KEEP
    // ============================================================

    List<ElibraryBookingPcUser> findByBookingOrderByPcNumberAsc(
            ElibraryBooking booking
    );

    List<ElibraryBookingPcUser> findByBookingInOrderByPcNumberAsc(
            List<ElibraryBooking> bookings
    );


    // ============================================================
    // OPTIMIZED E-LIBRARY REPORT
    //
    // These projections return only the columns needed by the
    // report. We do NOT load entire Booking entities + every
    // pcUsers collection anymore.
    // ============================================================

    interface PcUsageCountProjection {

        String getRoomName();

        Integer getPcNumber();

        Long getUsageCount();
    }


    interface PcUsageRowProjection {

        Long getAssignmentId();

        Integer getPcNumber();

        String getStudentIdNumber();

        Long getBookingId();

        String getBookingNumber();

        LocalDate getBookingDate();

        LocalTime getStartTime();

        LocalTime getEndTime();

        String getRoomName();

        String getTeacher();

        String getTeacherUsername();
    }


    // ============================================================
    // PC TAB COUNTS
    //
    // Very lightweight:
    // PC1 (120)
    // PC2 (98)
    // etc.
    //
    // This does NOT load all report rows.
    // ============================================================

    @Query("""
        SELECT
            b.room.roomName AS roomName,
            p.pcNumber AS pcNumber,
            COUNT(p.id) AS usageCount
        FROM ElibraryBookingPcUser p
        JOIN p.booking b
        WHERE b.status = :status
          AND b.bookingDate >= :dateFrom
          AND b.bookingDate <= :dateTo
          AND (
                :roomName = ''
                OR LOWER(b.room.roomName) = LOWER(:roomName)
              )
          AND (
                :requestedBy = ''
                OR LOWER(COALESCE(b.requestedBy, ''))
                   LIKE LOWER(CONCAT('%', :requestedBy, '%'))
                OR LOWER(COALESCE(b.requestedByUsername, ''))
                   LIKE LOWER(CONCAT('%', :requestedBy, '%'))
              )
          AND (
                :studentId = ''
                OR LOWER(COALESCE(p.studentIdNumber, ''))
                   LIKE LOWER(CONCAT('%', :studentId, '%'))
              )
        GROUP BY b.room.roomName, p.pcNumber
        ORDER BY b.room.roomName ASC, p.pcNumber ASC
    """)
    List<PcUsageCountProjection> findApprovedPcUsageCounts(
            @Param("status") BookingStatus status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("roomName") String roomName,
            @Param("requestedBy") String requestedBy,
            @Param("studentId") String studentId
    );


    // ============================================================
    // ONE SELECTED PC - SERVER PAGINATION
    //
    // Only 20 rows by default are sent to the browser.
    // ============================================================

    @Query(
        value = """
            SELECT
                p.id AS assignmentId,
                p.pcNumber AS pcNumber,
                p.studentIdNumber AS studentIdNumber,
                b.id AS bookingId,
                b.bookingNo AS bookingNumber,
                b.bookingDate AS bookingDate,
                b.startTime AS startTime,
                b.endTime AS endTime,
                b.room.roomName AS roomName,
                b.requestedBy AS teacher,
                b.requestedByUsername AS teacherUsername
            FROM ElibraryBookingPcUser p
            JOIN p.booking b
            WHERE b.status = :status
              AND b.bookingDate >= :dateFrom
              AND b.bookingDate <= :dateTo
              AND (
                    :roomName = ''
                    OR LOWER(b.room.roomName) = LOWER(:roomName)
                  )
              AND (
                    :pcNumber IS NULL
                    OR p.pcNumber = :pcNumber
                  )
              AND (
                    :requestedBy = ''
                    OR LOWER(COALESCE(b.requestedBy, ''))
                       LIKE LOWER(CONCAT('%', :requestedBy, '%'))
                    OR LOWER(COALESCE(b.requestedByUsername, ''))
                       LIKE LOWER(CONCAT('%', :requestedBy, '%'))
                  )
              AND (
                    :studentId = ''
                    OR LOWER(COALESCE(p.studentIdNumber, ''))
                       LIKE LOWER(CONCAT('%', :studentId, '%'))
                  )
            ORDER BY
                b.bookingDate DESC,
                b.startTime DESC,
                b.createdAt DESC
        """,
        countQuery = """
            SELECT COUNT(p.id)
            FROM ElibraryBookingPcUser p
            JOIN p.booking b
            WHERE b.status = :status
              AND b.bookingDate >= :dateFrom
              AND b.bookingDate <= :dateTo
              AND (
                    :roomName = ''
                    OR LOWER(b.room.roomName) = LOWER(:roomName)
                  )
              AND (
                    :pcNumber IS NULL
                    OR p.pcNumber = :pcNumber
                  )
              AND (
                    :requestedBy = ''
                    OR LOWER(COALESCE(b.requestedBy, ''))
                       LIKE LOWER(CONCAT('%', :requestedBy, '%'))
                    OR LOWER(COALESCE(b.requestedByUsername, ''))
                       LIKE LOWER(CONCAT('%', :requestedBy, '%'))
                  )
              AND (
                    :studentId = ''
                    OR LOWER(COALESCE(p.studentIdNumber, ''))
                       LIKE LOWER(CONCAT('%', :studentId, '%'))
                  )
        """
    )
    Page<PcUsageRowProjection> findApprovedPcUsagePage(
            @Param("status") BookingStatus status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("roomName") String roomName,
            @Param("pcNumber") Integer pcNumber,
            @Param("requestedBy") String requestedBy,
            @Param("studentId") String studentId,
            Pageable pageable
    );
}