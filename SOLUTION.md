# Voucher Claim System — Solution

HTTP contract: [API-specs.md](API-specs.md).

## 1. Scope

### Functional requirements

- A merchant creates a voucher campaign.
- A merchant activates a campaign.
- A user can claim at most one voucher in a campaign.
- Requests with higher scores are processed first within the same priority window.
- A retry with the same `Idempotency-Key` returns the previously created claim.
- The system supports a burst of 5,000 requests per second for one campaign.
- A successful claim creates a `VoucherClaimed` outbox event. The publisher sends it to Kafka for asynchronous consumption by the Notification Service.

### Non-functional requirements

- Keep the issued claim count within campaign inventory.
- Enforce one claim per `(campaign_id, user_id)`.
- Use MySQL as the source of truth for requests, inventory, claims, and events.
- Use Redis for priority scheduling and short-lived result caching.
- Admit requests directly at the expected traffic level of 5,000 requests/s/campaign.
- Allocate independent slot rows in short transactions, allowing workers to make progress in parallel.

### Out of scope

- Voucher redemption.
- Payment.
- Campaign search and catalog.
- JWT implementation.
- The external Notification Service.
- Cross-region active-active writes.

## 2. Traffic Model

| Item | Value |
|---|---:|
| Peak ingress | 5,000 claim requests/s/campaign |
| Internal priority collection window | 10–1,000 ms; default 100 ms |
| Maximum pending Redis queue | 10,000 requests/campaign |
| HTTP result wait | 1,500 ms |
| Idempotency result TTL | 30 seconds |
| Maximum inventory in the current implementation | 100,000 vouchers/campaign |

## 3. API Contract

### 3.1 Create campaign

```http
POST /v1/campaigns
X-Merchant-Id: <merchant_id>
Idempotency-Key: <key>
Content-Type: application/json
```

```json
{
  "name": "Summer campaign",
  "discountType": "PERCENTAGE",
  "discountValue": 15,
  "totalQuantity": 10000,
  "priorityOrder": "SCORE_DESC_THEN_REQUEST_MEMBER_DESC",
  "startAt": "2026-08-01T00:00:00Z",
  "endAt": "2026-09-01T00:00:00Z",
  "voucherExpiresAt": "2026-10-01T00:00:00Z"
}
```

Responses:

- `201 Created`: a new campaign.
- `200 OK` with `Idempotent-Replayed: true`: replay of the same creation key.
- `409 Conflict`: invalid campaign window.

### 3.2 Activate campaign

```http
POST /v1/campaigns/activate
X-Merchant-Id: <merchant_id>
Content-Type: application/json
```

```json
{
  "campaignId": "<campaign_uuid_v7>"
}
```

Responses:

- `200 OK`: campaign activated.
- `200 OK` with `Idempotent-Replayed: true`: campaign was already active.
- `404 Not Found`: the campaign is missing or belongs to another merchant.
- `409 Conflict`: the campaign state blocks activation.

### 3.3 Claim voucher

```http
POST /api/v1/claims
Idempotency-Key: <key>
Content-Type: application/json
```

```json
{
  "userId": "<user_id>",
  "campaignId": "<campaign_uuid_v7>"
}
```

| HTTP | Code | Meaning |
|---:|---|---|
| 201 | — | New claim succeeded |
| 200 | — | Successful claim replay |
| 409 | `ALREADY_CLAIMED` | User claimed with another key |
| 409 | `SOLD_OUT` | Campaign inventory is exhausted |
| 410 | `CAMPAIGN_NOT_ACTIVE` | Campaign is inactive or outside its claim window |
| 503 | `CLAIM_BUSY` | Queue, database, or result timeout; retry with the same key |

### 3.4 Read claim

```http
GET /v1/claims/me?campaignId=<campaign_uuid_v7>
X-User-Id: <user_id>
```

## 4. High-Level Architecture

