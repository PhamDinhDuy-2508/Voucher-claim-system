package com.example.voucherclaim.facade;

import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.model.ClaimOperationResult;
import com.example.voucherclaim.model.request.CreateClaimRequest;

import java.util.Optional;

/** API-facing orchestration boundary for voucher claim use cases. */
public interface ClaimFacade {
    /** Maps the public claim command into its campaign-and-user natural business key. */
    ClaimOperationResult claim(CreateClaimRequest request);

    /** Reads one asynchronous operation by the requestId returned from admission. */
    Optional<ClaimOperationResult> getOperation(String requestId);

    /** Reads one user's durable claim for the requested campaign. */
    Optional<VoucherClaim> getClaim(String campaignId, String userId);
}
