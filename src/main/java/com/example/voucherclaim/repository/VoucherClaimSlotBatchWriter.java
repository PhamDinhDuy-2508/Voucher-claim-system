package com.example.voucherclaim.repository;

import com.example.voucherclaim.entity.VoucherClaimSlot;
import com.example.voucherclaim.entity.VoucherClaimSlotId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Repository
public class VoucherClaimSlotBatchWriter {
    private static final int BATCH_SIZE = 500;

    private final EntityManager entityManager;

    public VoucherClaimSlotBatchWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Materializes inventory in the caller's activation transaction. Direct persist is kept
     * inside the persistence layer so it can flush and clear each bounded batch without
     * exposing EntityManager details to the business service.
     */
    public void insertAll(String campaignId, long totalQuantity) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Slot allocation requires an active campaign transaction");
        }

        Instant createdAt = Instant.now();
        for (long slotId = 1; slotId <= totalQuantity; slotId++) {
            entityManager.persist(new VoucherClaimSlot(
                    new VoucherClaimSlotId(campaignId, slotId), createdAt));

            // Flush sends the JDBC batch; clear prevents the first-level cache growing to 100k entities.
            if (slotId % BATCH_SIZE == 0) {
                flushAndClear();
            }
        }
        if (totalQuantity % BATCH_SIZE != 0) {
            flushAndClear();
        }
    }

    /** Flushes pending inserts and releases managed slot instances while keeping the transaction open. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
