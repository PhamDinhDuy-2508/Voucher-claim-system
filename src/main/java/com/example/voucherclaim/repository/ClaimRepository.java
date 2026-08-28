package com.example.voucherclaim.repository;

import com.example.voucherclaim.entity.VoucherClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<VoucherClaim, UUID> {
    Optional<VoucherClaim> findByCampaignIdAndUserIdAndIdempotencyKey(
            String campaignId,
            String userId,
            String idempotencyKey
    );

    Optional<VoucherClaim> findByCampaignIdAndUserId(String campaignId, String userId);

}
