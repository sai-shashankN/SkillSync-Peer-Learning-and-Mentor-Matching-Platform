-- Payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    payer_id BIGINT NOT NULL,
    payee_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    razorpay_order_id VARCHAR(255) UNIQUE,
    razorpay_payment_id VARCHAR(255) UNIQUE,
    razorpay_signature VARCHAR(500),
    captured_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    provider_receipt VARCHAR(255),
    provider_status VARCHAR(50),
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    captured_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'INITIATED', 'AUTHORIZED', 'CAPTURED',
        'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX idx_payments_session ON payments(session_id);
CREATE INDEX idx_payments_payer ON payments(payer_id, created_at DESC);
CREATE INDEX idx_payments_payee ON payments(payee_id, created_at DESC);

-- Payment refunds
CREATE TABLE payment_refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    session_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500),
    provider_refund_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    initiated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_refund_status CHECK (status IN ('INITIATED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_refunds_payment ON payment_refunds(payment_id, created_at DESC);

-- Payment webhook events (deduplication)
CREATE TABLE payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_webhook_status CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Mentor earnings
CREATE TABLE mentor_earnings (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL UNIQUE,
    total_earned DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    pending_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    available_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    locked_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_withdrawn DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Payouts
CREATE TABLE payouts (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    idempotency_key VARCHAR(100) UNIQUE,
    provider_payout_id VARCHAR(255),
    failure_reason VARCHAR(500),
    processed_by BIGINT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_payout_status CHECK (status IN ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_payouts_mentor ON payouts(mentor_id, status, requested_at DESC);
