package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    List<Shop> findByAssignedUserId(Long assignedUserId);

    // ✅ assigned OR unassigned (for regular users)
    @Query("""
        select s
        from Shop s
        where s.assignedUserId = :userId
           or s.assignedUserId is null
    """)
    List<Shop> findVisibleForUser(@Param("userId") Long userId);

    Optional<Shop> findByNameIgnoreCase(String name);
}
