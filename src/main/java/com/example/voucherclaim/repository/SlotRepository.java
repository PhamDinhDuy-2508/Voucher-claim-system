package com.example.voucherclaim.repository;

import com.example.voucherclaim.entity.VoucherClaimSlot;
import com.example.voucherclaim.entity.VoucherClaimSlotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SlotRepository extends JpaRepository<VoucherClaimSlot, VoucherClaimSlotId> {
    // JPQL has no portable SKIP LOCKED syntax, so this remains a native query managed by
    // Spring Data JPA. The selected entity stays locked until the surrounding transaction ends.
    @Query(value = """
            SELECT campaign_id, slot_id, created_at
            FROM voucher_claim_slot
            WHERE campaign_id = :campaignId
            ORDER BY slot_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<VoucherClaimSlot> lockOneAvailableSlot(@Param("campaignId") String campaignId);

    boolean existsByIdCampaignId(String campaignId);
}