```mermaid
flowchart LR
    C[Client] -->|1. Submit claim| API[Claim API]
    API -->|2. Persist claim_request| DB[(MySQL)]
    API -->|3. Direct ZADD NX<br/>after commit| R[(Redis ZSET)]
    API -->|4. Mark QUEUED| DB
    R -->|5. ZPOPMAX highest scores| PS[Priority Scheduler]
    PS -->|6. Dispatch within capacity| CW[Claim Worker]
    CW -->|7. Lease request; consume slot;<br/>write claim and VoucherClaimed outbox| DB
    DB -->|8. Poll VoucherClaimed| OP[Outbox Publisher]
    OP -->|9. Publish VoucherClaimed| K[(Kafka)]
    K -->|10. Consume notification event| NC[Notification Consumer]
    NC -->|11. Send notification| NS[Notification Service]
    DB -. Recover due or expired requests .-> RW[Recovery Watcher]
    RW -. ZADD NX repair .-> R
```

The API saves the request in MySQL before adding it to Redis. The scheduler then takes the highest-scored requests and sends them to a limited worker pool.

MySQL remains the source of truth. If Redis loses a request, the Recovery Watcher can add it back from `claim_request`. Kafka is only used after a claim succeeds, to deliver the `VoucherClaimed` notification.

## 5. Data Model

### Identifier format

`campaignId` is an RFC 9562 UUIDv7 containing a 48-bit Unix millisecond timestamp and 74 random bits. Each application instance generates the ID locally.

```mermaid
erDiagram
    VOUCHER_CAMPAIGN ||--o{ VOUCHER_CLAIM_SLOT : owns
    VOUCHER_CAMPAIGN ||--o{ CLAIM_REQUEST : accepts
    VOUCHER_CAMPAIGN ||--o{ VOUCHER_CLAIM : issues
    CLAIM_REQUEST o|--o| VOUCHER_CLAIM : resolves_to
    VOUCHER_CLAIM ||--|| OUTBOX_EVENT : produces

    VOUCHER_CAMPAIGN {
        char36 campaign_id PK
        varchar merchant_id
        varchar creation_idempotency_key
        bigint total_quantity
        bigint unallocated_quantity
        int priority_window_ms
        varchar status
        datetime start_at
        datetime end_at
        datetime voucher_expires_at
        bigint version
    }

    VOUCHER_CLAIM_SLOT {
        char36 campaign_id PK
        bigint slot_id PK
        datetime created_at
    }

    VOUCHER_CLAIM {
        char36 claim_id PK
        char36 campaign_id
        varchar user_id
        varchar idempotency_key
        varchar voucher_code UK
        bigint priority_score_snapshot
        varchar status
        datetime claimed_at
        datetime expires_at
    }

    CLAIM_REQUEST {
        varchar64 request_id PK
        char36 campaign_id
        varchar user_id
        varchar idempotency_key
        bigint priority_score_snapshot
        varchar status
        int attempt
        int max_attempt
        datetime next_attempt_at
        varchar lease_owner
        datetime lease_until
        varchar result_type
        char36 claim_id
        datetime created_at
        datetime updated_at
    }

    OUTBOX_EVENT {
        char36 event_id PK
        char36 aggregate_id
        varchar event_type
        json payload
        varchar publish_status
        int retry_count
        datetime created_at
    }
```

### Required constraints

```sql
UNIQUE (merchant_id, creation_idempotency_key)
UNIQUE (campaign_id, user_id)
UNIQUE (voucher_code)
UNIQUE (campaign_id, user_id, idempotency_key) -- claim_request
PRIMARY KEY (campaign_id, slot_id)
```

### Required indexes

```sql
INDEX idx_claim_idempotency_lookup
    (campaign_id, user_id, idempotency_key)

INDEX idx_campaign_status_time
    (status, start_at, end_at)

INDEX idx_outbox_unpublished
    (publish_status, created_at, event_id)

INDEX idx_claim_request_recovery
    (status, next_attempt_at, lease_until, created_at)
```

### State machines

Campaign lifecycle:

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Create campaign
    DRAFT --> ACTIVE: Activate / all slots materialized
    ACTIVE --> SOLD_OUT: Inventory exhausted
    ACTIVE --> ENDED: Claim window elapsed
    SOLD_OUT --> [*]
    ENDED --> [*]

    note right of DRAFT
        Waiting for activation
    end note

    note right of ACTIVE
        Claim is allowed only during
        startAt <= now < endAt
    end note

    note right of ENDED
        Transition is owned by the future
        expiry/reconciliation job
    end note
