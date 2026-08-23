package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.EnrollPasserRequest;
import com.asmsi.admin_system.dto.BulkEnrollPassersRequest;
import com.asmsi.admin_system.entity.Student;
import com.asmsi.admin_system.entity.AdmissionData;
import com.asmsi.admin_system.entity.SentHomeStudent;
import com.asmsi.admin_system.repository.StudentRepository;
import com.asmsi.admin_system.repository.AdmissionDataRepository;
import com.asmsi.admin_system.repository.SentHomeStudentRepository;
import com.asmsi.admin_system.repository.ViaDropdownRepository;
import com.asmsi.admin_system.service.CloudinaryService;
import com.opencsv.CSVReader;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class StudentController {

    private final StudentRepository studentRepository;
    private final CloudinaryService cloudinaryService;
    private final ViaDropdownRepository viaDropdownRepository;
    private final AdmissionDataRepository admissionDataRepository;
    private final SentHomeStudentRepository sentHomeStudentRepository;

    public StudentController(
            StudentRepository studentRepository,
            CloudinaryService cloudinaryService,
            ViaDropdownRepository viaDropdownRepository,
            AdmissionDataRepository admissionDataRepository,
            SentHomeStudentRepository sentHomeStudentRepository
    ) {
        this.studentRepository = studentRepository;
        this.cloudinaryService = cloudinaryService;
        this.viaDropdownRepository = viaDropdownRepository;
        this.admissionDataRepository = admissionDataRepository;
        this.sentHomeStudentRepository = sentHomeStudentRepository;
    }

    // ============================================================
    // STUDENT MANAGER PAGE
    // IMPORTANT: We do NOT load all students here anymore.
    // ============================================================

    @GetMapping("/student-info")
    public String showStudentPage(Model model) {

        List<String> vias = viaDropdownRepository.findAllUniqueVia();

        model.addAttribute("vias", vias);
        model.addAttribute("student", new Student());

        return "studentmanager";
    }

    // ============================================================
    // ADD STUDENT
    // ============================================================

    @PostMapping("/add-student")
    public String addStudent(
            @ModelAttribute Student student,
            @RequestParam("imageFile") MultipartFile imageFile
    ) {

        try {
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = cloudinaryService.uploadImage(imageFile);
                student.setImageUrl(imageUrl);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        studentRepository.save(student);

        return "redirect:/student-info";
    }

    // ============================================================
    // EDIT STUDENT
    // ============================================================

    @PostMapping("/edit-student")
    public String editStudent(
            @ModelAttribute Student student,
            @RequestParam("imageFile") MultipartFile imageFile
    ) {

        try {

            Student existingStudent =
                    studentRepository.findById(student.getId()).orElse(null);

            if (existingStudent == null) {
                return "redirect:/student-info?error=notfound";
            }

            if (imageFile != null && !imageFile.isEmpty()) {

                String imageUrl =
                        cloudinaryService.uploadImage(imageFile);

                student.setImageUrl(imageUrl);

            } else {

                student.setImageUrl(
                        existingStudent.getImageUrl()
                );
            }

            studentRepository.save(student);

        } catch (IOException e) {

            e.printStackTrace();

            return "redirect:/student-info?error=upload";
        }

        return "redirect:/student-info";
    }

    // ============================================================
    // DELETE STUDENT
    // ============================================================

    @PostMapping("/delete-student")
    public String deleteStudent(
            @RequestParam("id") Long id
    ) {

        studentRepository.deleteById(id);

        return "redirect:/student-info";
    }

    // ============================================================
    // STUDENT LOOKUP
    // KEEP THIS FOR VISITING DAY
    // ============================================================

    @GetMapping("/students/lookup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lookupStudent(
            @RequestParam("idNumber") String idNumber
    ) {

        Map<String, Object> response = new HashMap<>();

        String cleanId =
                idNumber == null
                        ? ""
                        : idNumber.trim();

        if (cleanId.isEmpty()) {

            response.put("found", false);
            response.put(
                    "message",
                    "Student ID is required."
            );

            return ResponseEntity.ok(response);
        }

        Optional<Student> studentOpt =
                studentRepository.findByIdNumberTrimmed(cleanId);

        if (studentOpt.isPresent()) {

            Student student = studentOpt.get();

            response.put("found", true);
            response.put("id", student.getId());
            response.put("idNumber", student.getIdNumber());
            response.put("name", student.getName());
            response.put("imageUrl", student.getImageUrl());
            response.put("campus", student.getCampus());
            response.put("via", student.getVia());
            response.put("familySaint", student.getFamilySaint());
            response.put("message", "Student found.");

        } else {

            response.put("found", false);
            response.put(
                    "message",
                    "Student not found."
            );
        }

        return ResponseEntity.ok(response);
    }

    // ============================================================
    // CSV UPLOAD
    // ============================================================

    @PostMapping("/upload-csv")
    public String uploadCSV(
            @RequestParam("file") MultipartFile file
    ) {

        int imported = 0;
        int rowNum = 0;

        try (
                Reader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        file.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        );

                CSVReader csvReader =
                        new CSVReader(reader)
        ) {

            List<String[]> records =
                    csvReader.readAll();

            if (records.isEmpty()) {

                System.err.println("CSV is empty.");

                return "redirect:/student-info?error=empty";
            }

            records.remove(0);
            rowNum = 1;

            for (String[] record : records) {

                rowNum++;

                try {

                    if (record == null || record.length < 13) {

                        throw new IllegalArgumentException(
                                "Row has fewer than 13 columns"
                        );
                    }

                    Student student =
                            new Student();

                    student.setAddress(
                            s(record[1])
                    );

                    student.setBirthday(
                            parseBirthday(
                                    s(record[2])
                            )
                    );

                    student.setCampus(
                            s(record[3])
                    );

                    student.setElementarySchool(
                            s(record[4])
                    );

                    student.setIdNumber(
                            s(record[5])
                    );

                    student.setLrn(
                            s(record[6])
                    );

                    student.setName(
                            s(record[7])
                    );

                    student.setPlaceOfBirth(
                            s(record[8])
                    );

                    student.setVia(
                            s(record[9])
                    );

                    student.setFamilySaint(
                            s(record[10])
                    );

                    student.setBatchNumber(
                            s(record[11])
                    );

                    student.setImageUrl(
                            nz(record[12])
                    );

                    if (!student.getLrn().matches("\\d{12}")) {

                        throw new IllegalArgumentException(
                                "lrn must be exactly 12 digits"
                        );
                    }

                    if (student.getName().isBlank()) {

                        throw new IllegalArgumentException(
                                "name is required"
                        );
                    }

                    studentRepository.save(student);

                    imported++;

                } catch (Exception rowEx) {

                    System.err.println(
                            "Row "
                                    + rowNum
                                    + " skipped: "
                                    + rowEx.getMessage()
                    );
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Error during CSV upload:"
            );

            e.printStackTrace();

            return "redirect:/student-info?error=upload";
        }

        return "redirect:/student-info"
                + "?success=1"
                + "&message=Imported+"
                + imported
                + "+students";
    }

    // ============================================================
    // ENROLL PASSER -> STUDENT MANAGER
    // ============================================================

    @PostMapping("/students/enroll-passer")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> enrollPasser(
            @RequestBody EnrollPasserRequest req
    ) {
        Map<String, Object> response = new HashMap<>();

        String passerIdNum = s(req.getPasserIdNum());
        String idNumber = s(req.getIdNumber());
        String lrn = s(req.getLrn());

        if (passerIdNum.isBlank()) {
            response.put("success", false);
            response.put("message", "Passer ID Number is required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AdmissionData> admissionOpt =
                admissionDataRepository.findFirstByIdNum(passerIdNum);

        if (admissionOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Passer record was not found.");
            return ResponseEntity.badRequest().body(response);
        }

        if (idNumber.isBlank()) {
            idNumber = passerIdNum;
        }

        if (studentRepository.existsByIdNumber(idNumber)) {
            response.put("success", false);
            response.put("message", "This student is already enrolled in Student Manager.");
            return ResponseEntity.status(409).body(response);
        }

        if (!lrn.isBlank() && studentRepository.findByLrn(lrn).isPresent()) {
            response.put("success", false);
            response.put("message", "A student with this LRN is already in Student Manager.");
            return ResponseEntity.status(409).body(response);
        }

        AdmissionData admission = admissionOpt.get();

        Student student = new Student();
        student.setIdNumber(idNumber);
        student.setLrn(lrn);
        student.setName(buildPasserStudentName(
                req.getLastName(),
                req.getFirstName(),
                req.getMiddleName(),
                req.getSuffix()
        ));

        if (req.getBirthDate() != null && !req.getBirthDate().isBlank()) {
            student.setBirthday(parseBirthday(req.getBirthDate()));
        }

        student.setAddress(buildPasserAddress(
                req.getBrgy(),
                req.getMunicipality(),
                req.getProvince()
        ));

        student.setPlaceOfBirth(s(req.getBirthPlace()));
        student.setElementarySchool(s(req.getElemSchool()));
        // VIA is optional.
        student.setVia(s(req.getVia()));

        // Campus is automatic for this school.
        student.setCampus("Girlstown");

        student.setFamilySaint(s(req.getFamilySaint()));
        student.setBatchNumber(s(req.getBatchNumber()));
        student.setImageUrl(null);

        Student saved = studentRepository.save(student);

        // Keep the admission history, but remove this person from the active Passers list.
        admission.setStatus("Enrolled");
        admissionDataRepository.save(admission);

        response.put("success", true);
        response.put("studentId", saved.getId());
        response.put("idNumber", saved.getIdNumber());
        response.put("name", saved.getName());
        response.put("removedFromPasserList", true);
        response.put(
                "message",
                saved.getName() + " has been enrolled and removed from the List of Passers."
        );

        return ResponseEntity.ok(response);
    }

    // ============================================================
    // BULK ENROLL PASSERS -> STUDENT MANAGER
    // ============================================================

    @PostMapping("/students/bulk-enroll-passers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkEnrollPassers(
            @RequestBody BulkEnrollPassersRequest req
    ) {

        Map<String, Object> response =
                new HashMap<>();

        String batchNumber =
                s(req.getBatchNumber());

        String sharedVia =
                s(req.getVia());

        if (batchNumber.isBlank()) {

            response.put(
                    "message",
                    "Batch Number is required."
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        if (
                req.getStudents() == null
                || req.getStudents().isEmpty()
        ) {

            response.put(
                    "message",
                    "No students were selected."
            );

            return ResponseEntity
                    .badRequest()
                    .body(response);
        }

        int enrolledCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        List<String> successfulPasserIds =
                new java.util.ArrayList<>();

        List<Map<String, String>> skipped =
                new java.util.ArrayList<>();

        List<Map<String, String>> failed =
                new java.util.ArrayList<>();


        for (
                EnrollPasserRequest item :
                req.getStudents()
        ) {

            String passerIdNum =
                    s(item.getPasserIdNum());

            String idNumber =
                    s(item.getIdNumber());

            String lrn =
                    s(item.getLrn());

            try {

                if (passerIdNum.isBlank()) {

                    failedCount++;

                    failed.add(
                            Map.of(
                                    "passerIdNum",
                                    "",
                                    "reason",
                                    "Missing passer ID."
                            )
                    );

                    continue;
                }


                Optional<AdmissionData> admissionOpt =
                        admissionDataRepository
                                .findFirstByIdNum(
                                        passerIdNum
                                );


                if (admissionOpt.isEmpty()) {

                    failedCount++;

                    failed.add(
                            Map.of(
                                    "passerIdNum",
                                    passerIdNum,
                                    "reason",
                                    "Passer record not found."
                            )
                    );

                    continue;
                }


                if (idNumber.isBlank()) {
                    idNumber = passerIdNum;
                }


                if (
                        studentRepository
                                .existsByIdNumber(
                                        idNumber
                                )
                ) {

                    skippedCount++;

                    skipped.add(
                            Map.of(
                                    "passerIdNum",
                                    passerIdNum,
                                    "reason",
                                    "Already enrolled by ID Number."
                            )
                    );

                    continue;
                }


                if (
                        !lrn.isBlank()
                        && studentRepository
                                .findByLrn(lrn)
                                .isPresent()
                ) {

                    skippedCount++;

                    skipped.add(
                            Map.of(
                                    "passerIdNum",
                                    passerIdNum,
                                    "reason",
                                    "Already enrolled by LRN."
                            )
                    );

                    continue;
                }


                AdmissionData admission =
                        admissionOpt.get();


                Student student =
                        new Student();


                student.setIdNumber(
                        idNumber
                );

                student.setLrn(
                        lrn
                );

                student.setName(
                        buildPasserStudentName(
                                item.getLastName(),
                                item.getFirstName(),
                                item.getMiddleName(),
                                item.getSuffix()
                        )
                );


                if (
                        item.getBirthDate() != null
                        && !item
                                .getBirthDate()
                                .isBlank()
                ) {

                    student.setBirthday(
                            parseBirthday(
                                    item.getBirthDate()
                            )
                    );
                }


                student.setAddress(
                        buildPasserAddress(
                                item.getBrgy(),
                                item.getMunicipality(),
                                item.getProvince()
                        )
                );


                student.setPlaceOfBirth(
                        s(item.getBirthPlace())
                );


                student.setElementarySchool(
                        s(item.getElemSchool())
                );


                // Optional shared VIA.
                student.setVia(
                        sharedVia
                );


                // Always Girlstown.
                student.setCampus(
                        "Girlstown"
                );


                // Keep each student's own Family Saint.
                student.setFamilySaint(
                        s(item.getFamilySaint())
                );


                student.setBatchNumber(
                        batchNumber
                );


                student.setImageUrl(
                        null
                );


                studentRepository.save(
                        student
                );


                // Preserve admission record, but hide it from Passers.
                admission.setStatus(
                        "Enrolled"
                );

                admissionDataRepository.save(
                        admission
                );


                successfulPasserIds.add(
                        passerIdNum
                );

                enrolledCount++;


            } catch (Exception ex) {

                failedCount++;

                failed.add(
                        Map.of(
                                "passerIdNum",
                                passerIdNum,
                                "reason",
                                ex.getMessage() == null
                                        ? "Unknown error"
                                        : ex.getMessage()
                        )
                );
            }
        }


        response.put(
                "success",
                true
        );

        response.put(
                "enrolledCount",
                enrolledCount
        );

        response.put(
                "skippedCount",
                skippedCount
        );

        response.put(
                "failedCount",
                failedCount
        );

        response.put(
                "successfulPasserIds",
                successfulPasserIds
        );

        response.put(
                "skipped",
                skipped
        );

        response.put(
                "failed",
                failed
        );

        response.put(
                "message",
                enrolledCount
                        + " enrolled, "
                        + skippedCount
                        + " skipped, "
                        + failedCount
                        + " failed."
        );


        return ResponseEntity.ok(
                response
        );
    }


    // ============================================================
    // STUDENT MANAGER -> SENT HOME TABLE
    //
    // Transaction:
    // 1. Copy Student data into sent_home_students
    // 2. Preserve available AdmissionData details
    // 3. Mark AdmissionData as Sent Home
    // 4. Remove Student from active Student Manager
    //
    // If any step fails, the transaction rolls back.
    // ============================================================

    @PostMapping("/students/send-home")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> sendStudentHome(
            @RequestParam Long id
    ) {

        Map<String, Object> response =
                new HashMap<>();

        Optional<Student> studentOpt =
                studentRepository.findById(id);

        if (studentOpt.isEmpty()) {

            response.put("success", false);
            response.put(
                    "message",
                    "Student was not found in Student Manager."
            );

            return ResponseEntity
                    .status(404)
                    .body(response);
        }


        Student student =
                studentOpt.get();

        String idNumber =
                s(student.getIdNumber());


        if (
                !idNumber.isBlank()
                && sentHomeStudentRepository
                        .existsByIdNumber(idNumber)
        ) {

            response.put("success", false);
            response.put(
                    "message",
                    "This student is already in the Sent Home table."
            );

            return ResponseEntity
                    .status(409)
                    .body(response);
        }


        Optional<AdmissionData> admissionOpt =
                idNumber.isBlank()
                        ? Optional.empty()
                        : admissionDataRepository
                                .findFirstByIdNum(idNumber);


        SentHomeStudent sentHome =
                new SentHomeStudent();


        // --------------------------------------------------------
        // Copy Student Manager data
        // --------------------------------------------------------

        sentHome.setOriginalStudentId(
                student.getId()
        );

        sentHome.setIdNumber(
                student.getIdNumber()
        );

        sentHome.setLrn(
                student.getLrn()
        );

        sentHome.setName(
                student.getName()
        );

        sentHome.setBirthday(
                student.getBirthday()
        );

        sentHome.setAddress(
                student.getAddress()
        );

        sentHome.setPlaceOfBirth(
                student.getPlaceOfBirth()
        );

        sentHome.setElementarySchool(
                student.getElementarySchool()
        );

        sentHome.setVia(
                student.getVia()
        );

        sentHome.setCampus(
                student.getCampus()
        );

        // Preserve the student's current Family Saint in the archive
        // BEFORE AdmissionData Family Saint is cleared.
        sentHome.setFamilySaint(
                student.getFamilySaint()
        );

        sentHome.setBatchNumber(
                student.getBatchNumber()
        );

        sentHome.setImageUrl(
                student.getImageUrl()
        );

        sentHome.setSentHomeAt(
                LocalDateTime.now()
        );

        sentHome.setStatus(
                "Sent Home"
        );


        // --------------------------------------------------------
        // Copy extra Admission information when available
        // --------------------------------------------------------

        if (admissionOpt.isPresent()) {

            AdmissionData admission =
                    admissionOpt.get();

            sentHome.setAdmissionId(
                    admission.getId()
            );

            sentHome.setLastName(
                    admission.getLastName()
            );

            sentHome.setFirstName(
                    admission.getFirstName()
            );

            sentHome.setMiddleName(
                    admission.getMiddleName()
            );

            sentHome.setSuffix(
                    admission.getSuffix()
            );

            sentHome.setExamPlace(
                    admission.getExamPlace()
            );

            sentHome.setGuardianContact(
                    admission.getGuardianContact()
            );

            sentHome.setSchoolYear(
                    admission.getSchoolYear()
            );


            // Follow the current Admission Send Home behavior.
            admission.setStatus(
                    "Sent Home"
            );

            admission.setFamilySaint(
                    ""
            );

            admission.setCodeNumber(
                    null
            );

            admissionDataRepository.save(
                    admission
            );
        }


        // --------------------------------------------------------
        // Save to Sent Home table FIRST
        // --------------------------------------------------------

        SentHomeStudent saved =
                sentHomeStudentRepository.save(
                        sentHome
                );


        // --------------------------------------------------------
        // Remove from active Student Manager
        // --------------------------------------------------------

        studentRepository.delete(
                student
        );


        response.put(
                "success",
                true
        );

        response.put(
                "sentHomeId",
                saved.getId()
        );

        response.put(
                "idNumber",
                saved.getIdNumber()
        );

        response.put(
                "name",
                saved.getName()
        );

        response.put(
                "admissionUpdated",
                admissionOpt.isPresent()
        );

        response.put(
                "message",
                saved.getName()
                        + " was transferred to the Sent Home table "
                        + "and removed from Student Manager."
        );


        return ResponseEntity.ok(
                response
        );
    }


    // ============================================================
    // STUDENT MANAGER: BATCH TABS
    // ============================================================

    @GetMapping("/students/manager/batches")
    @ResponseBody
    public List<Map<String, Object>>
    managerBatchCounts() {

        return studentRepository
                .countStudentsByBatch()
                .stream()
                .map(row -> {

                    Map<String, Object> item =
                            new HashMap<>();

                    item.put(
                            "batchNumber",
                            row[0]
                    );

                    item.put(
                            "count",
                            row[1]
                    );

                    return item;
                })
                .toList();
    }

    // ============================================================
    // STUDENT MANAGER: SENT HOME TAB COUNT
    // ============================================================

    @GetMapping("/students/manager/sent-home/count")
    @ResponseBody
    public Map<String, Object> managerSentHomeCount() {

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "count",
                sentHomeStudentRepository.count()
        );

        return response;
    }


    // ============================================================
    // STUDENT MANAGER: SENT HOME TAB PAGE
    // ============================================================

    @GetMapping("/students/manager/sent-home")
    @ResponseBody
    public Map<String, Object> managerSentHomePage(

            @RequestParam(defaultValue = "")
            String query,

            @RequestParam(defaultValue = "")
            String via,

            @RequestParam(defaultValue = "")
            String saint,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size

    ) {

        int safePage =
                Math.max(page, 0);

        int safeSize =
                Math.min(
                        Math.max(size, 5),
                        100
                );

        String cleanQuery =
                query == null
                        ? ""
                        : query.trim().toLowerCase();

        String cleanVia =
                via == null
                        ? ""
                        : via.trim().toLowerCase();

        String cleanSaint =
                saint == null
                        ? ""
                        : saint.trim().toLowerCase();


        // --------------------------------------------------------
        // IMPORTANT:
        // We intentionally do the filtering in Java here.
        // This avoids JPQL/database query problems and is reliable
        // for the Sent Home archive.
        // --------------------------------------------------------

        List<SentHomeStudent> filtered =
                sentHomeStudentRepository
                        .findAllByOrderBySentHomeAtDesc()
                        .stream()
                        .filter(student -> {

                            String name =
                                    s(student.getName())
                                            .toLowerCase();

                            String idNumber =
                                    s(student.getIdNumber())
                                            .toLowerCase();

                            String lrn =
                                    s(student.getLrn())
                                            .toLowerCase();

                            String studentVia =
                                    s(student.getVia())
                                            .toLowerCase();

                            String studentSaint =
                                    s(student.getFamilySaint())
                                            .toLowerCase();


                            boolean matchesQuery =
                                    cleanQuery.isBlank()
                                    || name.contains(cleanQuery)
                                    || idNumber.contains(cleanQuery)
                                    || lrn.contains(cleanQuery);


                            boolean matchesVia =
                                    cleanVia.isBlank()
                                    || studentVia.equals(cleanVia);


                            boolean matchesSaint =
                                    cleanSaint.isBlank()
                                    || studentSaint.equals(cleanSaint);


                            return matchesQuery
                                    && matchesVia
                                    && matchesSaint;
                        })
                        .toList();


        int totalElements =
                filtered.size();

        int totalPages =
                totalElements == 0
                        ? 0
                        : (int) Math.ceil(
                                (double) totalElements
                                / safeSize
                        );


        if (
                totalPages > 0
                && safePage >= totalPages
        ) {

            safePage =
                    totalPages - 1;
        }


        int fromIndex =
                Math.min(
                        safePage * safeSize,
                        totalElements
                );

        int toIndex =
                Math.min(
                        fromIndex + safeSize,
                        totalElements
                );


        List<Map<String, Object>> rows =
                filtered
                        .subList(
                                fromIndex,
                                toIndex
                        )
                        .stream()
                        .map(this::toSentHomeManagerRow)
                        .toList();


        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "content",
                rows
        );

        response.put(
                "page",
                safePage
        );

        response.put(
                "size",
                safeSize
        );

        response.put(
                "totalElements",
                totalElements
        );

        response.put(
                "totalPages",
                totalPages
        );

        response.put(
                "first",
                safePage == 0
        );

        response.put(
                "last",
                totalPages == 0
                || safePage >= totalPages - 1
        );


        return response;
    }


    private Map<String, Object>
    toSentHomeManagerRow(
            SentHomeStudent student
    ) {

        Map<String, Object> row =
                new HashMap<>();

        row.put(
                "id",
                student.getId()
        );

        row.put(
                "lrn",
                student.getLrn()
        );

        row.put(
                "idNumber",
                student.getIdNumber()
        );

        row.put(
                "name",
                student.getName()
        );

        row.put(
                "via",
                student.getVia()
        );

        row.put(
                "campus",
                student.getCampus()
        );

        row.put(
                "familySaint",
                student.getFamilySaint()
        );

        row.put(
                "batchNumber",
                student.getBatchNumber()
        );

        row.put(
                "imageUrl",
                student.getImageUrl()
        );

        row.put(
                "sentHomeAt",
                student.getSentHomeAt()
        );

        return row;
    }


    // ============================================================
    // STUDENT MANAGER: VIA + FAMILY SAINT FILTER OPTIONS
    // ============================================================

    @GetMapping("/students/manager/filter-options")
    @ResponseBody
    public Map<String, Object>
    managerFilterOptions() {

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "vias",
                studentRepository.findDistinctVias()
        );

        response.put(
                "saints",
                studentRepository.findDistinctFamilySaints()
        );

        return response;
    }

    // ============================================================
    // STUDENT MANAGER PAGE API
    //
    // THIS IS THE IMPORTANT FIX:
    // batch + query + via + saint + pageable
    // ============================================================

    @GetMapping("/students/manager/page")
    @ResponseBody
    public Map<String, Object> managerStudentPage(

            @RequestParam(defaultValue = "")
            String batch,

            @RequestParam(defaultValue = "")
            String query,

            @RequestParam(defaultValue = "")
            String via,

            @RequestParam(defaultValue = "")
            String saint,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size

    ) {

        int safePage =
                Math.max(
                        page,
                        0
                );

        int safeSize =
                Math.min(
                        Math.max(
                                size,
                                5
                        ),
                        100
                );

        String cleanBatch =
                batch == null
                        ? ""
                        : batch.trim();

        String cleanQuery =
                query == null
                        ? ""
                        : query.trim();

        String cleanVia =
                via == null
                        ? ""
                        : via.trim();

        String cleanSaint =
                saint == null
                        ? ""
                        : saint.trim();

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(
                                Sort.Direction.ASC,
                                "name"
                        )
                );

        Page<Student> result =
                studentRepository.searchStudentManager(
                        cleanBatch,
                        cleanQuery,
                        cleanVia,
                        cleanSaint,
                        pageable
                );

        List<Map<String, Object>> rows =
                result.getContent()
                        .stream()
                        .map(this::toManagerRow)
                        .toList();

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "content",
                rows
        );

        response.put(
                "page",
                result.getNumber()
        );

        response.put(
                "size",
                result.getSize()
        );

        response.put(
                "totalElements",
                result.getTotalElements()
        );

        response.put(
                "totalPages",
                result.getTotalPages()
        );

        response.put(
                "first",
                result.isFirst()
        );

        response.put(
                "last",
                result.isLast()
        );

        return response;
    }

    // ============================================================
    // STUDENT MANAGER JSON ROW
    // ============================================================

    private Map<String, Object>
    toManagerRow(Student student) {

        Map<String, Object> row =
                new HashMap<>();

        row.put(
                "id",
                student.getId()
        );

        row.put(
                "lrn",
                student.getLrn()
        );

        row.put(
                "idNumber",
                student.getIdNumber()
        );

        row.put(
                "name",
                student.getName()
        );

        row.put(
                "birthday",
                student.getBirthday()
        );

        row.put(
                "address",
                student.getAddress()
        );

        row.put(
                "placeOfBirth",
                student.getPlaceOfBirth()
        );

        row.put(
                "elementarySchool",
                student.getElementarySchool()
        );

        row.put(
                "via",
                student.getVia()
        );

        row.put(
                "campus",
                student.getCampus()
        );

        row.put(
                "familySaint",
                student.getFamilySaint()
        );

        row.put(
                "batchNumber",
                student.getBatchNumber()
        );

        row.put(
                "imageUrl",
                student.getImageUrl()
        );

        return row;
    }

    // ============================================================
    // VISITING DAY MASTERLIST
    // KEEP THIS
    // ============================================================

    @GetMapping("/students/masterlist")
    @ResponseBody
    public List<Map<String, Object>>
    studentMasterlist(

            @RequestParam(defaultValue = "")
            String query

    ) {

        String q =
                query == null
                        ? ""
                        : query.trim();

        List<Student> students;

        if (q.isEmpty()) {

            students =
                    studentRepository.findAll()
                            .stream()
                            .limit(50)
                            .toList();

        } else {

            students =
                    studentRepository
                            .findTop50ByNameContainingIgnoreCaseOrIdNumberContainingIgnoreCaseOrLrnContainingIgnoreCase(
                                    q,
                                    q,
                                    q
                            );
        }

        return students
                .stream()
                .map(student -> {

                    Map<String, Object> row =
                            new HashMap<>();

                    row.put(
                            "idNumber",
                            student.getIdNumber()
                    );

                    row.put(
                            "lrn",
                            student.getLrn()
                    );

                    row.put(
                            "name",
                            student.getName()
                    );

                    row.put(
                            "imageUrl",
                            student.getImageUrl()
                    );

                    row.put(
                            "campus",
                            student.getCampus()
                    );

                    row.put(
                            "familySaint",
                            student.getFamilySaint()
                    );

                    row.put(
                            "batchNumber",
                            student.getBatchNumber()
                    );

                    row.put(
                            "address",
                            student.getAddress()
                    );

                    return row;
                })
                .toList();
    }

    // ============================================================
    // HELPER: BUILD NAME FROM PASSER
    // ============================================================

    private static String buildPasserStudentName(
            String lastName,
            String firstName,
            String middleName,
            String suffix
    ) {

        String last =
                s(lastName);

        String first =
                s(firstName);

        String middle =
                s(middleName);

        String suffixValue =
                s(suffix);

        StringBuilder name =
                new StringBuilder();

        if (!last.isBlank()) {
            name.append(last);
        }

        if (
                !first.isBlank()
                || !middle.isBlank()
                || !suffixValue.isBlank()
        ) {

            if (name.length() > 0) {
                name.append(", ");
            }

            if (!first.isBlank()) {
                name.append(first);
            }

            if (!middle.isBlank()) {

                if (
                        name.length() > 0
                        && name.charAt(
                                name.length() - 1
                        ) != ' '
                ) {
                    name.append(" ");
                }

                name.append(middle);
            }

            if (!suffixValue.isBlank()) {

                if (
                        name.length() > 0
                        && name.charAt(
                                name.length() - 1
                        ) != ' '
                ) {
                    name.append(" ");
                }

                name.append(suffixValue);
            }
        }

        return name
                .toString()
                .trim();
    }

    // ============================================================
    // HELPER: BUILD ADDRESS FROM PASSER
    // ============================================================

    private static String buildPasserAddress(
            String brgy,
            String municipality,
            String province
    ) {

        return Stream.of(
                        s(brgy),
                        s(municipality),
                        s(province)
                )
                .filter(
                        value ->
                                !value.isBlank()
                )
                .collect(
                        Collectors.joining(", ")
                );
    }

    // ============================================================
    // BASIC HELPERS
    // ============================================================

    private static String s(String v) {

        return v == null
                ? ""
                : v.trim();
    }

    private static String nz(String v) {

        String t =
                s(v);

        return t.isEmpty()
                ? null
                : t;
    }

    private static LocalDate parseBirthday(
            String raw
    ) {

        if (
                raw == null
                || raw.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "birthday is blank"
            );
        }

        String val =
                raw.trim();

        if (
                val.matches("\\d{1,5}")
        ) {

            int serial =
                    Integer.parseInt(val);

            return LocalDate
                    .of(
                            1899,
                            12,
                            30
                    )
                    .plusDays(serial);
        }

        String norm =
                val
                        .replace('.', '/')
                        .replace('-', '/')
                        .replace(
                                "  ",
                                " "
                        );

        try {

            return LocalDate.parse(
                    val,
                    DateTimeFormatter.ISO_LOCAL_DATE
            );

        } catch (Exception ignore) {
        }

        for (
                String p :
                new String[]{
                        "M/d/uuuu",
                        "d/M/uuuu",
                        "MM/dd/uuuu",
                        "dd/MM/uuuu",
                        "M-d-uuuu",
                        "d-M-uuuu"
                }
        ) {

            try {

                return LocalDate.parse(
                        norm,
                        DateTimeFormatter.ofPattern(
                                p,
                                Locale.ENGLISH
                        )
                );

            } catch (Exception ignore) {
            }
        }

        for (
                String p :
                new String[]{
                        "MMMM d, uuuu",
                        "MMM d, uuuu",
                        "d MMM uuuu",
                        "d-MMM-uuuu",
                        "dd-MMM-uuuu"
                }
        ) {

            try {

                return LocalDate.parse(
                        val,
                        DateTimeFormatter.ofPattern(
                                p,
                                Locale.ENGLISH
                        )
                );

            } catch (Exception ignore) {
            }
        }

        try {

            DateTimeFormatter yyFmt =
                    new DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern(
                                    "d-MMM-"
                            )
                            .appendValueReduced(
                                    ChronoField.YEAR,
                                    2,
                                    2,
                                    2000
                            )
                            .toFormatter(
                                    Locale.ENGLISH
                            );

            return LocalDate.parse(
                    val.replace(
                            '/',
                            '-'
                    ),
                    yyFmt
            );

        } catch (Exception ignore) {
        }

        throw new IllegalArgumentException(
                "Invalid birthday '"
                        + raw
                        + "'. Use yyyy-MM-dd, "
                        + "M/d/yyyy, or d-MMM-yy "
                        + "(e.g., 2010-07-17, "
                        + "7/17/2010, 17-Jul-10)."
        );
    }
}