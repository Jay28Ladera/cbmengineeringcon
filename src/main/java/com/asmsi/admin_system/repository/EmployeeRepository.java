package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByOrgNodeId(Long orgNodeId);
    Optional<Employee> findByBiometricId(String biometricId);
    List<Employee> findByOrgNodeIdIsNotNull();
    List<Employee> findByOrgNodeIdIsNull();
    List<Employee> findByActiveTrue();
}
