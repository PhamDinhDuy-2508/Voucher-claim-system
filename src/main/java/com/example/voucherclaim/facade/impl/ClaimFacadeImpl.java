package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.facade.ClaimFacade;
import com.example.voucherclaim.model.ClaimOperationResult;
import com.example.voucherclaim.model.request.CreateClaimRequest;
import com.example.voucherclaim.service.ClaimService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ClaimFacadeImpl implements ClaimFacade {
    private final ClaimService claimService;

    public ClaimFacadeImpl(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Override
    public ClaimOperationResult claim(CreateClaimRequest request) {
        return claimService.claim(request.getCampaignId(), request.getUserId());
    }

    @Override
    public Optional<ClaimOperationResult> getOperation(String requestId) {
        return claimService.getOperation(requestId);
    }

    @Override
    public Optional<VoucherClaim> getClaim(String campaignId, String userId) {
        return claimService.getClaim(campaignId, userId);
    }
}