```

Rules:

- `DRAFT -> ACTIVE` commits only after every claim slot is created.
- `ACTIVE -> SOLD_OUT` occurs after both the physical slot count and unallocated inventory reach zero.
- `SOLD_OUT` and `ENDED` are terminal.
- `ACTIVE -> ENDED` is reserved for a future expiry job.

Claim lifecycle in the current claim-only scope:

```mermaid
stateDiagram-v2
    [*] --> ISSUED: Slot consumed + claim/outbox committed
    ISSUED --> [*]

    note right of ISSUED
        Terminal in current scope
    end note
```

A claim exists after the MySQL transaction commits. When the transaction rolls back, the slot remains available and the claim and outbox event are rolled back with it.

Durable claim-request lifecycle:

```mermaid
stateDiagram-v2
    [*] --> PENDING: claim_request commit
    PENDING --> QUEUED: Redis ZADD NX successful
    QUEUED --> PROCESSING: Worker acquires MySQL lease
    PROCESSING --> SUCCEEDED: CREATED or same-key REPLAYED
    PROCESSING --> REJECTED: terminal business result
    PROCESSING --> RETRY_WAIT: transient DB/slot contention
    RETRY_WAIT --> QUEUED: nextAttemptAt due
    PROCESSING --> RETRY_WAIT: lease expired / Recovery Watcher
    SUCCEEDED --> [*]
    REJECTED --> [*]
```

`PENDING`, `QUEUED`, and `RETRY_WAIT` are recoverable MySQL states. After `queueRecheckDelay`, the watcher may run `ZADD NX` again and move `nextAttemptAt` forward for fair batch scans. A `PROCESSING` request belongs to one worker until `leaseUntil`; completion and retry updates must match that worker's `leaseOwner`.

Outbox lifecycle:

```mermaid
stateDiagram-v2
    [*] --> PENDING: Claim transaction commits
    PENDING --> PUBLISHED: Kafka broker acknowledged
    PENDING --> PENDING: Kafka publish failed / retryCount + 1
    PENDING --> DEAD_LETTER: Max retries reached
    PUBLISHED --> [*]
    DEAD_LETTER --> [*]
```

## 6. Redis Model

| Purpose | Key | Type | TTL |
|---|---|---|---:|
| Successful idempotency replay | `claim:idem:{campaignId}:{requestId}` | JSON String | 30s |
| Worker result | `claim:request-result:{campaignId}:{requestId}` | JSON String | 30s |
| Score snapshot | `claim:score:{campaignId}:{userId}` | Integer String | 24h |
| Priority queue | `claim:priority:{campaignId}` | Sorted Set | 10m grace |
| Campaigns with pending work | `claim:priority:active-campaigns` | Set | none |

### Priority queue member

```text
member = userId + ":" + base64url(idempotencyKey)
score  = priorityScoreSnapshot
```

### Enqueue operation

One Lua script atomically executes:

1. `ZSCORE` to detect the same pending logical request.
2. `ZCARD` to enforce the per-campaign queue limit.
3. `ZADD NX` to insert once.
4. `PEXPIRE` to refresh the queue grace TTL.

| Value | Result |
|---:|---|
| `1` | Added |
| `0` | Already pending |
| `-1` | Queue full |

## 7. Campaign Flows

### 7.1 Create

```mermaid
sequenceDiagram
    participant M as Merchant
    participant API as Campaign API
    participant DB as MySQL

    M->>API: POST /v1/campaigns + Idempotency-Key
    API->>DB: Find merchant + creation key
    alt Existing
        DB-->>API: Existing campaign
        API-->>M: 200 replay
    else New
        API->>DB: INSERT DRAFT campaign
        DB-->>API: Commit
        API-->>M: 201 campaign
    end
```

### 7.2 Activate

```mermaid
sequenceDiagram
    participant M as Merchant
    participant API as Campaign API
    participant DB as MySQL

    M->>API: POST /v1/campaigns/activate
    API->>DB: Lock campaign FOR UPDATE
    API->>DB: INSERT claim slots in batches of 500
    API->>DB: UPDATE status = ACTIVE
    DB-->>API: Commit
    API-->>M: 200 ACTIVE
