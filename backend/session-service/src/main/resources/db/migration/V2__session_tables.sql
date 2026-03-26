-- Enable btree_gist extension for exclusion constraint
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Session booking holds (atomic slot reservation before payment)
CREATE TABLE session_booking_holds (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL,
    learner_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    quoted_amount DECIMAL(10,2) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hold_status CHECK (status IN ('ACTIVE', 'CONVERTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_hold_time_order CHECK (end_at > start_at),
    CONSTRAINT excl_mentor_time_overlap EXCLUDE USING gist (
        mentor_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status IN ('ACTIVE', 'CONVERTED'))
);

CREATE INDEX idx_holds_mentor_time ON session_booking_holds(mentor_id, start_at, end_at);
CREATE INDEX idx_holds_expires ON session_booking_holds(expires_at, status);
CREATE INDEX idx_holds_learner ON session_booking_holds(learner_id);

-- Sessions table
CREATE TABLE sessions (
    id BIGSERIAL PRIMARY KEY,
    hold_id BIGINT REFERENCES session_booking_holds(id),
    mentor_id BIGINT NOT NULL,
    learner_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    booking_reference VARCHAR(50) NOT NULL UNIQUE,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 60,
    topic VARCHAR(255) NOT NULL,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    status_reason VARCHAR(500),
    payment_deadline_at TIMESTAMPTZ NOT NULL,
    zoom_link VARCHAR(500),
    calendar_event_id VARCHAR(255),
    amount DECIMAL(10,2) NOT NULL,
    learner_timezone VARCHAR(50),
    mentor_timezone VARCHAR(50),
    accepted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_user_id BIGINT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_session_status CHECK (status IN (
        'PAYMENT_PENDING', 'PAID', 'INTEGRATION_PENDING',
        'ACCEPTED', 'REJECTED', 'COMPLETED',
        'CANCELLED', 'EXPIRED', 'PAYMENT_FAILED'
    )),
    CONSTRAINT chk_session_time_order CHECK (end_at > start_at),
    CONSTRAINT chk_duration CHECK (duration_minutes IN (30, 60, 90))
);

CREATE INDEX idx_sessions_mentor_start ON sessions(mentor_id, start_at DESC);
CREATE INDEX idx_sessions_learner_start ON sessions(learner_id, start_at DESC);
CREATE INDEX idx_sessions_status_start ON sessions(status, start_at);
CREATE INDEX idx_sessions_hold ON sessions(hold_id);

-- Session feedback
CREATE TABLE session_feedback (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES sessions(id),
    user_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    feedback_type VARCHAR(30) NOT NULL,
    submitted_by_role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_feedback_type CHECK (feedback_type IN ('MENTOR_TO_LEARNER', 'LEARNER_TO_MENTOR')),
    CONSTRAINT chk_submitted_role CHECK (submitted_by_role IN ('MENTOR', 'LEARNER')),
    CONSTRAINT uq_session_user_feedback UNIQUE (session_id, user_id)
);

CREATE INDEX idx_feedback_session ON session_feedback(session_id);
