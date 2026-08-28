package com.example.voucherclaim.model;

import java.math.BigDecimal;
import java.time.Instant;

public class CreateCampaignCommand {
    private final String merchantId;
    private final String idempotencyKey;
    private final String name;
    private final String discountType;
    private final BigDecimal discountValue;
    private final long totalQuantity;
    private final String priorityPolicyVersion;
    private final Instant startAt;
    private final Instant endAt;
    private final Instant voucherExpiresAt;

    public CreateCampaignCommand(
            String merchantId,
            String idempotencyKey,
            String name,
            String discountType,
            BigDecimal discountValue,
            long totalQuantity,
            String priorityPolicyVersion,
            Instant startAt,
            Instant endAt,
            Instant voucherExpiresAt
    ) {
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.totalQuantity = totalQuantity;
        this.priorityPolicyVersion = priorityPolicyVersion;
        this.startAt = startAt;
        this.endAt = endAt;
        this.voucherExpiresAt = voucherExpiresAt;
    }


    public String getMerchantId() { return merchantId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getName() { return name; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public long getTotalQuantity() { return totalQuantity; }
    public String getPriorityPolicyVersion() { return priorityPolicyVersion; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Instant getVoucherExpiresAt() { return voucherExpiresAt; }
}
