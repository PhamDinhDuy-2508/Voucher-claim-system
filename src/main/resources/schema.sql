-- Relationships are enforced by application logic. Drop legacy foreign keys before
-- Hibernate updates column definitions; each statement is safe on repeated startups.
SET @drop_slot_campaign_fk = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE voucher_claim_slot DROP FOREIGN KEY fk_slot_campaign',
              'SELECT 1')
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voucher_claim_slot'
      AND CONSTRAINT_NAME = 'fk_slot_campaign'
);
PREPARE drop_slot_campaign_fk_statement FROM @drop_slot_campaign_fk;
EXECUTE drop_slot_campaign_fk_statement;
DEALLOCATE PREPARE drop_slot_campaign_fk_statement;

SET @drop_claim_campaign_fk = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE voucher_claim DROP FOREIGN KEY fk_claim_campaign',
              'SELECT 1')
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voucher_claim'
      AND CONSTRAINT_NAME = 'fk_claim_campaign'
);
PREPARE drop_claim_campaign_fk_statement FROM @drop_claim_campaign_fk;
EXECUTE drop_claim_campaign_fk_statement;
DEALLOCATE PREPARE drop_claim_campaign_fk_statement;

SET @drop_claim_request_campaign_fk = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE claim_request DROP FOREIGN KEY fk_claim_request_campaign',
              'SELECT 1')
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'claim_request'
      AND CONSTRAINT_NAME = 'fk_claim_request_campaign'
);
PREPARE drop_claim_request_campaign_fk_statement FROM @drop_claim_request_campaign_fk;
EXECUTE drop_claim_request_campaign_fk_statement;
DEALLOCATE PREPARE drop_claim_request_campaign_fk_statement;

-- Migrate the former header-based claim idempotency schema to the natural
-- (campaign_id, user_id) business key without requiring a manual DDL step.
SET @drop_claim_request_idempotency_index = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE claim_request DROP INDEX uk_claim_request_idempotency',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'claim_request'
      AND INDEX_NAME = 'uk_claim_request_idempotency'
);
PREPARE drop_claim_request_idempotency_index_statement FROM @drop_claim_request_idempotency_index;
EXECUTE drop_claim_request_idempotency_index_statement;
DEALLOCATE PREPARE drop_claim_request_idempotency_index_statement;

SET @drop_claim_idempotency_index = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE voucher_claim DROP INDEX idx_claim_idempotency_lookup',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voucher_claim'
      AND INDEX_NAME = 'idx_claim_idempotency_lookup'
);
PREPARE drop_claim_idempotency_index_statement FROM @drop_claim_idempotency_index;
EXECUTE drop_claim_idempotency_index_statement;
DEALLOCATE PREPARE drop_claim_idempotency_index_statement;

SET @drop_claim_request_idempotency_column = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE claim_request DROP COLUMN idempotency_key',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'claim_request'
      AND COLUMN_NAME = 'idempotency_key'
);
PREPARE drop_claim_request_idempotency_column_statement FROM @drop_claim_request_idempotency_column;
EXECUTE drop_claim_request_idempotency_column_statement;
DEALLOCATE PREPARE drop_claim_request_idempotency_column_statement;

SET @drop_claim_idempotency_column = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE voucher_claim DROP COLUMN idempotency_key',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'voucher_claim'
      AND COLUMN_NAME = 'idempotency_key'
);
PREPARE drop_claim_idempotency_column_statement FROM @drop_claim_idempotency_column;
EXECUTE drop_claim_idempotency_column_statement;
DEALLOCATE PREPARE drop_claim_idempotency_column_statement;

SET @add_claim_request_user_index = (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.TABLES
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_request')
        AND NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE()
                         AND TABLE_NAME = 'claim_request'
                         AND INDEX_NAME = 'uk_claim_request_user'),
        'ALTER TABLE claim_request ADD CONSTRAINT uk_claim_request_user UNIQUE (campaign_id, user_id)',
        'SELECT 1')
);
PREPARE add_claim_request_user_index_statement FROM @add_claim_request_user_index;
EXECUTE add_claim_request_user_index_statement;
DEALLOCATE PREPARE add_claim_request_user_index_statement;

CREATE TABLE IF NOT EXISTS voucher_campaign (
    campaign_id VARCHAR(36) NOT NULL,
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

CREATE TABLE IF NOT EXISTS user_score (
    user_id VARCHAR(16) NOT NULL,
    score BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_user_score CHECK (score BETWEEN 0 AND 1000000000)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS campaign_activation_job (
    campaign_id VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    next_slot_id BIGINT NOT NULL,
    total_quantity BIGINT NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campaign_id),
    CONSTRAINT ck_activation_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'COMPLETED', 'CANCELED')),
    CONSTRAINT ck_activation_job_cursor
        CHECK (next_slot_id >= 1 AND total_quantity > 0 AND attempt >= 0),
    INDEX idx_activation_job_recovery (status, next_attempt_at, lease_until, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS voucher_claim_slot (
    campaign_id VARCHAR(36) NOT NULL,
    slot_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campaign_id, slot_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS voucher_claim (
    claim_id CHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    user_id CHAR(16) NOT NULL,
    voucher_code VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority_score_snapshot BIGINT NOT NULL,
    claimed_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (claim_id),
    CONSTRAINT uk_voucher_code UNIQUE (voucher_code),
    CONSTRAINT uk_campaign_user UNIQUE (campaign_id, user_id),
    INDEX idx_user_claim_history (user_id, claimed_at DESC, claim_id),
    INDEX idx_campaign_claim_history (campaign_id, claimed_at, claim_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS claim_request (
    request_id VARCHAR(64) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    user_id CHAR(16) NOT NULL,
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
    CONSTRAINT uk_claim_request_user
        UNIQUE (campaign_id, user_id),
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

-- Keep the activation-job state constraint aligned on existing databases as well as fresh ones.
SET @drop_activation_job_status_check = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE campaign_activation_job DROP CHECK ck_activation_job_status',
              'SELECT 1')
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'campaign_activation_job'
      AND CONSTRAINT_NAME = 'ck_activation_job_status'
);
PREPARE drop_activation_job_status_check_statement FROM @drop_activation_job_status_check;
EXECUTE drop_activation_job_status_check_statement;
DEALLOCATE PREPARE drop_activation_job_status_check_statement;

ALTER TABLE campaign_activation_job
    ADD CONSTRAINT ck_activation_job_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'COMPLETED', 'CANCELED'));
