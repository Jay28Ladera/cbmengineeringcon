package com.asmsi.admin_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.asmsi.admin_system.entity.AsmsiVisitor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Visiting Day logs for students.
 */
public interface AsmsiVisitorRepository extends JpaRepository<AsmsiVisitor, Long> {

    /* ---------- PROJECTION used by report (native) ---------- */
    public interface AsmsiVisitRow {
        String     getIdNumber();
        String     getName();
        String     getFamilySaint();
        String     getBatchNumber();
        LocalDate  getDate();
        LocalTime  getTimeIn();
        Integer    getAdultVisitor();
        Integer    getKidVisitor();
        Integer    getTotalVisitors();
        String     getVehicleType();   // e.g. FOUR_WHEELED / MOTORCYCLE / null
        String     getImageUrl();      // optional, in case you store it
        String     getAddress();       // optional
    }

    /**
     * All Visiting Day rows in [start, end] inclusive, newest first.
     * Adjust column names if your table uses different snake_case.
     */
    @Query(value = """
        SELECT
          id_number      AS idNumber,
          name           AS name,
          family_saint   AS familySaint,
          batch_number   AS batchNumber,
          date           AS date,
          time_in        AS timeIn,
          adult_visitor  AS adultVisitor,
          kid_visitor    AS kidVisitor,
          total_visitors AS totalVisitors,
          vehicle_type   AS vehicleType,
          image_url      AS imageUrl,
          address        AS address
        FROM asmsi_visitor
        WHERE date >= :start AND date <= :end
        ORDER BY date DESC, time_in DESC
        """, nativeQuery = true)
    List<AsmsiVisitRow> findRangeReport(@Param("start") LocalDate start,
                                        @Param("end")   LocalDate end);
}
