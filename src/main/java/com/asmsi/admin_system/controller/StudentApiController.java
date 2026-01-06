package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.data.domain.Sort;



@RestController
@RequestMapping("/api")
public class StudentApiController {

    @Autowired
    private StudentRepository studentRepository;

    // ✅ DataTables endpoint (existing)
    @GetMapping("/students")
    public Map<String, Object> getPaginatedStudents(
            @RequestParam("draw") int draw,
            @RequestParam("start") int start,
            @RequestParam("length") int length
    ) {
        int page = start / length;
        Pageable pageable = PageRequest.of(page, length);
        Page<Student> studentPage = studentRepository.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("draw", draw);
        response.put("recordsTotal", studentPage.getTotalElements());
        response.put("recordsFiltered", studentPage.getTotalElements());
        response.put("data", studentPage.getContent());

        return response;
    }
     @GetMapping("/students/all")
    public List<Student> getAllStudents() {
        return studentRepository.findAll(Sort.by("idNumber"));
    }

    // ✅ NEW: totals per VIA (for your summary table)
    @GetMapping("/students/via-totals")
    public Map<String, Long> getViaTotals() {
        return studentRepository.countStudentsByVia();
    }
}
