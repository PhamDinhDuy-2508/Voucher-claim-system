CREATE TABLE IF NOT EXISTS voucher_campaign (
    campaign_id CHAR(36) NOT NULL,
    merchant_id CHAR(16) NOT NULL,
    name VARCHAR(200) NOT NULL,
    discount_type VARCHAR(32) NOT NULL,
    discount_value DECIMAL(19, 4) NOT NULL,
    total_quantity BIGINT NOT NULL,
    unallocated_quantity BIGINT NOT NULL,
    priority_window_ms INT NOT NULL,
    priority_policy_version VARCHAR(64) NOT NULL,
    creation_idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    voucher_expires_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campaign_id),
    CONSTRAINT uk_campaign_creation_idempotency
        UNIQUE (merchant_id, creation_idempotency_key),
    CONSTRAINT ck_campaign_quantity
        CHECK (total_quantity >= 0 AND unallocated_quantity >= 0 AND unallocated_quantity <= total_quantity),
    CONSTRAINT ck_campaign_priority_window
        CHECK (priority_window_ms BETWEEN 10 AND 1000),
    INDEX idx_campaign_status_time (status, start_at, end_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS voucher_claim_slot (
    campaign_id CHAR(36) NOT NULL,
    slot_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campaign_id, slot_id),
    CONSTRAINT fk_slot_campaign
        FOREIGN KEY (campaign_id) REFERENCES voucher_campaign (campaign_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS voucher_claim (
    claim_id CHAR(36) NOT NULL,
    campaign_id CHAR(36) NOT NULL,
    user_id CHAR(16) NOT NULL,
    voucher_code VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    priority_score_snapshot BIGINT NOT NULL,
    claimed_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (claim_id),
    CONSTRAINT uk_voucher_code UNIQUE (voucher_code),
    CONSTRAINT uk_campaign_user UNIQUE (campaign_id, user_id),
    CONSTRAINT fk_claim_campaign
        FOREIGN KEY (campaign_id) REFERENCES voucher_campaign (campaign_id),
    INDEX idx_claim_idempotency_lookup (campaign_id, user_id, idempotency_key),
    INDEX idx_user_claim_history (user_id, claimed_at DESC, claim_id),
    INDEX idx_campaign_claim_history (campaign_id, claimed_at, claim_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS claim_request (
    request_id VARCHAR(64) NOT NULL,
    campaign_id CHAR(36) NOT NULL,
    user_id CHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    priority_score_snapshot BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    max_attempt INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until DATETIME(6) NULL,
    result_type VARCHAR(32) NULL,
    result_message VARCHAR(255) NULL,
    claim_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (request_id),
    CONSTRAINT uk_claim_request_idempotency
        UNIQUE (campaign_id, user_id, idempotency_key),
    CONSTRAINT fk_claim_request_campaign
        FOREIGN KEY (campaign_id) REFERENCES voucher_campaign (campaign_id),
    CONSTRAINT ck_claim_request_status
        CHECK (status IN ('PENDING', 'QUEUED', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'REJECTED')),
    CONSTRAINT ck_claim_request_attempt
        CHECK (attempt >= 0 AND max_attempt > 0 AND attempt <= max_attempt),
    INDEX idx_claim_request_recovery (status, next_attempt_at, lease_until, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS outbox_event (
    event_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    publish_status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT ck_outbox_publish_status
        CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'DEAD_LETTER')),
    CONSTRAINT ck_outbox_retry_count
        CHECK (retry_count >= 0),
    INDEX idx_outbox_unpublished (publish_status, created_at, event_id)
) ENGINE=InnoDB;
