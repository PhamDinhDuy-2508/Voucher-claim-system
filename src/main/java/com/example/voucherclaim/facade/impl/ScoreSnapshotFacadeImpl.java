package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.facade.ScoreSnapshotFacade;
import com.example.voucherclaim.model.request.ScoreSnapshotRequest;
import com.example.voucherclaim.service.ScoreSnapshotService;
import com.example.voucherclaim.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ScoreSnapshotFacadeImpl implements ScoreSnapshotFacade {
    private final ScoreSnapshotService scoreSnapshotService;
    private final AppProperties properties;

    public ScoreSnapshotFacadeImpl(ScoreSnapshotService scoreSnapshotService, AppProperties properties) {
        this.scoreSnapshotService = scoreSnapshotService;
        this.properties = properties;
    }

    /** Authenticates the trusted caller before forwarding the score command to the service. */
    @Override
    public void put(String internalToken, ScoreSnapshotRequest request) {
        if (!MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                properties.getAuth().getInternalToken().getBytes(StandardCharsets.UTF_8))) {
            throw ServiceException.forbidden("INVALID_INTERNAL_TOKEN", "Internal token is invalid");
        }
        scoreSnapshotService.put(request.getCampaignId(), request.getUserId(), request.getScore());
    }
}
