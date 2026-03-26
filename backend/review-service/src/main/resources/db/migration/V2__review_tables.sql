-- reviews table
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL,
    learner_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    moderated_by BIGINT,
    moderated_at TIMESTAMPTZ,
    moderation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_session_learner UNIQUE (session_id, learner_id)
);

CREATE INDEX idx_reviews_mentor_created ON reviews(mentor_id, created_at DESC);
CREATE INDEX idx_reviews_session ON reviews(session_id);
CREATE INDEX idx_reviews_visible_mentor ON reviews(is_visible, mentor_id, created_at DESC);

-- badges table
CREATE TABLE badges (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    skill_id BIGINT NOT NULL,
    tier VARCHAR(20) NOT NULL,
    description TEXT,
    icon_url VARCHAR(500),
    required_sessions INTEGER NOT NULL CHECK (required_sessions > 0),
    CONSTRAINT uq_badge_skill_tier UNIQUE (skill_id, tier),
    CONSTRAINT chk_badge_tier CHECK (tier IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

-- user_badges table
CREATE TABLE user_badges (
    user_id BIGINT NOT NULL,
    badge_id INTEGER NOT NULL REFERENCES badges(id),
    awarded_for_skill_id BIGINT,
    earned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, badge_id)
);
