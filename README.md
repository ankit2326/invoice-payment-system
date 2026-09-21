# Invoice & Payment Service

A minimal invoice and payment service built with Java 21 and Spring Boot 3. Businesses create invoices for their customers, customers pay invoices via a mock payment processor, and businesses receive webhook notifications for state changes.

## Language Choice

This project uses **Java 21 with Spring Boot** instead of Rust. Justification:
- Spring Boot provides a mature ecosystem for building production-grade HTTP services with built-in support for JPA, transaction management, and scheduled tasks — all critical for this assignment's requirements.
- JPA's `@Transactional` and `@Lock(PESSIMISTIC_WRITE)` annotations make the payment concurrency model straightforward to implement and reason about.
- Flyway integration is first-class in Spring Boot, and the testing story (MockMvc, @SpringBootTest) is well-established.

## Architecture

```
┌─────────────┐       ┌──────────────┐       ┌────────────┐
│   Client     │──────▶│  App (8080)  │──────▶│ Mock PSP   │
│  (curl/etc)  │       │  Spring Boot │       │   (9090)   │
└─────────────┘       └──────┬───────┘       └────────────┘
                             │
                             ▼
                      ┌────────────┐
                      │ PostgreSQL │
                      │   (5432)   │
                      └────────────┘
```

- **App**: Handles all API requests, manages invoice state, processes payments.
- **Mock PSP**: Simulates a payment processor with configurable outcomes via card tokens.
- **PostgreSQL**: Stores all persistent data. Flyway runs migrations on startup.

## Quick Start

```bash
docker compose up --build
```

This brings up all three services. The API is available at `http://localhost:8080`.

## curl Examples

### 1. Create a Business (returns API key)

```bash
curl -s -X POST http://localhost:8080/businesses \
  -H "Content-Type: application/json" \
  -d '{"name": "Acme Corp"}' | jq
```

Save the `api_key` from the response. Use it in subsequent requests.

### 2. Create a Customer

```bash
curl -s -X POST http://localhost:8080/customers \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "email": "john@example.com"}' | jq
```

### 3. Create an Invoice and Finalize It

```bash
# Create invoice (starts in 'draft' state)
curl -s -X POST http://localhost:8080/invoices \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "<CUSTOMER_ID>",
    "dueDate": "2026-12-31",
    "lineItems": [
      {"description": "Widget A", "quantity": 2, "unitAmountCents": 1500},
      {"description": "Widget B", "quantity": 1, "unitAmountCents": 3000}
    ]
  }' | jq

# Finalize invoice (draft -> open, makes it payable)
curl -s -X POST http://localhost:8080/invoices/<INVOICE_ID>/finalize \
  -H "Authorization: Bearer <YOUR_API_KEY>" | jq
```

### 4. Pay an Invoice (Success)

```bash
curl -s -X POST http://localhost:8080/invoices/<INVOICE_ID>/pay \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-$(uuidgen)" \
  -d '{"cardToken": "tok_success"}' | jq
```

### 5. Pay an Invoice (Failure — card declined)

```bash
# Create and finalize another invoice first, then:
curl -s -X POST http://localhost:8080/invoices/<INVOICE_ID>/pay \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: pay-$(uuidgen)" \
  -d '{"cardToken": "tok_card_declined"}' | jq
```

### 6. Register a Webhook Endpoint

```bash
curl -s -X POST http://localhost:8080/webhook-endpoints \
  -H "Authorization: Bearer <YOUR_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://webhook.site/<YOUR_ID>"}' | jq
```

## Mock PSP Card Tokens

| Token | Behavior |
|-------|----------|
| `tok_success` | Returns success after ~100ms |
| `tok_insufficient_funds` | Returns failure (insufficient_funds) after ~100ms |
| `tok_card_declined` | Returns failure (card_declined) after ~100ms |
| `tok_timeout` | Sleeps 30s then returns success. App times out at 5s. |
| `tok_network_error` | Returns HTTP 500 |

## Running Tests

Tests require a running PostgreSQL and mock PSP. The easiest way:

```bash
# Start dependencies
docker compose up postgres mock-psp -d

# Run tests
./mvnw test
```

## Demo Video

> **[Video link placeholder]** — https://www.loom.com/share/0c40c2ca96aa49d78f60a8ae13f2212c .

