package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.ItemRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItemRequestRepository extends JpaRepository<ItemRequest, Long> {
    List<ItemRequest> findByRequestedByIgnoreCaseOrderByDateRequestedDesc(String requestedBy);
}
