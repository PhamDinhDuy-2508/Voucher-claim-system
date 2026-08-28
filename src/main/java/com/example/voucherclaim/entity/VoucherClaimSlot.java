package com.example.voucherclaim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "voucher_claim_slot")
public class VoucherClaimSlot {
    @EmbeddedId
    private VoucherClaimSlotId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoucherClaimSlot() {
    }

    public VoucherClaimSlot(VoucherClaimSlotId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public VoucherClaimSlotId getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
}
