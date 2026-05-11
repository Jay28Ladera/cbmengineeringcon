package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.PasserHealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasserHealthRecordRepository extends JpaRepository<PasserHealthRecord, Long> {

    Optional<PasserHealthRecord> findByIdNum(String idNum);

    List<PasserHealthRecord> findAllByIdNumIn(List<String> idNums);
}