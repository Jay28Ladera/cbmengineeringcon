package com.asmsi.admin_system.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long> {
    List<MaterialCategory> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
