package com.example.voucherclaim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VoucherClaimSlotId implements Serializable {
    @Column(name = "campaign_id", nullable = false, length = 36)
    private String campaignId;

    @Column(name = "slot_id", nullable = false)
    private long slotId;

    protected VoucherClaimSlotId() {
    }

    public VoucherClaimSlotId(String campaignId, long slotId) {
        this.campaignId = campaignId;
        this.slotId = slotId;
    }


    public String getCampaignId() { return campaignId; }
    public long getSlotId() { return slotId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VoucherClaimSlotId that)) return false;
        return slotId == that.slotId && Objects.equals(campaignId, that.campaignId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(campaignId, slotId);
    }
}
