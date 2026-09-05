package com.example.voucherclaim.repository;

import com.example.voucherclaim.entity.VoucherCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface CampaignRepository extends JpaRepository<VoucherCampaign, String> {
    Optional<VoucherCampaign> findByMerchantIdAndCreationIdempotencyKey(
            String merchantId,
            String creationIdempotencyKey
    );

    Optional<VoucherCampaign> findByCampaignIdAndMerchantId(String campaignId, String merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select campaign
            from VoucherCampaign campaign
            where campaign.campaignId = :campaignId
              and campaign.merchantId = :merchantId
            """)
    Optional<VoucherCampaign> lockByIdAndMerchant(
            @Param("campaignId") String campaignId,
            @Param("merchantId") String merchantId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VoucherCampaign campaign
            set campaign.status = com.example.voucherclaim.domain.type.CampaignStatus.ACTIVE,
                campaign.unallocatedQuantity = 0,
                campaign.version = campaign.version + 1,
                campaign.updatedAt = :updatedAt
            where campaign.campaignId = :campaignId
              and campaign.status = com.example.voucherclaim.domain.type.CampaignStatus.ACTIVATING
            """)
    int activate(
            @Param("campaignId") String campaignId,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VoucherCampaign campaign
            set campaign.status = com.example.voucherclaim.domain.type.CampaignStatus.SOLD_OUT,
                campaign.version = campaign.version + 1,
                campaign.updatedAt = :updatedAt
            where campaign.campaignId = :campaignId
              and campaign.status = com.example.voucherclaim.domain.type.CampaignStatus.ACTIVE
            """)
    int markSoldOut(
            @Param("campaignId") String campaignId,
            @Param("updatedAt") Instant updatedAt
    );

    /** Finds newly expired campaigns and ENDED campaigns whose slot cleanup is incomplete. */
    @Query(value = """
            SELECT campaign.campaign_id
            FROM voucher_campaign campaign
            WHERE (campaign.status IN ('DRAFT', 'ACTIVATING', 'ACTIVE')
                       AND campaign.end_at <= :now)
               OR (campaign.status = 'ENDED'
                       AND EXISTS (
                           SELECT 1
                           FROM voucher_claim_slot slot
                           WHERE slot.campaign_id = campaign.campaign_id
                       ))
            ORDER BY campaign.end_at, campaign.campaign_id
            """, nativeQuery = true)
    List<String> findExpirationCleanupCandidates(
            @Param("now") Instant now,
            Pageable pageable
    );

    /** Closes claim admission before any remaining inventory is deleted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VoucherCampaign campaign
            set campaign.status = com.example.voucherclaim.domain.type.CampaignStatus.ENDED,
                campaign.version = campaign.version + 1,
                campaign.updatedAt = :updatedAt
            where campaign.campaignId = :campaignId
              and campaign.endAt <= :updatedAt
              and campaign.status in (
                  com.example.voucherclaim.domain.type.CampaignStatus.DRAFT,
                  com.example.voucherclaim.domain.type.CampaignStatus.ACTIVATING,
                  com.example.voucherclaim.domain.type.CampaignStatus.ACTIVE
              )
            """)
    int markEndedIfExpired(
            @Param("campaignId") String campaignId,
            @Param("updatedAt") Instant updatedAt
    );
}
