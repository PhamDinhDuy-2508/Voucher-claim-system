package com.example.voucherclaim.service.impl;

import com.example.voucherclaim.config.AppProperties;
import com.example.voucherclaim.domain.type.CampaignActivationJobStatus;
import com.example.voucherclaim.entity.CampaignActivationJob;
import com.example.voucherclaim.repository.CampaignActivationJobRepository;
import com.example.voucherclaim.repository.CampaignRepository;
import com.example.voucherclaim.repository.VoucherClaimSlotBatchWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignActivationServiceImplTest {
    private final CampaignActivationJobRepository jobRepository = mock(CampaignActivationJobRepository.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final VoucherClaimSlotBatchWriter slotWriter = mock(VoucherClaimSlotBatchWriter.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final AppProperties properties = mock(AppProperties.class);
    private final AppProperties.Activation activation =
            new AppProperties.Activation(Duration.ofSeconds(30), Duration.ofSeconds(1), 1_000, 10);
    private final CampaignActivationServiceImpl service = new CampaignActivationServiceImpl(
            jobRepository, campaignRepository, slotWriter, transactionTemplate, properties);

    @BeforeEach
    void executeTransactionCallbacksSynchronously() {
        when(properties.getActivation()).thenReturn(activation);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void commitsOneBoundedBatchAndLeavesTheCampaignActivating() {
        CampaignActivationJob job = new CampaignActivationJob("campaign-1", 1_500, Instant.now());
        when(jobRepository.findEligibleCampaignIds(anyList(), eq(CampaignActivationJobStatus.PROCESSING),
                any(), any())).thenReturn(List.of("campaign-1"));
        when(jobRepository.lockByCampaignId("campaign-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);

        service.processDueJobs();

        verify(slotWriter).insertRange("campaign-1", 1, 1_000);
        verify(campaignRepository, never()).activate(any(), any());
        assertThat(job.getStatus()).isEqualTo(CampaignActivationJobStatus.PENDING);
        assertThat(job.getNextSlotId()).isEqualTo(1_001);
    }

    @Test
    void activatesTheCampaignInTheFinalBatchTransaction() {
        CampaignActivationJob job = new CampaignActivationJob("campaign-1", 500, Instant.now());
        when(jobRepository.findEligibleCampaignIds(anyList(), eq(CampaignActivationJobStatus.PROCESSING),
                any(), any())).thenReturn(List.of("campaign-1"));
        when(jobRepository.lockByCampaignId("campaign-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(campaignRepository.activate(eq("campaign-1"), any())).thenReturn(1);

        service.processDueJobs();

        verify(slotWriter).insertRange("campaign-1", 1, 500);
        verify(campaignRepository).activate(eq("campaign-1"), any());
        assertThat(job.getStatus()).isEqualTo(CampaignActivationJobStatus.COMPLETED);
    }
}
