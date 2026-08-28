package com.example.voucherclaim.model;

import com.example.voucherclaim.domain.type.ProcessingResultType;
import com.example.voucherclaim.entity.VoucherClaim;

public class ProcessingResult {
    private String requestId;
    private ProcessingResultType type;
    private VoucherClaim claim;
    private String message;

    public ProcessingResult() {
    }

    public ProcessingResult(String requestId, ProcessingResultType type, VoucherClaim claim, String message) {
        this.requestId = requestId;
        this.type = type;
        this.claim = claim;
        this.message = message;
    }

    public static ProcessingResult created(String requestId, VoucherClaim claim) {
        return new ProcessingResult(requestId, ProcessingResultType.CREATED, claim, null);
    }

    public static ProcessingResult replayed(String requestId, VoucherClaim claim) {
        return new ProcessingResult(requestId, ProcessingResultType.REPLAYED, claim, null);
    }

    public static ProcessingResult failure(String requestId, ProcessingResultType type, String message) {
        return new ProcessingResult(requestId, type, null, message);
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public ProcessingResultType getType() { return type; }
    public void setType(ProcessingResultType type) { this.type = type; }
    public VoucherClaim getClaim() { return claim; }
    public void setClaim(VoucherClaim claim) { this.claim = claim; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

}
