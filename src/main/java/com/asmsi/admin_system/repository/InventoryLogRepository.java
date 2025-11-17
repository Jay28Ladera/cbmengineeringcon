// InventoryLogRepository.java
package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.InventoryLog;

import java.util.List;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
    List<InventoryLog> findBySessionDateBetween(LocalDate start, LocalDate end);

    @Query("select distinct YEAR(l.sessionDate) " +
       "from InventoryLog l " +
       "where l.sessionDate is not null " +
       "order by YEAR(l.sessionDate) desc")
List<Integer> findDistinctSessionYears();

}
