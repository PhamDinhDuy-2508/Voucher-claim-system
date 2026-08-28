package com.example.voucherclaim.facade.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.exception.ServiceException;
import com.example.voucherclaim.model.request.ScoreSnapshotRequest;
import com.example.voucherclaim.service.ScoreSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScoreSnapshotFacadeImplTest {
    private final ScoreSnapshotService scoreSnapshotService = mock(ScoreSnapshotService.class);
    private final AppProperties properties = mock(AppProperties.class);
    private final AppProperties.Auth auth = mock(AppProperties.Auth.class);
    private final ScoreSnapshotFacadeImpl facade = new ScoreSnapshotFacadeImpl(scoreSnapshotService, properties);

    @BeforeEach
    void setUp() {
        when(properties.getAuth()).thenReturn(auth);
        when(auth.getInternalToken()).thenReturn("trusted-token");
    }

    @Test
    void authenticatesThenDelegatesToTheScoreService() {
        ScoreSnapshotRequest request = request();

        facade.put("trusted-token", request);

        verify(scoreSnapshotService).put(request.getCampaignId(), request.getUserId(), request.getScore());
    }

    @Test
    void rejectsAnInvalidInternalTokenBeforeCallingTheService() {
        assertThatThrownBy(() -> facade.put("wrong-token", request()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Internal token is invalid");
        verifyNoInteractions(scoreSnapshotService);
    }

    private ScoreSnapshotRequest request() {
        ScoreSnapshotRequest request = new ScoreSnapshotRequest();
        request.setCampaignId("019c6fa6-5e22-7abc-9123-abcdef123456");
        request.setUserId("2000000000000001");
        request.setScore(900);
        return request;
    }
}
