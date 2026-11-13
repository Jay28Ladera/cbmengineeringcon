package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.OrganizationReporting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface OrganizationReportingRepository extends JpaRepository<OrganizationReporting, Long> {
    List<OrganizationReporting> findByMemberId(Long memberId);
    boolean existsBySupervisorId(Long supervisorId);

    @Modifying
    @Transactional
    void deleteByMemberId(Long memberId);
}
