package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByIdNumber(String idNumber); // Customize as needed
     List<Student> findByIdNumberIn(Collection<String> idNumbers);
        @Query("""
        select s from Student s
        where (:q is null or :q = ''
               or lower(s.name) like lower(concat('%', :q, '%'))
               or lower(s.idNumber) like lower(concat('%', :q, '%')))
        order by s.name asc
    """)
    List<Student> searchTop(@Param("q") String q, Pageable pageable);
     
}