```

## 8. Claim Flow

```mermaid
sequenceDiagram
    participant U as User
    participant API as Claim API
    participant R as Redis
    participant DB as MySQL
    participant S as Priority Scheduler
    participant W as Claim Worker
    participant RW as Recovery Watcher

    U->>API: POST /api/v1/claims + Idempotency-Key
    API->>R: GET committed idempotency result
    alt Cache hit
        R-->>API: Existing claim
        API-->>U: 200 replay
    else Cache miss
        API->>DB: Find exact durable idempotency result
        alt Durable replay
            DB-->>API: Existing claim
            API->>R: Cache committed result 30s
            API-->>U: 200 replay
        else New operation
            API->>DB: Check campaign + user claim
            API->>R: Read score snapshot
            API->>DB: TX insert claim_request(PENDING)
            DB-->>API: Durable admission committed
            API->>DB: Lock and load eligible durable request
            API->>R: Atomic ZADD NX by score
            API->>DB: Mark QUEUED
            S->>R: ZPOPMAX highest scores
            S->>W: Dispatch admitted request
            W->>DB: Acquire PROCESSING lease
            W->>DB: Claim transaction
            DB-->>W: Commit result
            W->>DB: Persist SUCCEEDED / REJECTED
            W->>R: Cache claim + request result
            R-->>API: Request result
            API-->>U: 201 or business result
        end
    end
    opt Direct ZADD failed, Redis data was lost, or lease expired
        RW->>DB: Scan bounded recoverable request IDs
        RW->>R: Repeat idempotent ZADD NX
        RW->>DB: Mark QUEUED / advance recovery time
    end
```

### Idempotency rules

- Scope: `(campaign_id, user_id, idempotency_key)`.
- Cache hit: replay success.
- Cache miss: continue to MySQL. Redis stores committed positive results only.
- Write the cache only after the MySQL transaction commits.
- After the TTL expires, MySQL still provides the durable result.
- The same pending key produces the same deterministic `requestId`. The `claim_request` primary key permits one durable operation.
- HTTP retries and recovery may enqueue again, but `ZADD NX` and the worker lease make this idempotent.

## 9. Priority Scheduling

### Ordering

```text
Primary:   priority_score_snapshot DESC
Tie-break: Redis member DESC
Scope:     one campaign + one priority window
```

### Scheduler policy

1. The API materializes each committed request directly into the ZSET; the Recovery Watcher repairs gaps from MySQL.
2. Scan `claim:priority:active-campaigns` every 10 ms.
3. Wait for `priority_window_ms` before the first dispatch.
4. Calculate free workers.
5. Set `batchSize = min(admissionBatchSize, availableWorkers)`.
6. Execute `ZPOPMAX batchSize`.
7. Submit workers; each worker must acquire a MySQL lease.
8. Re-enqueue if the executor rejects submission.

Higher scores run first among requests visible when the scheduler selects a batch. Requests arriving in later batches and parallel transaction commits may appear in a different order.

## 10. Claim Transaction

Isolation level: `READ_COMMITTED`.

The project defines transaction boundaries with a shared `TransactionTemplate`. `ClaimTransactionServiceImpl.execute()` opens the boundary before invoking the claim logic, keeping slot deletion, claim insertion, and outbox insertion in one transaction.

```text
BEGIN
  1. SELECT claim WHERE campaign_id=? AND user_id=?
  2. Validate campaign status and time window
  3. SELECT one slot
       ORDER BY slot_id
       LIMIT 1
       FOR UPDATE SKIP LOCKED
  4. If the slot query is empty:
       - physical slot exists  -> BUSY
       - inventory exhausted   -> SOLD_OUT
  5. DELETE locked slot
  6. INSERT voucher_claim
  7. INSERT outbox_event
COMMIT
```

### Transactional boundary

The following commit or roll back together:

- Delete one claim slot.
- Insert one voucher claim.
- Insert one outbox event.

### Database race resolution

If two workers for one user reach MySQL:

1. Both can pass the initial read.
2. `UNIQUE (campaign_id, user_id)` selects one winner.
3. The losing transaction rolls back its slot deletion.
4. The worker reads the winner.
5. The same key returns replay; another key returns `ALREADY_CLAIMED`.

## 11. Contention Control

### 11.1 The shared-counter problem

The simplest design stores `remaining_quantity` in one campaign row:

```sql
UPDATE voucher_campaign
SET remaining_quantity = remaining_quantity - 1
WHERE campaign_id = :campaignId
  AND remaining_quantity > 0;
