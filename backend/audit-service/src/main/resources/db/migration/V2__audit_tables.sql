CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details JSONB,
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    actor_type VARCHAR(20) DEFAULT 'USER',
    outcome VARCHAR(20) DEFAULT 'SUCCESS',
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'SERVICE')),
    CONSTRAINT chk_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_user_created ON audit_logs(user_id, created_at DESC);
CREATE INDEX idx_audit_action_created ON audit_logs(action_type, created_at DESC);
CREATE INDEX idx_audit_service_created ON audit_logs(service_name, created_at DESC);
CREATE INDEX idx_audit_correlation ON audit_logs(correlation_id);

CREATE TABLE analytics_kpis_daily (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    total_users BIGINT NOT NULL DEFAULT 0,
    new_users BIGINT NOT NULL DEFAULT 0,
    total_sessions BIGINT NOT NULL DEFAULT 0,
    completed_sessions BIGINT NOT NULL DEFAULT 0,
    cancelled_sessions BIGINT NOT NULL DEFAULT 0,
    total_revenue DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_refunds DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    active_mentors BIGINT NOT NULL DEFAULT 0,
    new_reviews BIGINT NOT NULL DEFAULT 0,
    badges_awarded BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE skill_popularity_daily (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    skill_id BIGINT NOT NULL,
    session_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_skill_pop_date_skill UNIQUE (date, skill_id)
);

CREATE TABLE mentor_performance_daily (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    mentor_id BIGINT NOT NULL,
    sessions_completed BIGINT NOT NULL DEFAULT 0,
    sessions_cancelled BIGINT NOT NULL DEFAULT 0,
    avg_rating DECIMAL(3,2),
    revenue DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    CONSTRAINT uq_mentor_perf_date_mentor UNIQUE (date, mentor_id)
);
