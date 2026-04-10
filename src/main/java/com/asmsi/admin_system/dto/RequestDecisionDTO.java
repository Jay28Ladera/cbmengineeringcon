package com.asmsi.admin_system.dto;

public class RequestDecisionDTO {

    private Long requestId;
    private String stage;     // CHECKED, NOTED, APPROVED1, APPROVED2
    private String decision;  // APPROVED, DENIED
    private String reason;    // required if DENIED
    private String passcode;  // required if APPROVED

    public RequestDecisionDTO() {}

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }
}