```

This keeps inventory correct, but every claim for one campaign competes for the same exclusive row lock. At 5,000 requests/s/campaign, transactions serialize at that hot row. Adding API instances or workers only increases pressure on it, along with lock waits, tail latency, timeouts, and retries.

### 11.2 Current solution: materialized claim slots

```text
One inventory unit = one voucher_claim_slot row
```

#### High-level contention design

```mermaid
flowchart LR
    REQUESTS[Concurrent claim requests] --> PRIORITY[Redis priority queue<br/>ordering and admission only]
    PRIORITY --> GATE[Bounded worker gate]

    subgraph MYSQL[MySQL correctness boundary]
        SLOTS[(Independent slot rows<br/>slot 1, slot 2, ... slot N)]
        TX[Short claim transaction<br/>lock and delete one slot]
        RESULT[(voucher_claim + outbox_event)]

        SLOTS -->|FOR UPDATE SKIP LOCKED| TX
        TX -->|atomic commit| RESULT
    end

    GATE --> TX
```

Redis orders requests and limits the work entering the database. MySQL owns inventory and grants each voucher through an independent slot row, a local transaction, and unique constraints.

Each claimable voucher is one row keyed by `(campaign_id, slot_id)`:

```sql
SELECT campaign_id, slot_id
FROM voucher_claim_slot
WHERE campaign_id = :campaignId
ORDER BY slot_id
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

How contention is reduced:

1. Worker A locks slot 1. Worker B skips it and locks slot 2 instead of waiting.
2. Transactions spread across many rows rather than serializing on one counter.
3. Each lock is held only while selecting/deleting a slot and inserting the claim/outbox.
4. All writes commit together; any failure restores the slot.
5. `UNIQUE (campaign_id, user_id)` remains the final protection against duplicate winners.

This trades additional storage and writes for parallel short transactions and strong business invariants.

#### Concurrent claim sequence

```mermaid
sequenceDiagram
    participant Q as Priority Scheduler
    participant A as Worker A
    participant DB as MySQL Slot Pool
    participant B as Worker B

    Q->>A: Dispatch request A
    Q->>B: Dispatch request B

    A->>DB: BEGIN and SELECT one slot FOR UPDATE SKIP LOCKED
    DB-->>A: Return slot 1 with row lock

    B->>DB: BEGIN and SELECT one slot FOR UPDATE SKIP LOCKED
    Note right of DB: Slot 1 is locked by Worker A
    DB-->>B: Skip slot 1 and lock slot 2

    par Worker A transaction
        A->>DB: DELETE slot 1
        A->>DB: INSERT voucher_claim A
        A->>DB: INSERT VoucherClaimed outbox A
        A->>DB: COMMIT
    and Worker B transaction
        B->>DB: DELETE slot 2
        B->>DB: INSERT voucher_claim B
        B->>DB: INSERT VoucherClaimed outbox B
        B->>DB: COMMIT
    end

    DB-->>A: Claim A committed
    DB-->>B: Claim B committed
```

If every remaining slot is temporarily locked, the worker returns retryable `BUSY`. `SOLD_OUT` is returned after an authoritative check confirms that both physical slots and unallocated inventory are exhausted.

### 11.3 Admission control before MySQL

```text
5,000 req/s ingress
        ↓
MySQL claim_request (durable admission)
        ↓ direct post-commit ZADD NX
Redis ZSET priority index
        ↓ bounded by available worker count
Claim transactions
        ↓
MySQL
```

`claim_request` stores the request durably and Redis orders it by score. Worker capacity, rather than the HTTP burst, determines inventory-write concurrency. A deterministic `requestId`, database uniqueness, and `ZADD NX` make repeated use of one idempotency key converge on the same operation.

The ZSET is a rebuildable scheduling index; `claim_request` owns the durable work and MySQL grants the voucher.

### 11.4 Redis recovery

