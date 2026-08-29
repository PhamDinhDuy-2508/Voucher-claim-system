# Voucher Claim System

A production-oriented Spring Boot reference implementation for high-contention voucher campaigns. The system accepts durable claim requests, prioritizes users by score, and allocates a finite inventory without overselling.

## What This Project Demonstrates

- A controller → facade → service → repository architecture.
- MySQL as the source of truth for campaigns, claim requests, inventory slots, claims, and outbox events.
- Redis Sorted Sets for fast, best-effort score-based scheduling.
- Kafka for durable asynchronous notification delivery after a claim commits.
- The transactional outbox pattern for reliable event publication.
- Database-backed recovery for Redis loss, consumer restarts, and expired worker leases.
- Durable asynchronous campaign activation with a resumable MySQL cursor.
- Idempotent create and activate operations, plus natural claim idempotency by campaign and user.
- UUIDv7 campaign identifiers.

## Technology

- Java 17
- Spring Boot 3
- Spring Data JPA / Hibernate
- MySQL 8
- Redis
- Apache Kafka
- Maven

## Project Structure

    src/main/java/.../
    ├── controller/          HTTP endpoints and request logging
    ├── facade/              Use-case orchestration
    ├── service/             Service contracts
    ├── service/impl/        Business implementations
    ├── repository/          JPA repositories and native slot queries
    ├── model/               JPA entities
    ├── type/                Enums and state definitions
    ├── messaging/           Kafka producers and consumers
    ├── scheduler/           Priority dispatch, outbox, and recovery jobs
    └── configuration/       Redis, Kafka, and application configuration

## Claim Lifecycle

```mermaid
flowchart LR
    C[Client] -->|1. Submit claim| API[Claim API]
    API -->|2. Persist claim_request| DB[(MySQL)]
    API -->|3. Direct ZADD NX<br/>after commit| R[(Redis ZSET)]
    API -->|4. Return 202 QUEUED| C
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

Steps 1–2 durably admit the request. Steps 3–4 add its disposable Redis priority entry and return without another database update. Steps 5–7 dispatch by best-effort priority and atomically consume one MySQL slot. Steps 8–11 publish and handle the resulting `VoucherClaimed` notification. If direct materialization fails or Redis loses data, the Recovery Watcher rebuilds the member from MySQL.

Priority is intentionally best-effort. Requests already present in the same Redis queue are dequeued by descending score, but parallel workers and transaction timing do not guarantee that database commits occur in exactly that order.

## High-Contention Inventory

The implementation does not decrement one shared campaign counter for every claim. Activation creates one independent row per voucher:

    voucher_claim_slot(campaign_id, slot_no)

Each claim transaction selects one available row using:

    SELECT ...
    FOR UPDATE SKIP LOCKED
    LIMIT 1

The transaction deletes the selected slot, inserts the claim, and writes the success outbox event atomically. Concurrent workers therefore spread across different rows instead of contending on one hot counter. MySQL constraints prevent duplicate claims and duplicate voucher codes.

No distributed lock is required for the claim path. Database row locks and unique constraints are the correctness boundary; Redis remains an acceleration and scheduling layer.

Campaign activation follows the same durability rule. The API returns `202 ACTIVATING` after storing a `campaign_activation_job`; a scheduled worker creates slot ranges and changes the campaign to `ACTIVE` only after the final range commits.

## Identifiers

- Campaign IDs are RFC 9562 UUIDv7 strings, which are time ordered and database-index friendly.
- User IDs and merchant IDs are opaque strings.
- API validation requires identifiers to be present, but does not enforce their length or format.
- Voucher codes are globally unique in the current schema.

## Run Locally

Prerequisites: JDK 17, Maven, and Docker.

Start infrastructure:

    docker compose up -d

Run the application:

    mvn spring-boot:run

The default local configuration expects MySQL, Redis, and Kafka from [compose.yaml](compose.yaml).

At startup, the application idempotently fills `user_score` with 500 sample users beginning at `2000000000000001`. Existing rows are never overwritten. Set `USER_SCORE_SEED_ENABLED=false` to disable it or change `USER_SCORE_SEED_COUNT` for a different development dataset.

### Aiven MySQL, Redis, and Kafka

The `aiven` profile connects to MySQL, Redis/Valkey, and Kafka with TLS. Download the Kafka CA certificate, access certificate, and access key from Aiven, then save them as:

    secrets/aiven/ca.pem
    secrets/aiven/service.cert
    secrets/aiven/service.key

The `secrets` directory is ignored by Git. The Aiven Kafka bootstrap server is configured in the `aiven` profile. Start the application with:

    powershell -ExecutionPolicy Bypass -File scripts/run-aiven.ps1

The script asks separately for the MySQL and Redis passwords, then loads the Kafka certificates from the Git-ignored local directory. Redis uses the Aiven `default` user and a TLS connection to port `12560`. Temporary credential environment variables are removed when the process stops, and no password is stored in Git.

## Example Workflow

Create a campaign:

    curl -i -X POST http://localhost:8080/api/v1/campaigns \
      -H "Content-Type: application/json" \
      -H "X-Merchant-Id: 1234567890123456" \
      -H "Idempotency-Key: create-campaign-001" \
      -d "{\"name\":\"Launch campaign\",\"totalQuantity\":1000,\"startsAt\":\"2026-08-28T12:00:00Z\",\"endsAt\":\"2026-08-29T12:00:00Z\"}"

Activate it:

    curl -i -X POST http://localhost:8080/api/v1/campaigns/activate \
      -H "Content-Type: application/json" \
      -H "X-Merchant-Id: 1234567890123456" \
      -d "{\"campaignId\":\"<campaignId>\"}"

Read campaign availability for the Claim button:

    curl -i "http://localhost:8080/api/v1/campaigns/status?campaignId=<campaignId>"

Store or update a user's trusted score:

    curl -i -X PUT http://localhost:8080/api/v1/internal/score-snapshots \
      -H "Content-Type: application/json" \
      -H "X-Internal-Token: local-internal-token" \
      -d "{\"userId\":\"1234567890123456\",\"score\":800}"

Submit a claim:

    curl -i -X POST http://localhost:8080/api/v1/claims \
      -H "Content-Type: application/json" \
      -d "{\"userId\":\"1234567890123456\",\"campaignId\":\"<campaignId>\"}"

The endpoint commits the durable request, attempts Redis priority materialization, and returns `202 Accepted` without waiting for a worker. Use the returned `requestId` to read progress:

    curl -i "http://localhost:8080/api/v1/claims/status?requestId=<requestId>"

Ready-to-run request files are available in the [http](http) directory.

## Verification

Run the automated tests:

    mvn test

The suite covers identifier generation, idempotency, campaign transitions, priority queue behavior, claim correctness, recovery, and transactional outbox flows.

## Correctness Invariants

- A campaign never allocates more claims than its materialized slots.
- A user can claim at most once per campaign.
- A `(campaignId, userId)` pair represents at most one accepted claim request and claim.
- A voucher code can appear in at most one claim.
- An accepted request is recoverable from MySQL even if Redis is flushed.
- Notification publication is at-least-once; consumers and database writes are idempotent.

## Documentation

- [SOLUTION.md](SOLUTION.md) describes the architecture, data model, concurrency strategy, and failure handling.
- [API-specs.md](API-specs.md) defines the HTTP contract, examples, status codes, and retry behavior.
