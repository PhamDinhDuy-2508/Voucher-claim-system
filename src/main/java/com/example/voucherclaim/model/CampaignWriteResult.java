package com.example.voucherclaim.model;

import com.example.voucherclaim.entity.VoucherCampaign;

public class CampaignWriteResult {
    private final VoucherCampaign campaign;
    private final boolean replayed;

    public CampaignWriteResult(VoucherCampaign campaign, boolean replayed) {
        this.campaign = campaign;
        this.replayed = replayed;
    }

    public VoucherCampaign getCampaign() { return campaign; }
    public boolean isReplayed() { return replayed; }
}