- The API creates the asynchronous operation only after `claim_request` commits.
- If the process dies before `ZADD NX`, the durable row remains `PENDING` and discoverable.
- Repeated direct materialization collapses through the same `requestId` and `ZADD NX`.
- The Recovery Watcher rematerializes `PENDING`, `QUEUED`, and `RETRY_WAIT` rows.
- If a worker dies after pop, its `PROCESSING.leaseUntil` expires.
- If a worker dies after claim commit but before completing the request, the next attempt reads the durable claim and resolves `REPLAYED`.

Direct post-commit materialization is the low-latency path; the watcher is the recovery path. It reads durable request states from MySQL instead of relying on Redis key-expiry events.

### 11.5 Inventory stays in MySQL

A Redis `DECR` or Lua script is fast and atomic inside Redis, but the claim and outbox must still be persisted in MySQL. Redis and MySQL cannot commit atomically:

| Write order | Failure | Consequence |
|---|---|---|
| Redis first, MySQL second | MySQL fails or worker dies | Stock can be reserved while the claim is missing |
| MySQL first, Redis second | Redis timeout or failover | Claim exists while Redis still exposes stock |
| Parallel writes | One side succeeds | Complex reconciliation and compensation |

Making Redis authoritative requires reservation leases, recovery, reconciliation, and idempotent compensation. MySQL is selected because one local transaction guarantees that a slot is consumed once, a user owns one claim, and the claim/outbox coexist.

Redis holds rebuildable data: priority ordering, a 30-second positive replay cache, score snapshots, and short-lived request results.

### 11.6 When Redis inventory reservation could fit

Redis reservation becomes useful when measured throughput exceeds database scaling and the product can expose a `PENDING` state backed by reconciliation and compensation. A Lua script can create a TTL reservation, with success returned after the durable store commits. At the stated target, bounded workers and slot rows provide enough concurrency with a simpler consistency model.

### 11.7 Distributed-lock policy

Claim ownership is split by scope:

- `ZADD NX` deduplicates Redis queue membership.
- The MySQL lease assigns one durable request to a worker.
- `FOR UPDATE SKIP LOCKED` assigns one inventory slot to a transaction.
- Unique constraints settle duplicate claim races.

These narrow ownership boundaries let independent claims run in parallel and remove the need for a campaign-wide lock.

## 12. Slot Lifecycle

### Current implementation

- Campaign creation sets `unallocated_quantity = total_quantity`.
- Activation creates every slot in batches of 500.
- After all slots exist, `unallocated_quantity = 0` and status becomes `ACTIVE`.
- A successful claim deletes exactly one slot.
- Activation materializes the full inventory, so the current lifecycle ends with slot consumption.

### Large-inventory extension

Use bounded pre-allocation when inventory exceeds the current limit:

| Setting | Suggested value |
|---|---:|
| Initial slot batch | 5,000–20,000 |
| Low watermark | 20–30% of the batch |
| Refill size | 5,000–20,000 |

Refill transaction:

1. Lock campaign allocation metadata.
2. Reserve `min(refill_size, unallocated_quantity)`.
3. Decrement `unallocated_quantity`.
4. Insert the new slot-ID range.
5. Commit.

Refill coordination stays outside the claim path.

## 13. Outbox

`outbox_event` protects the allocation-to-notification boundary. Allocation writes `voucher_claim + VoucherClaimed` in one transaction, pairing every committed claim with its notification event. `claim_request` already provides admission durability; Kafka starts at the notification boundary.

Claim event:

```json
{
  "event_type": "VoucherClaimed",
  "aggregate_type": "VoucherClaim",
  "aggregate_id": "<claim_id>",
  "payload": {
    "claim_id": "<claim_id>",
    "campaign_id": "<campaign_id>",
    "user_id": "<user_id>",
    "status": "ISSUED",
    "claimed_at": "<timestamp>"
  }
}
```

Publisher flow:

