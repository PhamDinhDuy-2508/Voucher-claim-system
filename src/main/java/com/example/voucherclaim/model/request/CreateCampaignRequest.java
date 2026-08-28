package com.example.voucherclaim.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public class CreateCampaignRequest {
    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 32)
    private String discountType;

    @NotNull
    @DecimalMin("0.0001")
    private BigDecimal discountValue;

    @Min(1)
    @Max(100_000)
    private long totalQuantity;

    @NotBlank
    @Pattern(regexp = "SCORE_DESC_THEN_REQUEST_MEMBER_DESC")
    private String priorityOrder;

    @NotNull
    private Instant startAt;

    @NotNull
    private Instant endAt;

    @NotNull
    private Instant voucherExpiresAt;

    public CreateCampaignRequest() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public long getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(long totalQuantity) { this.totalQuantity = totalQuantity; }
    public String getPriorityOrder() { return priorityOrder; }
    public void setPriorityOrder(String priorityOrder) { this.priorityOrder = priorityOrder; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public Instant getVoucherExpiresAt() { return voucherExpiresAt; }
    public void setVoucherExpiresAt(Instant voucherExpiresAt) { this.voucherExpiresAt = voucherExpiresAt; }
}
