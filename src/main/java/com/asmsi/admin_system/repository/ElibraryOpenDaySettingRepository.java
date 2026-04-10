package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.DayCode;
import com.asmsi.admin_system.entity.ElibraryOpenDaySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ElibraryOpenDaySettingRepository extends JpaRepository<ElibraryOpenDaySetting, Long> {

    Optional<ElibraryOpenDaySetting> findByDayCode(DayCode dayCode);

    List<ElibraryOpenDaySetting> findAllByOrderByDayCodeAsc();
}