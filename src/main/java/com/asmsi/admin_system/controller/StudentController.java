package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.repository.StudentRepository;
import com.asmsi.admin_system.repository.ViaDropdownRepository;
import com.asmsi.admin_system.service.CloudinaryService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.opencsv.CSVReader;

import java.util.List;

import java.io.Reader;

import java.util.Locale;





@Controller
public class StudentController {
    
    @Autowired
    private final StudentRepository studentRepository;
    private final CloudinaryService cloudinaryService;

    public StudentController(StudentRepository studentRepository, CloudinaryService cloudinaryService) {
        this.studentRepository = studentRepository;
        this.cloudinaryService = cloudinaryService;
    }
    @Autowired
    private ViaDropdownRepository viaDropdownRepository;
    
    @GetMapping("/student-info")
public String showStudentPage(Model model) {
    List<String> vias = viaDropdownRepository.findAllUniqueVia();
    model.addAttribute("vias", vias);
    model.addAttribute("students", studentRepository.findAll());
    model.addAttribute("student", new Student()); // Bind empty student for modal form
    return "studentmanager";
}
    @PostMapping("/add-student")
    public String addStudent(@ModelAttribute Student student,
            @RequestParam("imageFile") MultipartFile imageFile) {
        try {
            // Upload image to Cloudinary
            if (!imageFile.isEmpty()) {
                String imageUrl = cloudinaryService.uploadImage(imageFile);
                student.setImageUrl(imageUrl);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        studentRepository.save(student);
        return "redirect:/student-info"; // Corrected redirect path
    }
   
    @PostMapping("/edit-student")
    public String editStudent(
            @ModelAttribute Student student,
            @RequestParam("imageFile") MultipartFile imageFile) {

        try {
            Student existingStudent = studentRepository.findById(student.getId()).orElse(null);

            if (existingStudent == null) {
                // Optional: Add error handling or redirect to error page
                return "redirect:/student-info?error=notfound";
            }

            // If a new image is uploaded
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = cloudinaryService.uploadImage(imageFile);
                student.setImageUrl(imageUrl);
            } else {
                // Retain the existing image if no new one is uploaded
                student.setImageUrl(existingStudent.getImageUrl());
            }

            // Save the updated student
            studentRepository.save(student);

        } catch (IOException e) {
            e.printStackTrace(); // Optional: Replace with logger
            return "redirect:/student-info?error=upload";
        }

        return "redirect:/student-info";
    }

    @PostMapping("/delete-student")
    public String deleteStudent(@RequestParam("id") Long id) {
        studentRepository.deleteById(id);
        return "redirect:/student-info";
    }
  
@PostMapping("/upload-csv")
public String uploadCSV(@RequestParam("file") MultipartFile file) {
    int imported = 0;
    int rowNum = 0;

    try (Reader reader = new BufferedReader(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

        CSVReader csvReader = new CSVReader(reader);
        List<String[]> records = csvReader.readAll();
        if (records.isEmpty()) {
            System.err.println("CSV is empty.");
            return "redirect:/student-info?error=empty";
        }

        // Expect header:
        // id,address,birthday,campus,elementary_school,id_number,lrn,name,place_of_birth,via,family_saint,batch_number,image_url
        records.remove(0); // remove header
        rowNum = 1;

        for (String[] record : records) {
            rowNum++;
            try {
                if (record == null || record.length < 13) {
                    throw new IllegalArgumentException("Row has fewer than 13 columns");
                }

                Student student = new Student();
                // 0 = id (ignored for insert)
                student.setAddress(s(record[1]));
                student.setBirthday(parseBirthday(s(record[2]))); // ← robust date parser below
                student.setCampus(s(record[3]));
                student.setElementarySchool(s(record[4]));
                student.setIdNumber(s(record[5]));
                student.setLrn(s(record[6]));
                student.setName(s(record[7]));
                student.setPlaceOfBirth(s(record[8]));
                student.setVia(s(record[9]));
                student.setFamilySaint(s(record[10]));
                student.setBatchNumber(s(record[11]));
                student.setImageUrl(nz(record[12]));

                // minimal validations
                if (!student.getLrn().matches("\\d{12}")) {
                    throw new IllegalArgumentException("lrn must be exactly 12 digits");
                }
                if (student.getName().isBlank()) {
                    throw new IllegalArgumentException("name is required");
                }

                studentRepository.save(student);
                imported++;

            } catch (Exception rowEx) {
                // Log and continue with next row
                System.err.println("Row " + rowNum + " skipped: " + rowEx.getMessage());
            }
        }

    } catch (Exception e) {
        System.out.println("❌ Error during CSV upload:");
        e.printStackTrace();
        return "redirect:/student-info?error=upload";
    }

    return "redirect:/student-info?success=1&message=Imported+" + imported + "+students";
}

// -------- helpers --------
private static String s(String v) { return v == null ? "" : v.trim(); }
private static String nz(String v) { String t = s(v); return t.isEmpty() ? null : t; }

/** Accepts: yyyy-MM-dd, M/d/yyyy, d/M/yyyy, MMMM d, uuuu, MMM d, uuuu, d-MMM-uuuu, d-MMM-yy, and Excel serials. */
private static LocalDate parseBirthday(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("birthday is blank");
    String val = raw.trim();

    // Excel serial (days since 1899-12-30)
    if (val.matches("\\d{1,5}")) {
        int serial = Integer.parseInt(val);
        return LocalDate.of(1899, 12, 30).plusDays(serial);
    }

    // Normalize separators to help parsing
    String norm = val.replace('.', '/').replace('-', '/').replace("  ", " ");

    // Try ISO first
    try { return LocalDate.parse(val, DateTimeFormatter.ISO_LOCAL_DATE); } catch (Exception ignore) {}

    // Common numeric formats
    for (String p : new String[] { "M/d/uuuu", "d/M/uuuu", "MM/dd/uuuu", "dd/MM/uuuu", "M-d-uuuu", "d-M-uuuu" }) {
        try { return LocalDate.parse(norm, DateTimeFormatter.ofPattern(p, Locale.ENGLISH)); } catch (Exception ignore) {}
    }

    // Long / short month names
    for (String p : new String[] { "MMMM d, uuuu", "MMM d, uuuu", "d MMM uuuu", "d-MMM-uuuu", "dd-MMM-uuuu" }) {
        try { return LocalDate.parse(val, DateTimeFormatter.ofPattern(p, Locale.ENGLISH)); } catch (Exception ignore) {}
    }

    // Two-digit year like "17-Jul-10" → assume 2000-based (=> 2010)
    try {
        DateTimeFormatter yyFmt = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d-MMM-")
                .appendValueReduced(ChronoField.YEAR, 2, 2, 2000) // base 2000
                .toFormatter(Locale.ENGLISH);
        return LocalDate.parse(val.replace('/', '-'), yyFmt);
    } catch (Exception ignore) {}

    throw new IllegalArgumentException("Invalid birthday '" + raw + "'. Use yyyy-MM-dd, M/d/yyyy, or d-MMM-yy (e.g., 2010-07-17, 7/17/2010, 17-Jul-10).");
}

    

}
