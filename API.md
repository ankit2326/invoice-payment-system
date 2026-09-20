# API Documentation

Base URL: `http://localhost:8080`

All authenticated endpoints require: `Authorization: Bearer <api_key>`

## Error Format

All errors follow a consistent structure:

```json
{
  "status": 409,
  "error": "conflict",
  "message": "Invoice cannot be paid. Current state: paid. Only invoices in 'open' state can be paid.",
  "timestamp": "2026-09-18T10:30:00Z"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | integer | HTTP status code |
| `error` | string | Machine-readable error type |
| `message` | string | Human-readable description |
| `timestamp` | string (ISO 8601) | When the error occurred |

---

## Businesses (Public — No Auth Required)

### POST /businesses

Create a business and its first API key.

**Request:**
```json
{
  "name": "Acme Corp"
}
```

**Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Acme Corp",
  "api_key": "dodo_abc123...",
  "created_at": "2026-09-18T10:00:00Z"
}
```

> The `api_key` is shown **once** at creation. Store it securely.

---

## API Keys (Authenticated)

### POST /api-keys

Generate an additional API key for the authenticated business.

**Response (201):**
```json
{
  "id": "uuid",
  "api_key": "dodo_xyz789...",
  "prefix": "dodo_xyz789..",
  "created_at": "2026-09-18T10:00:00Z"
}
```

### DELETE /api-keys/{keyId}

Revoke an API key. Takes effect immediately.

**Response:** `204 No Content`

---

## Customers (Authenticated)

### POST /customers

**Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "businessId": "uuid",
  "name": "John Doe",
  "email": "john@example.com",
  "createdAt": "2026-09-18T10:00:00Z"
}
```

**Errors:**
- `409 Conflict` — Customer with that email already exists for this business.

### GET /customers/{id}

**Response (200):** Customer object.

**Errors:**
- `404 Not Found` — Customer not found or belongs to a different business.

### GET /customers

List all customers for the authenticated business.

**Response (200):** Array of customer objects.

---

## Invoices (Authenticated)

### POST /invoices

Create an invoice in `draft` state. The server computes the total from line items.

**Request:**
```json
{
  "customerId": "uuid",
  "dueDate": "2026-12-31",
  "lineItems": [
    {
      "description": "Widget A",
      "quantity": 2,
      "unitAmountCents": 1500
    },
    {
      "description": "Widget B",
      "quantity": 1,
      "unitAmountCents": 3000
    }
  ]
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "business_id": "uuid",
  "customer_id": "uuid",
  "status": "draft",
  "total_amount_cents": 6000,
  "due_date": "2026-12-31",
  "created_at": "2026-09-18T10:00:00Z",
  "updated_at": "2026-09-18T10:00:00Z",
  "line_items": [
    {
      "id": "uuid",
      "description": "Widget A",
      "quantity": 2,
      "unit_amount_cents": 1500,
      "total_cents": 3000
    },
    {
      "id": "uuid",
      "description": "Widget B",
      "quantity": 1,
      "unit_amount_cents": 3000,
      "total_cents": 3000
    }
  ]
}
```

**Errors:**
- `404 Not Found` — Customer not found.
- `400 Bad Request` — Missing or invalid fields.

### GET /invoices/{id}

**Response (200):** Invoice object with line items.

### GET /invoices?status={status}

List invoices, optionally filtered by status.

**Query Parameters:**
- `status` (optional): `draft`, `open`, `paid`, `void`, `uncollectible`

**Response (200):** Array of invoice objects.

### POST /invoices/{id}/finalize

Transition: `draft` → `open`. Makes the invoice payable.

**Response (200):** Updated invoice object.

**Errors:**
- `409 Conflict` — Invalid state transition.

### POST /invoices/{id}/void

Transition: `draft` → `void` or `open` → `void`.

**Response (200):** Updated invoice object.

**Errors:**
- `409 Conflict` — Invoice is in a terminal state or transition is invalid.

### POST /invoices/{id}/mark-uncollectible

Transition: `open` → `uncollectible`.

**Response (200):** Updated invoice object.

---

## Payments (Authenticated)

### POST /invoices/{id}/pay

Attempt to pay an invoice. Requires `Idempotency-Key` header.

**Headers:**
- `Idempotency-Key` (required): A unique string for this payment attempt. Reusing the same key with the same body returns the cached response. Reusing with a different body returns 422.

**Request:**
```json
{
  "cardToken": "tok_success"
}
```

**Card Tokens:**
| Token | Behavior |
|-------|----------|
| `tok_success` | Payment succeeds |
| `tok_insufficient_funds` | Payment fails: insufficient funds |
| `tok_card_declined` | Payment fails: card declined |
| `tok_timeout` | PSP times out (5s), payment left as pending |
| `tok_network_error` | PSP returns 500, payment left as pending |

**Response (200):**
```json
{
  "payment_attempt_id": "uuid",
  "invoice_id": "uuid",
  "status": "succeeded",
  "amount_cents": 6000,
  "card_token": "tok_success",
  "psp_reference": "psp-uuid",
  "failure_code": "",
  "invoice_status": "paid"
}
```

**Errors:**
- `400 Bad Request` — Missing Idempotency-Key header.
- `404 Not Found` — Invoice not found.
- `409 Conflict` — Invoice is not in `open` state, or has a pending payment attempt.
- `422 Unprocessable Entity` — Idempotency key reused with different request body.

---

## Webhook Endpoints (Authenticated)

### POST /webhook-endpoints

Register a URL to receive webhook events.

**Request:**
```json
{
  "url": "https://example.com/webhooks"
}
```

**Response (201):**
```json
{
  "id": "uuid",
  "url": "https://example.com/webhooks",
  "secret": "whsec_abc123...",
  "active": true,
  "created_at": "2026-09-18T10:00:00Z"
}
```

> The `secret` is shown **once**. Use it to verify webhook signatures.

### GET /webhook-endpoints

List all webhook endpoints for the authenticated business.

---

## Webhook Events

Events are delivered as POST requests to registered endpoints.

### Event Types

| Event | Trigger |
|-------|---------|
| `invoice.created` | Invoice created |
| `invoice.paid` | Invoice payment succeeded |
| `invoice.payment_failed` | Payment attempt failed |

### Webhook Payload

```json
{
  "event_type": "invoice.paid",
  "data": {
    "invoice_id": "uuid",
    "business_id": "uuid",
    "customer_id": "uuid",
    "status": "paid",
    "total_amount_cents": 6000,
    "due_date": "2026-12-31"
  }
}
```

### Signature Verification

Each webhook request includes:

```
X-Webhook-Signature: t=1695000000,v1=<hex_hmac_sha256>
X-Webhook-Event-Type: invoice.paid
X-Webhook-Event-Id: <uuid>
```

To verify:
1. Extract `t` (timestamp) and `v1` (signature) from the header.
2. Compute: `HMAC-SHA256(secret, "{t}.{raw_body}")`
3. Compare the computed hex digest with `v1`.
4. Reject if the timestamp is more than 5 minutes old (replay protection).

---

## Health Check

### GET /health

**Response (200):**
```json
{
  "status": "ok"
}
```
