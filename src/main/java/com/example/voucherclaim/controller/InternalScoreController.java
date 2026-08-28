package com.example.voucherclaim.controller;

import com.example.voucherclaim.facade.ScoreSnapshotFacade;
import com.example.voucherclaim.model.request.ScoreSnapshotRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trusted internal HTTP boundary for publishing immutable claim-priority score snapshots. */
@RestController
@RequestMapping("/v1/internal/score-snapshots")
public class InternalScoreController {
    private static final Logger log = LoggerFactory.getLogger(InternalScoreController.class);
    private final ScoreSnapshotFacade scoreSnapshotFacade;

    public InternalScoreController(ScoreSnapshotFacade scoreSnapshotFacade) {
        this.scoreSnapshotFacade = scoreSnapshotFacade;
    }

    /** Stores a score snapshot while deliberately excluding the internal token from logs. */
    @PutMapping
    public ResponseEntity<Void> put(
            @RequestHeader("X-Internal-Token") String token,
            @Valid @RequestBody ScoreSnapshotRequest request
    ) {
        log.debug("Score snapshot request received campaignId={} userId={} score={}",
                request.getCampaignId(), request.getUserId(), request.getScore());
        scoreSnapshotFacade.put(token, request);
        log.debug("Score snapshot request completed campaignId={} userId={}",
                request.getCampaignId(), request.getUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
