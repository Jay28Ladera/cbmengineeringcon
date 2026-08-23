package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.SentHomeStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SentHomeStudentRepository
        extends JpaRepository<SentHomeStudent, Long> {

    boolean existsByIdNumber(String idNumber);

    List<SentHomeStudent> findAllByOrderBySentHomeAtDesc();
}