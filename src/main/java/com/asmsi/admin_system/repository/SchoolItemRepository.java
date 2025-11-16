package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.SchoolItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SchoolItemRepository extends JpaRepository<SchoolItem, Long> {

    boolean existsByItemCode(String itemCode);

     // Find all items for a specific shop name
    List<SchoolItem> findByShops(String shops);
}
