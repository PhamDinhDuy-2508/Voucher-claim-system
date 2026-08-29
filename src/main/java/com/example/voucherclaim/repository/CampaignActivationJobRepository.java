package com.example.voucherclaim.repository;

import com.example.voucherclaim.domain.type.CampaignActivationJobStatus;
import com.example.voucherclaim.entity.CampaignActivationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA boundary for durable campaign activation work. */
public interface CampaignActivationJobRepository extends JpaRepository<CampaignActivationJob, String> {
    @Query("""
            select job.campaignId
            from CampaignActivationJob job
            where ((job.status in :readyStatuses and job.nextAttemptAt <= :now)
                or (job.status = :processingStatus and job.leaseUntil <= :now))
            order by job.nextAttemptAt, job.createdAt
            """)
    List<String> findEligibleCampaignIds(
            @Param("readyStatuses") List<CampaignActivationJobStatus> readyStatuses,
            @Param("processingStatus") CampaignActivationJobStatus processingStatus,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from CampaignActivationJob job where job.campaignId = :campaignId")
    Optional<CampaignActivationJob> lockByCampaignId(@Param("campaignId") String campaignId);
}
