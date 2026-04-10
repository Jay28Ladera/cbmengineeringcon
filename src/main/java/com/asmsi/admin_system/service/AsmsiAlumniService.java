// src/main/java/com/asmsi/admin_system/service/AsmsiAlumniService.java
package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.EmploymentStats;
import com.asmsi.admin_system.entity.AsmsiAlumni;
import com.asmsi.admin_system.repository.AsmsiAlumniRepository;
import com.opencsv.CSVReaderHeaderAware;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class AsmsiAlumniService {

    private final AsmsiAlumniRepository repo;
    private final CloudinaryService cloudinary;

    public AsmsiAlumniService(AsmsiAlumniRepository repo, CloudinaryService cloudinary) {
        this.repo = repo;
        this.cloudinary = cloudinary;
    }

    public List<AsmsiAlumni> list() {
        return repo.findAll();
    }

    public void add(AsmsiAlumni a, MultipartFile imageFile) throws Exception {
        if (repo.existsByIdNumber(a.getIdNumber()))
            throw new IllegalArgumentException("ID Number already exists.");
        if (imageFile != null && !imageFile.isEmpty())
            a.setImageUrl(cloudinary.uploadImage(imageFile));
        repo.save(a);
    }

    public void update(AsmsiAlumni incoming, MultipartFile imageFile) throws Exception {
        AsmsiAlumni cur = repo.findById(incoming.getId())
                .orElseThrow(() -> new IllegalArgumentException("Alumni not found"));

        if (!Objects.equals(cur.getIdNumber(), incoming.getIdNumber()) &&
            repo.existsByIdNumber(incoming.getIdNumber()))
            throw new IllegalArgumentException("ID Number already exists.");

        cur.setIdNumber(incoming.getIdNumber());
        cur.setBatchNumber(incoming.getBatchNumber());
        cur.setLastName(incoming.getLastName());
        cur.setFirstName(incoming.getFirstName());
        cur.setMiddleName(incoming.getMiddleName());
        cur.setFather(incoming.getFather());
        cur.setMother(incoming.getMother());
        cur.setDateOfBirth(incoming.getDateOfBirth());
        cur.setAddress(incoming.getAddress());
        cur.setHighestEducationalAttainment(incoming.getHighestEducationalAttainment());
        cur.setSelfEmployed(incoming.getSelfEmployed());
        cur.setEmployed(incoming.getEmployed());
        cur.setEmployerName(incoming.getEmployerName());
        cur.setContactNumber(incoming.getContactNumber());
        cur.setEmail(incoming.getEmail());
        cur.setFbAccount(incoming.getFbAccount());

        if (imageFile != null && !imageFile.isEmpty())
            cur.setImageUrl(cloudinary.uploadImage(imageFile));
        // no need to call save explicitly; JPA will flush because of @Transactional,
        // but you can call repo.save(cur) if you prefer it explicit.
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    // ---------- CSV IMPORT (unchanged) ----------
    public int importCsv(MultipartFile file) throws Exception {
        int upserts = 0;
        try (var r = new CSVReaderHeaderAware(new InputStreamReader(file.getInputStream()))) {
            Map<String, String> row;
            DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
            while ((row = r.readMap()) != null) {
                String idNumber = get(row, "idNumber");
                if (idNumber.isBlank()) continue;

                AsmsiAlumni a = repo.findByIdNumber(idNumber).orElseGet(AsmsiAlumni::new);
                a.setIdNumber(idNumber);
                a.setBatchNumber(get(row, "batchNumber"));
                a.setLastName(get(row, "lastName"));
                a.setFirstName(get(row, "firstName"));
                a.setMiddleName(get(row, "middleName"));
                a.setFather(get(row, "father"));
                a.setMother(get(row, "mother"));
                String dob = get(row, "dateOfBirth");
                a.setDateOfBirth(dob.isBlank() ? null : LocalDate.parse(dob, iso));
                a.setAddress(get(row, "address"));
                a.setHighestEducationalAttainment(get(row, "highestEducationalAttainment"));
                a.setSelfEmployed(get(row, "selfEmployed"));
                a.setEmployed(get(row, "employed"));
                a.setEmployerName(get(row, "employerName"));
                a.setContactNumber(get(row, "contactNumber"));
                a.setEmail(get(row, "email"));
                a.setFbAccount(get(row, "fbAccount"));
                String imageUrl = get(row, "imageUrl");
                if (!imageUrl.isBlank()) a.setImageUrl(imageUrl);

                repo.save(a);
                upserts++;
            }
        }
        return upserts;
    }

    private static String get(Map<String, String> row, String key) {
        if (row == null) return "";
        String v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        if (v == null) v = row.get(key.toUpperCase());
        return v == null ? "" : v.trim();
    }

    // ------- STATS -------

    public Map<String, Long> countByBatch() {
        List<Object[]> rows = repo.countByBatch();
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] r : rows) {
            String batch = (r[0] == null) ? "N/A" : r[0].toString();
            long cnt = num(r[1]);
            map.put(batch, cnt);
        }
        return map;
    }

    public EmploymentStats employmentStats() {
        List<Object[]> rows = repo.employmentTotals();   // expect single row
        long employed = 0, selfEmp = 0;
        if (rows != null && !rows.isEmpty()) {
            Object[] r = rows.get(0);
            employed = num(r[0]);
            selfEmp = num(r[1]);
        }
        long total = repo.count();
        return new EmploymentStats(employed, selfEmp, total);
    }

    private static long num(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        if (o instanceof Object[] a && a.length > 0 && a[0] instanceof Number n0) return n0.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    // ------- NEW: paging for DataTables server-side -------

    /**
     * Returns a page of alumni for DataTables.
     *
     * @param search search string (may be null/blank)
     * @param page   zero-based page index
     * @param size   page size
     */
    public Page<AsmsiAlumni> findPage(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (search == null || search.isBlank()) {
            return repo.findAll(pageable);
        }

        String q = search.trim().toLowerCase();
        return repo.searchPage(q, pageable);
    }

    /**
     * Total count of alumni (for recordsTotal in DataTables).
     */
    public long countAll() {
        return repo.count();
    }
}