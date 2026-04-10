package com.asmsi.admin_system.service;

import com.asmsi.admin_system.dto.RequestDecisionDTO;
import com.asmsi.admin_system.entity.DecisionStatus;
import com.asmsi.admin_system.entity.ItemRequest;
import com.asmsi.admin_system.repository.ItemRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemRequestDecisionService {

    // ✅ temporary passcode (change here anytime)
    private static final String TEMP_PASSCODE = "1234";

    private final ItemRequestRepository repo;

    public ItemRequestDecisionService(ItemRequestRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void applyDecision(RequestDecisionDTO dto, String username) {

        ItemRequest r = repo.findById(dto.getRequestId())
                .orElseThrow(() -> new RuntimeException("Request not found"));

        String stage = dto.getStage();
        String decision = dto.getDecision();

        if (stage == null || stage.trim().isEmpty()) throw new RuntimeException("Stage is required");
        if (decision == null || decision.trim().isEmpty()) throw new RuntimeException("Decision is required");

        stage = stage.trim().toUpperCase();
        decision = decision.trim().toUpperCase();

        if (!decision.equals("APPROVED") && !decision.equals("DENIED")) {
            throw new RuntimeException("Invalid decision");
        }

        // APPROVED requires passcode
        if (decision.equals("APPROVED")) {
            if (dto.getPasscode() == null || dto.getPasscode().trim().isEmpty()) {
                throw new RuntimeException("Passcode required");
            }
            if (!TEMP_PASSCODE.equals(dto.getPasscode().trim())) {
                throw new RuntimeException("Invalid passcode");
            }
        }

        // DENIED requires reason
        if (decision.equals("DENIED")) {
            if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
                throw new RuntimeException("Reason required");
            }
        }

        switch (stage) {
            case "CHECKED" -> {
                if (r.getCheckedStatus() != DecisionStatus.PENDING) throw new RuntimeException("Already decided");
                if (decision.equals("APPROVED")) {
                    r.setCheckedStatus(DecisionStatus.APPROVED);
                } else {
                    r.setCheckedStatus(DecisionStatus.DENIED);
                    r.setCheckedReason(dto.getReason().trim());
                }
                r.setCheckedBy(username);
            }

            case "NOTED" -> {
                if (r.getNotedStatus() != DecisionStatus.PENDING) throw new RuntimeException("Already decided");
                if (decision.equals("APPROVED")) {
                    r.setNotedStatus(DecisionStatus.APPROVED);
                } else {
                    r.setNotedStatus(DecisionStatus.DENIED);
                    r.setNotedReason(dto.getReason().trim());
                }
                r.setNotedBy(username);
            }

            case "APPROVED1" -> {
                if (r.getApproved1Status() != DecisionStatus.PENDING) throw new RuntimeException("Already decided");
                if (decision.equals("APPROVED")) {
                    r.setApproved1Status(DecisionStatus.APPROVED);
                } else {
                    r.setApproved1Status(DecisionStatus.DENIED);
                    r.setApproved1Reason(dto.getReason().trim());
                }
                r.setApprovedBy1(username);
            }

            case "APPROVED2" -> {
                if (r.getApproved2Status() != DecisionStatus.PENDING) throw new RuntimeException("Already decided");
                if (decision.equals("APPROVED")) {
                    r.setApproved2Status(DecisionStatus.APPROVED);
                } else {
                    r.setApproved2Status(DecisionStatus.DENIED);
                    r.setApproved2Reason(dto.getReason().trim());
                }
                r.setApprovedBy2(username);
            }

            default -> throw new RuntimeException("Invalid stage");
        }

        repo.save(r);
    }
}
