package com.example.voucherclaim.service;

import com.example.voucherclaim.model.PriorityRequest;
import com.example.voucherclaim.model.ProcessingResult;

/** Correctness-critical database transaction for one admitted claim request. */
public interface ClaimTransactionService {
    /** Atomically resolves the claim against inventory; success writes VoucherClaimed outbox. */
    ProcessingResult execute(PriorityRequest request);
}
