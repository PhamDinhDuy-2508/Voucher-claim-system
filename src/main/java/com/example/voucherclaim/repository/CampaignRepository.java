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
}
