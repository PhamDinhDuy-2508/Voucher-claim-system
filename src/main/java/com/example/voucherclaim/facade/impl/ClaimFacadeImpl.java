package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.entity.VoucherClaim;
import com.example.voucherclaim.facade.ClaimFacade;
import com.example.voucherclaim.model.ProcessingResult;
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
    public ProcessingResult claim(String idempotencyKey, CreateClaimRequest request) {
        return claimService.claim(request.getCampaignId(), request.getUserId(), idempotencyKey);
    }

    @Override
    public Optional<VoucherClaim> getClaim(String campaignId, String userId) {
        return claimService.getClaim(campaignId, userId);
    }
}