```mermaid
sequenceDiagram
    participant P as Outbox Publisher
    participant DB as MySQL
    participant K as Kafka
    participant C as Notification Consumer
    participant N as Notification Service Client

    P->>DB: Read bounded PENDING event IDs
    loop Each event
        P->>DB: SELECT event FOR UPDATE
        alt Already published by another publisher
            DB-->>P: Skip
        else Still PENDING
            P->>K: Produce VoucherClaimed, key=eventId
            alt Broker acknowledged
                K-->>P: Record metadata ack
                P->>DB: status=PUBLISHED, publishedAt=now
            else Publish failed or timed out
                P->>DB: retryCount + 1
                P->>DB: DEAD_LETTER when retry budget exhausted
            end
        end
    end
    K-->>C: Deliver VoucherClaimed
    C->>N: send notification
    alt Handler successful
        C->>K: Commit consumer offset
    else Handler failed
        C-->>K: Leave record uncommitted for redelivery
    end
```

Implementation rules:

1. Poll a bounded batch through `(publish_status, created_at, event_id)`.
2. `lockById` uses a pessimistic row lock to assign each `PENDING` row to one publisher at a time.
3. Use `eventId` as the Kafka key. Delivery is at-least-once, so notification consumers deduplicate by that key.
4. `OutboxDeliveryServiceImpl` uses `TransactionTemplate` for its database boundary.

## 14. Sharding

### Baseline

Start with one database shard at 5,000 requests/s for a campaign. Redis scheduling, bounded worker admission, and independent slot rows address the first contention bottlenecks.

### Sharding triggers

Shard after load tests show any of the following:

- Aggregate committed claim writes exceed one MySQL primary.
- Data or indexes no longer meet latency targets.
- Replication lag exceeds the SLO.
- P99 transaction latency remains high after admission tuning.

### Shard key

```text
shard = hash(campaign_id) % shard_count
```

Co-locate `voucher_campaign`, `voucher_claim_slot`, `claim_request`, `voucher_claim`, and `outbox_event`. A campaign stays on one shard, preserving a local transaction and avoiding distributed transactions.

## 15. Failure Handling

| Failure | Result |
|---|---|
| Redis replay cache unavailable | Skip the fast path; MySQL decides replay/admission |
| Score snapshot missing | Return `503 BUSY`; leave the request due for recovery |
| API dies after admission commit but before Redis | Recovery materializes the durable `PENDING` request from MySQL |
| Direct Redis materialization fails | Durable request remains due; HTTP retry or Recovery Watcher repeats `ZADD NX` |
| Kafka unavailable | Claim processing continues; notification outbox remains `PENDING` |
| Redis restarts or loses a ZSET | Recovery runs `ZADD NX` from durable requests |
| Priority queue full | Durable request remains due and materialization retries |
| Worker dies before commit | Transaction rolls back and lease expiry retries |
| Worker dies after commit before request completion | Next attempt resolves the durable claim as replay |
| Redis result write fails | Claim remains durable and a retry reads MySQL |
| All slots temporarily locked | Return `BUSY` and retry later |
| Inventory exhausted | Move the campaign to `SOLD_OUT` |
| Duplicate user race | Unique constraint selects one winner |
| Outbox insert fails | Entire claim transaction rolls back |
| Kafka publish fails or times out | Event stays `PENDING` and retry count increases |
| Outbox retry budget is exhausted | Event moves to `DEAD_LETTER` |
| Kafka ack succeeds but status commit fails | Event may publish again; consumer deduplicates by `eventId` |
| Notification handler fails | Kafka redelivers from the last committed offset |
| Consumer dies after notification before offset commit | Notification Service deduplicates the redelivery |

## 16. Scaling Topology

### Stateless API

- Run many API instances behind a load balancer.
- Use the Redis `{campaignId}` hash tag to keep campaign keys in one cluster slot where atomicity is required.
- Store all durable request state in MySQL.

### Scheduler

Spring scheduling runs the recurring triggers. Each claim task is durable in `claim_request`.

| Job | Trigger | Responsibility |
|---|---|---|
| `PriorityScheduler` | `@Scheduled(fixedDelay = 10ms)` | Scan active campaigns, apply the priority window, run `ZPOPMAX`, and dispatch |
| `OutboxPublisher` | `@Scheduled(fixedDelay = 500ms)` | Poll bounded `PENDING` outbox IDs and deliver each event |
| `ClaimRequestRecoveryWatcher` | `@Scheduled(fixedDelay = 2s)` | Query due/expired requests and rematerialize them with `ZADD NX` |

