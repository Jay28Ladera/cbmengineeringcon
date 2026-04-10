package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.ElibraryPc;
import com.asmsi.admin_system.entity.ElibraryRoom;
import com.asmsi.admin_system.entity.PcStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElibraryPcRepository extends JpaRepository<ElibraryPc, Long> {

    List<ElibraryPc> findByRoomOrderByPcNumberAsc(ElibraryRoom room);

    List<ElibraryPc> findByRoomAndActiveTrueOrderByPcNumberAsc(ElibraryRoom room);

    List<ElibraryPc> findByRoomAndActiveTrueAndStatusOrderByPcNumberAsc(ElibraryRoom room, PcStatus status);

    long countByRoomAndStatusAndActiveTrue(ElibraryRoom room, PcStatus status);
}