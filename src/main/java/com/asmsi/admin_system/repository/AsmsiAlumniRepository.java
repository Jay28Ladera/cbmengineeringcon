package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.AsmsiAlumni;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AsmsiAlumniRepository extends JpaRepository<AsmsiAlumni, Long> {
    Optional<AsmsiAlumni> findByIdNumber(String idNumber);
}
