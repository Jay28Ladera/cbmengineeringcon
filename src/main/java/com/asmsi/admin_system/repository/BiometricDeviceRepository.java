package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.BiometricDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice, Long> {
    Optional<BiometricDevice> findBySerialNumber(String serialNumber);
    List<BiometricDevice> findAllByOrderByApprovedDescNameAsc();
}
