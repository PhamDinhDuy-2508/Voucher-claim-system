package com.example.voucherclaim.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class ScoreSnapshotRequest {
    @NotBlank
    private String userId;

    @Min(0)
    @Max(1_000_000_000L)
    private long score;

    public ScoreSnapshotRequest() {
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getScore() { return score; }
    public void setScore(long score) { this.score = score; }
}
