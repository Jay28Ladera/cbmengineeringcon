package com.asmsi.admin_system.controller;

import com.asmsi.admin_system.dto.PasserHealthRequest;
import com.asmsi.admin_system.entity.AdmissionData;
import com.asmsi.admin_system.entity.PasserHealthRecord;
import com.asmsi.admin_system.entity.User;
import com.asmsi.admin_system.repository.AdmissionDataRepository;
import com.asmsi.admin_system.repository.PasserHealthRecordRepository;
import com.asmsi.admin_system.repository.UserRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class PasserHealthController {

    private final AdmissionDataRepository admissionDataRepository;
    private final PasserHealthRecordRepository passerHealthRecordRepository;
    private final UserRepository userRepository;

    public PasserHealthController(AdmissionDataRepository admissionDataRepository,
                                  PasserHealthRecordRepository passerHealthRecordRepository,
                                  UserRepository userRepository) {
        this.admissionDataRepository = admissionDataRepository;
        this.passerHealthRecordRepository = passerHealthRecordRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/admission/passer-health")
    public String passerHealthPage(Model model, Principal principal) {

        List<AdmissionData> arrivedPassers =
                admissionDataRepository.findByStatusIgnoreCaseOrderByLastNameAsc("Arrived");

        List<String> idNums = arrivedPassers.stream()
                .map(AdmissionData::getIdNum)
                .filter(id -> id != null && !id.trim().isEmpty())
                .toList();

        List<PasserHealthRecord> healthRecords =
                passerHealthRecordRepository.findAllByIdNumIn(idNums);

        Map<String, PasserHealthRecord> healthRecordMap = new HashMap<>();

        for (PasserHealthRecord record : healthRecords) {
            if (record.getIdNum() != null) {
                healthRecordMap.put(record.getIdNum(), record);
            }
        }

        long normalCount = 0;
        long malnourishedCount = 0;
        long overweightCount = 0;
        long obeseCount = 0;
        long notRecordedCount = 0;

        for (AdmissionData student : arrivedPassers) {
            PasserHealthRecord record = healthRecordMap.get(student.getIdNum());

            if (record == null || record.getHealthStatus() == null || record.getHealthStatus().trim().isEmpty()) {
                notRecordedCount++;
                continue;
            }

            String status = record.getHealthStatus().toLowerCase();

            if (status.contains("normal")) {
                normalCount++;
            } else if (status.contains("malnourished") || status.contains("underweight") || status.contains("severe")) {
                malnourishedCount++;
            } else if (status.contains("overweight")) {
                overweightCount++;
            } else if (status.contains("obese")) {
                obeseCount++;
            } else {
                notRecordedCount++;
            }
        }

        model.addAttribute("arrivedPasserList", arrivedPassers);
        model.addAttribute("passerList", arrivedPassers);
        model.addAttribute("healthRecordMap", healthRecordMap);

        model.addAttribute("normalCount", normalCount);
        model.addAttribute("malnourishedCount", malnourishedCount);
        model.addAttribute("overweightCount", overweightCount);
        model.addAttribute("obeseCount", obeseCount);
        model.addAttribute("notRecordedCount", notRecordedCount);

        addSidebarAttributes(model, principal);

        return "passerhealth";
    }

    @PostMapping("/admission/passer-health/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePasserHealth(@RequestBody PasserHealthRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (request.getIdNum() == null || request.getIdNum().trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "ID number is required.");
            return ResponseEntity.badRequest().body(response);
        }

        if (request.getWeightKg() == null || request.getHeightCm() == null || request.getBmi() == null) {
            response.put("success", false);
            response.put("message", "Weight, height, and BMI are required.");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AdmissionData> studentOptional =
                admissionDataRepository.findFirstByIdNum(request.getIdNum());

        if (studentOptional.isEmpty()) {
            response.put("success", false);
            response.put("message", "Student not found.");
            return ResponseEntity.badRequest().body(response);
        }

        AdmissionData student = studentOptional.get();

        PasserHealthRecord record = passerHealthRecordRepository.findByIdNum(request.getIdNum())
                .orElse(new PasserHealthRecord());

        record.setIdNum(student.getIdNum());
        record.setLastName(student.getLastName());
        record.setFirstName(student.getFirstName());
        record.setMiddleName(student.getMiddleName());
        record.setExamPlace(student.getExamPlace());
        record.setGuardianContact(student.getGuardianContact());
        record.setCodeNumber(student.getCodeNumber() != null ? String.valueOf(student.getCodeNumber()) : null);
        record.setFamilySaint(student.getFamilySaint());

        record.setWeightKg(request.getWeightKg());
        record.setHeightCm(request.getHeightCm());
        record.setBmi(request.getBmi());
        record.setHealthStatus(request.getHealthStatus());
        record.setRemarks(request.getRemarks());

        passerHealthRecordRepository.save(record);

        response.put("success", true);
        response.put("message", "Health record saved successfully.");
        response.put("idNum", record.getIdNum());
        response.put("bmi", record.getBmi());
        response.put("healthStatus", record.getHealthStatus());

        return ResponseEntity.ok(response);
    }

    private void addSidebarAttributes(Model model, Principal principal) {

        String username = principal != null ? principal.getName() : null;
        String role = "user";

        if (username != null) {
            Optional<User> userOptional = userRepository.findByUsername(username);

            if (userOptional.isPresent() && userOptional.get().getRole() != null) {
                role = String.valueOf(userOptional.get().getRole()).trim();

                if (role.equalsIgnoreCase("ROLE_ADMIN") || role.equalsIgnoreCase("ADMIN")) {
                    role = "admin";
                } else if (role.equalsIgnoreCase("ROLE_USER") || role.equalsIgnoreCase("USER")) {
                    role = "user";
                } else {
                    role = role.toLowerCase();
                }
            }
        }

        model.addAttribute("username", username);
        model.addAttribute("role", role);

        model.addAttribute("activePage", "passer-health");

        model.addAttribute("studentInfoExpanded", false);
        model.addAttribute("asmsiExpanded", false);
        model.addAttribute("admissionExpanded", true);
        model.addAttribute("attendanceExpanded", false);
        model.addAttribute("eventsExpanded", false);
        model.addAttribute("familyDayExpanded", false);
    }
}