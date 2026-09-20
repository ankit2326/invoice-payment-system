CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Businesses
CREATE TABLE businesses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- API Keys (hashed, with prefix for lookup)
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    key_prefix VARCHAR(12) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix) WHERE NOT revoked;

-- Customers (scoped to a business)
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(business_id, email)
);
CREATE INDEX idx_customers_business ON customers(business_id);

-- Invoices with state machine
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    total_amount_cents BIGINT NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_invoice_status CHECK (status IN ('draft', 'open', 'paid', 'void', 'uncollectible'))
);
CREATE INDEX idx_invoices_business_status ON invoices(business_id, status);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);

-- Invoice line items (integer cents only)
CREATE TABLE invoice_line_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_amount_cents BIGINT NOT NULL CHECK (unit_amount_cents >= 0),
    total_cents BIGINT NOT NULL
);
CREATE INDEX idx_line_items_invoice ON invoice_line_items(invoice_id);

-- Payment attempts tracking each try
CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    idempotency_key VARCHAR(255),
    card_token VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    psp_reference VARCHAR(255),
    failure_code VARCHAR(100),
    amount_cents BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_status CHECK (status IN ('pending', 'succeeded', 'failed'))
);
CREATE INDEX idx_payment_attempts_invoice ON payment_attempts(invoice_id);
CREATE UNIQUE INDEX idx_payment_attempts_idempotency ON payment_attempts(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Idempotency cache for API responses
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(255) NOT NULL,
    business_id UUID NOT NULL REFERENCES businesses(id),
    request_path TEXT NOT NULL,
    request_body_hash VARCHAR(128) NOT NULL,
    response_status_code INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (idempotency_key, business_id)
);

-- Webhook endpoints registered by businesses
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    url TEXT NOT NULL,
    secret VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhook_endpoints_business ON webhook_endpoints(business_id) WHERE active;

-- Webhook events queued for delivery
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id),
    webhook_endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id),
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_webhook_status CHECK (status IN ('pending', 'delivered', 'failed'))
);
CREATE INDEX idx_webhook_events_pending ON webhook_events(next_retry_at) WHERE status = 'pending';