A bounded `claimWorkerExecutor` runs claim work, while recovery uses indexed batch scans.

The baseline uses one scheduler-enabled instance. A production deployment can add campaign ownership partitioning or leader election. Redis atomic pop and MySQL constraints preserve correctness when multiple schedulers run, although the in-memory priority-window timestamp can make ordering and scan frequency less efficient.

### Database

- Bound worker concurrency to measured MySQL capacity.
- Add a read replica for history queries when needed.
- Keep claim, slot, request, and outbox writes on one primary/shard.

## 17. Observability

### Metrics

```text
claim_requests_total{campaign,result}
claim_replay_total{source=redis|mysql}
claim_queue_depth{campaign}
claim_queue_wait_ms
claim_transaction_ms
claim_slot_busy_total
claim_sold_out_total
claim_worker_active
claim_worker_rejected_total
redis_operation_ms{operation}
outbox_pending_total
outbox_oldest_pending_seconds
kafka_publish_latency_ms
kafka_consumer_lag
kafka_consumer_delivery_failures_total
```

### Structured logging

- `INFO`: campaign lifecycle, recovery batch size, and operational events.
- `DEBUG`: data flow by `requestId`, `campaignId`, `userId`, score, queue result, lease, attempt, transition, claim result, outbox event, and Kafka topic.
- Log only a hash or short prefix of `idempotencyKey`.
- Keep `VOUCHER_CLAIM_LOG_LEVEL=INFO` in production; 5,000 requests/s can otherwise amplify logs significantly.

### Alerts

- Queue depth continuously grows.
- Queue-wait P99 exceeds the SLO.
- Worker rejection remains above zero.
- MySQL transaction P99 rises.
- Redis error rate rises.
- Oldest pending outbox age exceeds its threshold.
- Kafka publish errors or consumer lag exceed thresholds.
- `BUSY` ratio increases while worker utilization stays low.

## 18. Load-Test Plan

### Scenario A — one hot campaign

- 5,000 requests/s.
- One campaign with varied score distributions.
- Assert `issued claims <= inventory` and priority ordering within each window.

### Scenario B — idempotency spam

- One user, one key, 1,000 retries.
- Assert one ZSET member and one durable claim.

### Scenario C — same user, different keys

- 100 concurrent keys.
- Assert one claim and `ALREADY_CLAIMED` for the rest.

### Scenario D — many campaigns

- 5,000 requests/s/campaign across several campaigns.
- Assert independent queues and slot locks.

### Scenario E — dependency failures

- Restart Redis.
- Inject transient MySQL errors.
- Stop Kafka and restart consumers.
- Terminate workers before and after commit.
- Assert retries with one key still produce exactly one claim.

## 19. Correctness Invariants

1. `issued_claim_count <= total_quantity`.
2. At most one claim exists for `(campaign_id, user_id)`.
3. A slot is deleted only in the transaction that creates exactly one claim.
4. A committed claim has its outbox event in the same transaction.
5. Redis caches success after the MySQL commit.
6. MySQL uniqueness remains effective after the cache expires.
7. `SOLD_OUT` requires both physical slots and unallocated inventory to reach zero.
8. The claim persists the same score snapshot used for enqueue.
9. Outbox status becomes `PUBLISHED` only after Kafka acknowledgement.
10. A pessimistic row lock assigns each pending outbox row to one publisher at a time; consumers handle sequential duplicate delivery by `eventId`.
11. Notification applies one business effect per `eventId` despite redelivery.
12. Every accepted operation is durable before Redis materialization.
13. Redis queues can be rebuilt from `claim_request`.
14. Only a worker holding an unexpired lease can execute the transaction.
15. One `(campaign_id, user_id, idempotency_key)` maps to one durable request.

## 20. Implementation Map

```mermaid
flowchart LR
    C[controller] --> F[facade interface]
    F --> FI[facade.impl]
    FI --> S[service interface]
    S --> SI[service.impl]
    SI --> R[repository]
    SI --> RC[redis]
    SI --> M[messaging interface]
    M --> MI[messaging.impl / Kafka]

    C --> DTO[model.request / model.response]
    FI --> DTO
    SI --> DM[model / domain / entity]

    SCH[scheduler] --> S
```
