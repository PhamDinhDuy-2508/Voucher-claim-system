package com.example.voucherclaim.service;

import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.ProcessingResult;

import java.util.Optional;

/** Business use cases for claiming and reading a user's campaign voucher. */
public interface ClaimService {
    /** Admits or replays the one natural-idempotency operation for a campaign and user. */
    ProcessingResult claim(String campaignId, String userId);

    /** Reads the durable voucher currently owned by a user in one campaign. */
    Optional<VoucherClaim> getClaim(String campaignId, String userId);
}
