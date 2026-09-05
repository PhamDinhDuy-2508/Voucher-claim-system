package com.example.voucherclaim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private final Auth auth;
    private final Availability availability;
    private final ResultCache resultCache;
    private final Priority priority;
    private final ClaimRequest claimRequest;
    private final Activation activation;
    private final ExpirationCleanup expirationCleanup;
    private final UserScoreSeed userScoreSeed;
    private final Outbox outbox;
    private final Kafka kafka;

    public AppProperties(Auth auth, Availability availability, ResultCache resultCache, Priority priority,
                         ClaimRequest claimRequest, Activation activation, ExpirationCleanup expirationCleanup,
                         UserScoreSeed userScoreSeed,
                         Outbox outbox, Kafka kafka) {
        this.auth = auth;
        this.availability = availability;
        this.resultCache = resultCache;
        this.priority = priority;
        this.claimRequest = claimRequest;
        this.activation = activation;
        this.expirationCleanup = expirationCleanup;
        this.userScoreSeed = userScoreSeed;
        this.outbox = outbox;
        this.kafka = kafka;
    }

    public Auth getAuth() { return auth; }
    public Availability getAvailability() { return availability; }
    public ResultCache getResultCache() { return resultCache; }
    public Priority getPriority() { return priority; }
    public ClaimRequest getClaimRequest() { return claimRequest; }
    public Activation getActivation() { return activation; }
    public ExpirationCleanup getExpirationCleanup() { return expirationCleanup; }
    public UserScoreSeed getUserScoreSeed() { return userScoreSeed; }
    public Outbox getOutbox() { return outbox; }
    public Kafka getKafka() { return kafka; }

    public static class Auth {
        private final String internalToken;

        public Auth(String internalToken) {
            this.internalToken = internalToken;
        }

        public String getInternalToken() { return internalToken; }
    }

    public static class ResultCache {
        private final Duration ttl;

        public ResultCache(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getTtl() { return ttl; }
    }

    public static class Availability {
        private final Duration cacheTtl;

        public Availability(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public Duration getCacheTtl() { return cacheTtl; }
    }

    public static class Priority {
        private final Duration collectionWindow;
        private final Duration schedulerScanInterval;
        private final long maxPendingPerCampaign;
        private final int admissionBatchSize;
        private final int minWorkerThreads;
        private final int maxWorkerThreads;
        private final Duration queueKeyGrace;

        public Priority(
                Duration collectionWindow,
                Duration schedulerScanInterval,
                long maxPendingPerCampaign,
                int admissionBatchSize,
                int minWorkerThreads,
                int maxWorkerThreads,
                Duration queueKeyGrace
        ) {
            this.collectionWindow = collectionWindow;
            this.schedulerScanInterval = schedulerScanInterval;
            this.maxPendingPerCampaign = maxPendingPerCampaign;
            this.admissionBatchSize = admissionBatchSize;
            this.minWorkerThreads = minWorkerThreads;
            this.maxWorkerThreads = maxWorkerThreads;
            this.queueKeyGrace = queueKeyGrace;
        }

        public Duration getCollectionWindow() { return collectionWindow; }
        public Duration getSchedulerScanInterval() { return schedulerScanInterval; }
        public long getMaxPendingPerCampaign() { return maxPendingPerCampaign; }
        public int getAdmissionBatchSize() { return admissionBatchSize; }
        public int getMinWorkerThreads() { return minWorkerThreads; }
        public int getMaxWorkerThreads() { return maxWorkerThreads; }
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

    public static class Activation {
        private final Duration leaseDuration;
        private final Duration retryDelay;
        private final int slotBatchSize;
        private final int recoveryBatchSize;

        public Activation(Duration leaseDuration, Duration retryDelay,
                          int slotBatchSize, int recoveryBatchSize) {
            this.leaseDuration = leaseDuration;
            this.retryDelay = retryDelay;
            this.slotBatchSize = slotBatchSize;
            this.recoveryBatchSize = recoveryBatchSize;
        }

        public Duration getLeaseDuration() { return leaseDuration; }
        public Duration getRetryDelay() { return retryDelay; }
        public int getSlotBatchSize() { return slotBatchSize; }
        public int getRecoveryBatchSize() { return recoveryBatchSize; }
    }

    public static class ExpirationCleanup {
        private final int campaignBatchSize;
        private final int slotDeleteBatchSize;

        public ExpirationCleanup(int campaignBatchSize, int slotDeleteBatchSize) {
            this.campaignBatchSize = campaignBatchSize;
            this.slotDeleteBatchSize = slotDeleteBatchSize;
        }

        public int getCampaignBatchSize() { return campaignBatchSize; }
        public int getSlotDeleteBatchSize() { return slotDeleteBatchSize; }
    }

    public static class UserScoreSeed {
        private final boolean enabled;
        private final int count;
        private final long startId;

        public UserScoreSeed(boolean enabled, int count, long startId) {
            this.enabled = enabled;
            this.count = count;
            this.startId = startId;
        }

        public boolean isEnabled() { return enabled; }
        public int getCount() { return count; }
        public long getStartId() { return startId; }
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
        private final int notificationTopicPartitions;
        private final Duration sendTimeout;

        public Kafka(String notificationTopic, int notificationTopicPartitions,
                     Duration sendTimeout) {
            this.notificationTopic = notificationTopic;
            this.notificationTopicPartitions = notificationTopicPartitions;
            this.sendTimeout = sendTimeout;
        }

        public String getNotificationTopic() { return notificationTopic; }
        public int getNotificationTopicPartitions() { return notificationTopicPartitions; }
        public Duration getSendTimeout() { return sendTimeout; }
    }
}
