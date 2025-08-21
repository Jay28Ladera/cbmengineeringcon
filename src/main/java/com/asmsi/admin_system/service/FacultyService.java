// src/main/java/com/asmsi/admin_system/service/FacultyService.java
package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.Faculty;
import com.asmsi.admin_system.repository.FacultyRepository;
import com.opencsv.CSVReaderHeaderAware;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class FacultyService {
    private final FacultyRepository repo;
    private final CloudinaryService cloudinary;

    public FacultyService(FacultyRepository repo, CloudinaryService cloudinary) {
        this.repo = repo; this.cloudinary = cloudinary;
    }

    public List<Faculty> list(){ return repo.findAll(); }

    public void add(Faculty f, MultipartFile image) throws Exception {
        if (repo.existsByEmployeeId(f.getEmployeeId())) throw new IllegalArgumentException("Employee ID already exists.");
        if (image != null && !image.isEmpty()) f.setImageUrl(cloudinary.uploadImage(image));
        repo.save(f);
    }

    public void update(Faculty incoming, MultipartFile image) throws Exception {
        Faculty cur = repo.findById(incoming.getId()).orElseThrow(() -> new IllegalArgumentException("Faculty not found"));
        if (!Objects.equals(cur.getEmployeeId(), incoming.getEmployeeId()) &&
            repo.existsByEmployeeId(incoming.getEmployeeId())) {
            throw new IllegalArgumentException("Employee ID already exists.");
        }
        cur.setEmployeeId(incoming.getEmployeeId().trim());
        cur.setName(incoming.getName().trim());
        cur.setPosition(incoming.getPosition().trim());
        cur.setDepartment(incoming.getDepartment().trim());
        if (image != null && !image.isEmpty()) cur.setImageUrl(cloudinary.uploadImage(image));
        // dirty checking persists
    }

    public void delete(Long id){ repo.deleteById(id); }

    public int importCsv(MultipartFile file) throws Exception {
        int upserts = 0;
        try (var r = new CSVReaderHeaderAware(new InputStreamReader(file.getInputStream()))) {
            Map<String,String> row;
            while ((row = r.readMap()) != null) {
                String employeeId = get(row,"employeeId");
                String name       = get(row,"name");
                String position   = get(row,"position");
                String department = get(row,"department");
                String imageUrl   = get(row,"imageUrl"); // optional
                if (employeeId.isBlank() || name.isBlank()) continue;

                Faculty f = repo.findByEmployeeId(employeeId).orElseGet(Faculty::new);
                f.setEmployeeId(employeeId);
                f.setName(name); f.setPosition(position); f.setDepartment(department);
                if (!imageUrl.isBlank()) f.setImageUrl(imageUrl);
                repo.save(f);
                upserts++;
            }
        }
        return upserts;
    }

    private static String get(Map<String,String> row, String key){
        if (row==null) return "";
        String v = row.getOrDefault(key, row.getOrDefault(key.toLowerCase(), ""));
        return v==null ? "" : v.trim();
    }
}
