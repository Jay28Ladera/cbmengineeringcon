package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.ElibraryRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ElibraryRoomRepository extends JpaRepository<ElibraryRoom, Long> {

    Optional<ElibraryRoom> findByRoomName(String roomName);

    List<ElibraryRoom> findAllByOrderByRoomNameAsc();
}