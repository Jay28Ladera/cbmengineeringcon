package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findAllByOrderByNameAsc();
    List<Material> findByActiveTrueOrderByNameAsc();
    boolean existsByCode(String code);
    long countByCategoryId(Long categoryId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m where m.id = ?1")
    Optional<Material> lockById(Long id);
}
