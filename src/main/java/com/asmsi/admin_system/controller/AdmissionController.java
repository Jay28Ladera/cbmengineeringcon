package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.FamilySaintDTO;
import com.asmsi.admin_system.entity.AdmissionData;
import com.asmsi.admin_system.entity.FinalStudent;
import com.asmsi.admin_system.entity.SentHomeStudent;
import com.asmsi.admin_system.repository.AdmissionDataRepository;
import com.asmsi.admin_system.repository.AdmissionSettingRepository;
import com.asmsi.admin_system.repository.FamilySaintRepository;
import com.asmsi.admin_system.repository.FinalStudentRepository;
import com.asmsi.admin_system.repository.SentHomeStudentRepository;
import com.asmsi.admin_system.service.AdmissionService;
import com.asmsi.admin_system.service.UploadService;
import com.asmsi.admin_system.service.XmlParserHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AdmissionController {

    @Autowired
    private AdmissionService admissionService;

    @Autowired
    private AdmissionSettingRepository admissionSettingRepository;

    @Autowired
    private FinalStudentRepository finalStudentRepository;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private FamilySaintRepository familySaintRepository;

    @Autowired
    private AdmissionDataRepository admissionDataRepository;

    @Autowired
    private SentHomeStudentRepository sentHomeStudentRepository;

    @GetMapping("/admission")
    public String admissionPage(Model model,
                                @ModelAttribute("formData") AdmissionData formData,
                                @ModelAttribute("schoolYear") String schoolYear) {

        model.addAttribute("activePage", "admission");

        if (formData != null && formData.getIdNumber() != null) {
            model.addAttribute("studentData", formData);
        }

        if (schoolYear != null && !schoolYear.isEmpty()) {
            model.addAttribute("schoolYear", schoolYear);
        }

        return "admission";
    }

    @GetMapping("/upload-database")
    public String uploadDatabasePage(Model model) {
        List<Map<String, Object>> summaryList = uploadService.getUploadSummary();

        model.addAttribute("studentCounts", summaryList);
        model.addAttribute("activePage", "upload-database");

        return "upload-database";
    }

    @PostMapping("/upload-xml")
    public String handleXmlUpload(@RequestParam("schoolYear") String schoolYear,
                                  @RequestParam("xmlFile") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        try {
            List<AdmissionData> dataList = XmlParserHelper.parse(file, schoolYear);
            admissionService.saveAll(dataList);

            redirectAttributes.addFlashAttribute("successMessage", "XML file successfully uploaded and saved!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to process XML file: " + e.getMessage());
        }

        return "redirect:/upload-database";
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public String submitForm(
            @RequestParam String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp,

            @RequestParam String lastName,
            @RequestParam String firstName,
            @RequestParam(required = false, defaultValue = "") String middleName,
            @RequestParam(required = false, defaultValue = "") String suffix,

            @RequestParam String barangay,
            @RequestParam String municipality,
            @RequestParam String province,

            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate birthdate,
            @RequestParam String birthPlace,
            @RequestParam int age,

            @RequestParam(required = false, defaultValue = "") String examPlace,
            @RequestParam String idNumber,
            @RequestParam(required = false, defaultValue = "") String lrn,

            @RequestParam String religion,
            @RequestParam String gender,
            @RequestParam String citizenship,

            @RequestParam String elementarySchool,
            @RequestParam String elemAddress,

            @RequestParam String fatherName,
            @RequestParam(required = false, defaultValue = "") String fatherOccupation,
            @RequestParam(required = false, defaultValue = "") String fatherContact,

            @RequestParam String motherName,
            @RequestParam(required = false, defaultValue = "") String motherOccupation,
            @RequestParam(required = false, defaultValue = "") String motherContact,

            @RequestParam(required = false, defaultValue = "") String guardianName,
            @RequestParam(required = false, defaultValue = "") String guardianRelationship,
            @RequestParam(required = false, defaultValue = "") String guardianOccupation,
            @RequestParam(required = false, defaultValue = "") String guardianContact,

            @RequestParam String psastatus,
            @RequestParam String baptismalstatus,
            @RequestParam String confirmationstatus,
            @RequestParam String goodmoralstatus,
            @RequestParam String form138status,

            // ✅ NEW FIELD FOR FORM 137E
            @RequestParam String form137estatus,

            @RequestParam String indigencystatus,
            @RequestParam String philhealthstatus,
            @RequestParam String kasunduansatus,
            @RequestParam String photoStatus,
            @RequestParam String medicalrecordstatus,

            @RequestParam String schoolYear,
            @RequestParam String familySaint,
            @RequestParam String codeNumber,

            @RequestParam(required = false, defaultValue = "") String motherSister,
            @RequestParam(required = false, defaultValue = "") String buildingName,

            RedirectAttributes redirectAttributes,
            Model model
    ) {
        try {
            AdmissionData admissionData = admissionService.searchStudents(idNumber)
                    .stream()
                    .findFirst()
                    .orElse(new AdmissionData());

            boolean isNew = admissionData.getId() == null;

            admissionData.setIdNum(idNumber);
            admissionData.setIdNumber(idNumber);

            admissionData.setLastName(lastName);
            admissionData.setFirstName(firstName);
            admissionData.setMiddleName(middleName);
            admissionData.setSuffix(suffix);

            admissionData.setBrgy(barangay);
            admissionData.setMunicipality(municipality);
            admissionData.setProvince(province);

            admissionData.setBirthDate(birthdate);
            admissionData.setBirthPlace(birthPlace);
            admissionData.setAge(age);

            admissionData.setExamPlace(examPlace);
            admissionData.setLrn(lrn);

            admissionData.setReligion(religion);
            admissionData.setGender(gender);
            admissionData.setCitizenship(citizenship);

            admissionData.setElemSchool(elementarySchool);
            admissionData.setSchoolAddress(elemAddress);

            admissionData.setFatherName(fatherName);
            admissionData.setFatherOccupation(fatherOccupation);
            admissionData.setFatherContact(fatherContact);

            admissionData.setMotherName(motherName);
            admissionData.setMotherOccupation(motherOccupation);
            admissionData.setMotherContact(motherContact);

            admissionData.setGuardianName(guardianName);
            admissionData.setGuardianRelationship(guardianRelationship);
            admissionData.setGuardianOccupation(guardianOccupation);
            admissionData.setGuardianContact(guardianContact);

            admissionData.setPsastatus(psastatus);
            admissionData.setBaptismalstatus(baptismalstatus);
            admissionData.setConfirmationstatus(confirmationstatus);
            admissionData.setGoodmoralstatus(goodmoralstatus);
            admissionData.setForm138status(form138status);

            // ✅ THIS IS THE MISSING SAVE LINE
            admissionData.setForm137estatus(form137estatus);

            admissionData.setIndigencystatus(indigencystatus);
            admissionData.setPhilhealthstatus(philhealthstatus);
            admissionData.setKasunduansatus(kasunduansatus);
            admissionData.setPhotoStatus(photoStatus);
            admissionData.setMedicalrecordstatus(medicalrecordstatus);

            admissionData.setSchoolYear(schoolYear);
            admissionData.setFamilySaint(familySaint);
            admissionData.setCodeNumber(Integer.parseInt(codeNumber));

            admissionData.setMotherSister(motherSister);
            admissionData.setBuildingName(buildingName);

            admissionData.setStatus(status);
            admissionData.setTimestamp(timestamp);

            boolean isDuplicate = admissionService.getAllAdmissions()
                    .stream()
                    .anyMatch(existing ->
                            schoolYear.equals(existing.getSchoolYear()) &&
                                    familySaint.equalsIgnoreCase(existing.getFamilySaint()) &&
                                    Integer.parseInt(codeNumber) == existing.getCodeNumber() &&
                                    !idNumber.equals(existing.getIdNum())
                    );

            if (isDuplicate) {
                redirectAttributes.addFlashAttribute("errorMessage", "Code number already taken for this Family Saint and School Year.");
                redirectAttributes.addFlashAttribute("stayOnStep", 3);
                redirectAttributes.addFlashAttribute("formData", admissionData);
                redirectAttributes.addFlashAttribute("schoolYear", schoolYear);

                return "redirect:/admission";
            }

            admissionService.save(admissionData);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    isNew ? "New student record added!" : "Student record updated!"
            );

            return "redirect:/admission";

        } catch (Exception e) {
            e.printStackTrace();

            model.addAttribute("errorMessage", "Failed to save student: " + e.getMessage());
            model.addAttribute("stayOnStep", 3);

            return "admission";
        }
    }

    @GetMapping("/api/admission/search")
    @ResponseBody
    public ResponseEntity<?> searchById(@RequestParam("idNumber") String idNum) {
        try {
            List<AdmissionData> data = admissionService.searchStudents(idNum);

            System.out.println("Found: " + data.size() + " records");

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/admission/school-years")
    @ResponseBody
    public List<String> getSchoolYears() {
        return admissionSettingRepository.findAllSchoolYears();
    }

    @GetMapping("/family-saint-settings/api")
    @ResponseBody
    public List<FamilySaintDTO> getSaintsBySchoolYear(@RequestParam String schoolYear) {
        return familySaintRepository.findSaintsWithDetails(schoolYear)
                .stream()
                .map(row -> new FamilySaintDTO(
                        row[0] != null ? row[0].toString() : "",
                        row[1] != null ? row[1].toString() : "",
                        row[2] != null ? row[2].toString() : ""
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/api/admission/used-codes")
    @ResponseBody
    public List<Integer> getUsedCodesForSaint(@RequestParam("schoolYear") String schoolYear,
                                              @RequestParam("familySaint") String familySaint) {
        List<FinalStudent> students = finalStudentRepository.findBySchoolYearAndFamilySaint(schoolYear, familySaint);

        return students.stream()
                .map(FinalStudent::getCodeNumber)
                .distinct()
                .collect(Collectors.toList());
    }

    @GetMapping("/studentpasser")
    public String viewPasserAdmission(Model model) {

        List<AdmissionData> passerList = admissionDataRepository.findAll()
                .stream()
                .filter(student ->
                        student.getStatus() == null
                        || !student.getStatus().equalsIgnoreCase("Enrolled")
                )
                .toList();

        model.addAttribute("passerList", passerList);
        model.addAttribute("activePage", "studentpasser");

        return "passeradmission";
    }

    @PostMapping("/api/admission/delete-passer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePasser(
            @RequestParam Long id
    ) {

        Map<String, Object> response = new java.util.HashMap<>();

        try {

            if (!admissionDataRepository.existsById(id)) {
                response.put("success", false);
                response.put("message", "Passer record not found.");

                return ResponseEntity
                        .status(404)
                        .body(response);
            }

            admissionDataRepository.deleteById(id);

            response.put("success", true);
            response.put(
                    "message",
                    "Passer deleted successfully."
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            e.printStackTrace();

            response.put("success", false);
            response.put(
                    "message",
                    "Unable to delete passer: " + e.getMessage()
            );

            return ResponseEntity
                    .status(500)
                    .body(response);
        }
    }


    @PostMapping("/api/admission/delete-passers-bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deletePassersBulk(
            @RequestBody Map<String, List<Long>> request
    ) {

        Map<String, Object> response =
                new java.util.HashMap<>();

        try {

            List<Long> ids =
                    request.get("ids");

            if (ids == null || ids.isEmpty()) {

                response.put(
                        "success",
                        false
                );

                response.put(
                        "message",
                        "No passers were selected."
                );

                return ResponseEntity
                        .badRequest()
                        .body(response);
            }


            List<Long> deletedIds =
                    new java.util.ArrayList<>();


            for (Long id : ids) {

                if (
                        id != null
                        && admissionDataRepository
                                .existsById(id)
                ) {

                    admissionDataRepository
                            .deleteById(id);

                    deletedIds.add(id);
                }
            }


            response.put(
                    "success",
                    true
            );

            response.put(
                    "deletedCount",
                    deletedIds.size()
            );

            response.put(
                    "deletedIds",
                    deletedIds
            );

            response.put(
                    "message",
                    deletedIds.size()
                            + " passer(s) deleted successfully."
            );


            return ResponseEntity.ok(
                    response
            );


        } catch (Exception e) {

            e.printStackTrace();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    "Unable to bulk delete passers: "
                            + e.getMessage()
            );

            return ResponseEntity
                    .status(500)
                    .body(response);
        }
    }


    @GetMapping("/senthome")
    public String viewSentHomePage(Model model) {

        List<SentHomeStudent> sentHomeList =
                sentHomeStudentRepository
                        .findAllByOrderBySentHomeAtDesc();

        model.addAttribute(
                "sentHomeList",
                sentHomeList
        );

        model.addAttribute(
                "activePage",
                "senthome"
        );

        return "senthome";
    }

    @PostMapping("/api/admission/send-home")
    public ResponseEntity<?> sendHome(@RequestParam String idNum) {
        List<AdmissionData> students = admissionDataRepository.findByIdNum(idNum);

        if (!students.isEmpty()) {
            AdmissionData student = students.get(0);

            System.out.println("Before update status: " + student.getStatus());

            student.setStatus("Sent Home");
            student.setFamilySaint("");
            student.setCodeNumber(null);

            admissionDataRepository.save(student);

            System.out.println("Saved status: " + student.getStatus());

            return ResponseEntity.ok("Updated");
        }

        return ResponseEntity.status(404).body("Student not found");
    }
}