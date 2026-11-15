package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.OrganizationNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrganizationNodeRepository extends JpaRepository<OrganizationNode, Long> {
    List<OrganizationNode> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByParentId(Long parentId);
    boolean existsByEmployeeIdIgnoreCaseAndIdNot(String employeeId, Long id);
    boolean existsByEmployeeIdIgnoreCase(String employeeId);
    List<OrganizationNode> findByAccountUserId(Long accountUserId);
}
