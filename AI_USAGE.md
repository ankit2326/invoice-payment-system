# AI_USAGE.md

## Tools Used

1. **Claude Code (Claude Opus 4.6)** — Used as a coding assistant for generating boilerplate code: entity classes, JPA repository interfaces, DTO records, controller shells, and Dockerfile/docker-compose.yml scaffolding. I provided the architectural decisions and reviewed all generated code before accepting it.

2. **Claude** — Used for drafting initial versions of the Flyway migration SQL schema. I revised the schema to add CHECK constraints on status columns, partial indexes for performance, and the composite primary key on `idempotency_records`.

## Three Decisions I Made Myself

### 1. Pessimistic locking (SELECT FOR UPDATE) over optimistic concurrency for the payment path

The AI initially suggested using `@Version`-based optimistic locking for the payment flow. I chose pessimistic locking instead because with optimistic locking, two concurrent payment attempts would both call the PSP before one detects the conflict at commit time — resulting in a potential double-charge. Pessimistic locking serializes access *before* the PSP call, which is the correct behavior for payment processing. I kept `@Version` as a secondary guard but the primary mechanism is `SELECT FOR UPDATE`.

### 2. Leaving payment attempts as "pending" on PSP timeout instead of marking them "failed"

The AI suggested marking timed-out payment attempts as "failed" for simplicity. I chose "pending" because we genuinely don't know the outcome — the PSP might have charged the customer successfully after our timeout. Marking it "failed" would be inaccurate and could lead to a second charge attempt on what was actually a successful first charge. "Pending" correctly represents the unknown state and signals to the caller that reconciliation is needed.

### 3. Using the client's idempotency key as the PSP idempotency key

The AI suggested using the `payment_attempt.id` as the PSP idempotency key. I chose to forward the client's `Idempotency-Key` instead because of the crash scenario: if the PSP succeeds but we crash before committing, the payment_attempt ID is lost (transaction rolled back). On retry, we'd generate a new ID, and the PSP would treat it as a new charge — double-charging the customer. By using the client's idempotency key (which survives crashes since the client holds it), the PSP recognizes the retry and returns the cached result.

## One Thing the AI Got Wrong

The AI-generated `RestTemplateBuilder` code used `.connectTimeout(Duration)` and `.readTimeout(Duration)` methods, which were renamed/removed in Spring Boot 3.3.x. The build failed with "cannot find symbol" errors. I corrected this by switching to `SimpleClientHttpRequestFactory` with `.setConnectTimeout(int)` and `.setReadTimeout(int)` methods, which is the correct API for this Spring Boot version. This was caught immediately by the compiler and fixed before any runtime issues.
