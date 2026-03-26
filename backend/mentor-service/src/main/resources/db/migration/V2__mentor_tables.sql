-- Mentors table
CREATE TABLE mentors (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    headline VARCHAR(200),
    bio TEXT NOT NULL,
    experience_years INTEGER NOT NULL CHECK (experience_years >= 0),
    hourly_rate DECIMAL(10,2) NOT NULL CHECK (hourly_rate > 0),
    avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_sessions INTEGER NOT NULL DEFAULT 0,
    total_reviews INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_at TIMESTAMPTZ,
    approved_by BIGINT,
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mentor_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'BANNED'))
);

CREATE INDEX idx_mentors_user_id ON mentors(user_id);
CREATE INDEX idx_mentors_status ON mentors(status);

-- Mentor skills (cross-service reference - no DB FK to skill-service)
CREATE TABLE mentor_skills (
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (mentor_id, skill_id)
);

CREATE INDEX idx_mentor_skills_skill_id ON mentor_skills(skill_id);

-- Mentor availability (weekly recurring slots)
CREATE TABLE mentor_availability (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_time_order CHECK (end_time > start_time),
    CONSTRAINT uq_mentor_day_start UNIQUE (mentor_id, day_of_week, start_time)
);

CREATE INDEX idx_availability_mentor_day ON mentor_availability(mentor_id, day_of_week, start_time, end_time);

-- Mentor unavailability (ad-hoc blocks: holidays, leave)
CREATE TABLE mentor_unavailability (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    blocked_from TIMESTAMPTZ NOT NULL,
    blocked_to TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_block_order CHECK (blocked_to > blocked_from)
);

CREATE INDEX idx_unavailability_mentor ON mentor_unavailability(mentor_id, blocked_from, blocked_to);

-- Waitlists
CREATE TABLE waitlists (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES mentors(id) ON DELETE CASCADE,
    learner_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notified_at TIMESTAMPTZ,
    last_notified_slot_start TIMESTAMPTZ,
    notification_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '7 days'),
    CONSTRAINT chk_waitlist_status CHECK (status IN ('ACTIVE', 'NOTIFIED', 'EXPIRED', 'BOOKED')),
    CONSTRAINT uq_active_waitlist UNIQUE (mentor_id, learner_id)
);

CREATE INDEX idx_waitlists_mentor_status ON waitlists(mentor_id, status);
