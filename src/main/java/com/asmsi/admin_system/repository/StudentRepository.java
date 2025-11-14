package com.asmsi.admin_system.repository;

import com.asmsi.admin_system.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 // 🔹 Raw query: VIA + total count
    @Query("SELECT s.via AS via, COUNT(s) AS total FROM Student s GROUP BY s.via")
    List<Object[]> countStudentsByViaRaw();

    // 🔹 Convenience method: convert to Map<String, Long>
    default Map<String, Long> countStudentsByVia() {
        Map<String, Long> result = new HashMap<>();
        List<Object[]> rows = countStudentsByViaRaw();

        for (Object[] row : rows) {
            String via = (String) row[0];   // s.via
            Long total = (Long) row[1];     // COUNT(s)
            result.put(via, total);
        }

        return result;
    }

    
     
}