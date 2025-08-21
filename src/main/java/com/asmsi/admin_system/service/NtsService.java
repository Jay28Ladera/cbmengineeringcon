// src/main/java/com/asmsi/admin_system/service/NtsService.java
package com.asmsi.admin_system.service;

import com.asmsi.admin_system.entity.NonTeachingStaff;
import com.asmsi.admin_system.repository.NonTeachingStaffRepository;
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
public class NtsService {

    private final NonTeachingStaffRepository repo;
    private final CloudinaryService cloudinaryService;

    public NtsService(NonTeachingStaffRepository repo, CloudinaryService cloudinaryService) {
        this.repo = repo;
        this.cloudinaryService = cloudinaryService;
    }

    public List<NonTeachingStaff> list() { return repo.findAll(); }

    public void add(NonTeachingStaff nts, MultipartFile image) throws Exception {
        if (repo.existsByEmployeeId(nts.getEmployeeId())) {
            throw new IllegalArgumentException("Employee ID already exists.");
        }
        if (image != null && !image.isEmpty()) {
            nts.setImageUrl(cloudinaryService.uploadImage(image));
        }
        repo.save(nts);
    }

    public void update(NonTeachingStaff incoming, MultipartFile image) throws Exception {
        NonTeachingStaff cur = repo.findById(incoming.getId())
                .orElseThrow(() -> new IllegalArgumentException("Staff not found"));

        if (!Objects.equals(cur.getEmployeeId(), incoming.getEmployeeId()) &&
            repo.existsByEmployeeId(incoming.getEmployeeId())) {
            throw new IllegalArgumentException("Employee ID already exists.");
        }

        cur.setEmployeeId(incoming.getEmployeeId().trim());
        cur.setName(incoming.getName().trim());
        cur.setPosition(incoming.getPosition().trim());
        cur.setDepartment(incoming.getDepartment().trim());

        if (image != null && !image.isEmpty()) {
            cur.setImageUrl(cloudinaryService.uploadImage(image));
        }
        // dirty checking persists
    }

    public void delete(Long id) { repo.deleteById(id); }

    public int importCsv(MultipartFile file) throws Exception {
        int upserts = 0;
        try (var reader = new CSVReaderHeaderAware(new InputStreamReader(file.getInputStream()))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                String employeeId = get(row, "employeeId");
                String name       = get(row, "name");
                String position   = get(row, "position");
                String department = get(row, "department");
                String imageUrl   = get(row, "imageUrl");

                if (employeeId.isBlank() || name.isBlank()) continue;

                NonTeachingStaff nts = repo.findByEmployeeId(employeeId).orElseGet(NonTeachingStaff::new);
                nts.setEmployeeId(employeeId);
                nts.setName(name);
                nts.setPosition(position);
                nts.setDepartment(department);
                if (!imageUrl.isBlank()) nts.setImageUrl(imageUrl);

                repo.save(nts);
                upserts++;
            }
        }
        return upserts;
    }

    private static String get(Map<String,String> row, String key) {
        if (row == null) return "";
        String v = row.getOrDefault(key, row.getOrDefault(key.toLowerCase(), ""));
        return v == null ? "" : v.trim();
    }
}
