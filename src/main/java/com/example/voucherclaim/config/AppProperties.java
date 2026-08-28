package com.example.voucherclaim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Auth auth;
    private final Idempotency idempotency;
    private final Priority priority;
    private final ClaimRequest claimRequest;
    private final Outbox outbox;
    private final Kafka kafka;

    public AppProperties(Auth auth, Idempotency idempotency, Priority priority,
                         ClaimRequest claimRequest, Outbox outbox, Kafka kafka) {
        this.auth = auth;
        this.idempotency = idempotency;
        this.priority = priority;
        this.claimRequest = claimRequest;
        this.outbox = outbox;
        this.kafka = kafka;
    }

    public Auth getAuth() { return auth; }
    public Idempotency getIdempotency() { return idempotency; }
    public Priority getPriority() { return priority; }
    public ClaimRequest getClaimRequest() { return claimRequest; }
    public Outbox getOutbox() { return outbox; }
    public Kafka getKafka() { return kafka; }

    public static class Auth {
        private final String internalToken;

        public Auth(String internalToken) {
            this.internalToken = internalToken;
        }

        public String getInternalToken() { return internalToken; }
    }

    public static class Idempotency {
        private final Duration resultTtl;

        public Idempotency(Duration resultTtl) {
            this.resultTtl = resultTtl;
        }

        public Duration getResultTtl() { return resultTtl; }
    }

    public static class Priority {
        private final Duration collectionWindow;
        private final Duration schedulerScanInterval;
        private final Duration resultWaitTimeout;
        private final Duration resultPollInterval;
        private final long maxPendingPerCampaign;
        private final int admissionBatchSize;
        private final int workerThreads;
        private final Duration queueKeyGrace;

        public Priority(
                Duration collectionWindow,
                Duration schedulerScanInterval,
                Duration resultWaitTimeout,
                Duration resultPollInterval,
                long maxPendingPerCampaign,
                int admissionBatchSize,
                int workerThreads,
                Duration queueKeyGrace
        ) {
            this.collectionWindow = collectionWindow;
            this.schedulerScanInterval = schedulerScanInterval;
            this.resultWaitTimeout = resultWaitTimeout;
            this.resultPollInterval = resultPollInterval;
            this.maxPendingPerCampaign = maxPendingPerCampaign;
            this.admissionBatchSize = admissionBatchSize;
            this.workerThreads = workerThreads;
            this.queueKeyGrace = queueKeyGrace;
        }

        public Duration getCollectionWindow() { return collectionWindow; }
        public Duration getSchedulerScanInterval() { return schedulerScanInterval; }
        public Duration getResultWaitTimeout() { return resultWaitTimeout; }
        public Duration getResultPollInterval() { return resultPollInterval; }
        public long getMaxPendingPerCampaign() { return maxPendingPerCampaign; }
        public int getAdmissionBatchSize() { return admissionBatchSize; }
        public int getWorkerThreads() { return workerThreads; }
        public Duration getQueueKeyGrace() { return queueKeyGrace; }
    }

    public static class Outbox {
        private final Duration pollInterval;
        private final int batchSize;
        private final int maxRetries;

        public Outbox(Duration pollInterval, int batchSize, int maxRetries) {
            this.pollInterval = pollInterval;
            this.batchSize = batchSize;
            this.maxRetries = maxRetries;
        }

        public Duration getPollInterval() { return pollInterval; }
        public int getBatchSize() { return batchSize; }
        public int getMaxRetries() { return maxRetries; }
    }

    public static class ClaimRequest {
        private final Duration leaseDuration;
        private final Duration retryDelay;
        private final Duration queueRecheckDelay;
        private final int maxAttempts;
        private final int recoveryBatchSize;

        public ClaimRequest(Duration leaseDuration, Duration retryDelay, Duration queueRecheckDelay,
                            int maxAttempts, int recoveryBatchSize) {
            this.leaseDuration = leaseDuration;
            this.retryDelay = retryDelay;
            this.queueRecheckDelay = queueRecheckDelay;
            this.maxAttempts = maxAttempts;
            this.recoveryBatchSize = recoveryBatchSize;
        }

        public Duration getLeaseDuration() { return leaseDuration; }
        public Duration getRetryDelay() { return retryDelay; }
        public Duration getQueueRecheckDelay() { return queueRecheckDelay; }
        public int getMaxAttempts() { return maxAttempts; }
        public int getRecoveryBatchSize() { return recoveryBatchSize; }
    }

    public static class Kafka {
        private final String notificationTopic;
        private final String claimRequestTopic;
        private final int notificationTopicPartitions;
        private final Duration sendTimeout;

        public Kafka(String notificationTopic, String claimRequestTopic,
                     int notificationTopicPartitions, Duration sendTimeout) {
            this.notificationTopic = notificationTopic;
            this.claimRequestTopic = claimRequestTopic;
            this.notificationTopicPartitions = notificationTopicPartitions;
            this.sendTimeout = sendTimeout;
        }

        public String getNotificationTopic() { return notificationTopic; }
        public String getClaimRequestTopic() { return claimRequestTopic; }
        public int getNotificationTopicPartitions() { return notificationTopicPartitions; }
        public Duration getSendTimeout() { return sendTimeout; }
    }
}
