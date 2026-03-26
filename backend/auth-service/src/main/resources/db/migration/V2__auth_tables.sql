-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    terms_accepted_at TIMESTAMPTZ,
    privacy_policy_version VARCHAR(20),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_id UNIQUE (auth_provider, provider_id),
    CONSTRAINT chk_local_password CHECK (
        (auth_provider = 'LOCAL' AND password_hash IS NOT NULL) OR auth_provider != 'LOCAL'
    ),
    CONSTRAINT chk_oauth_provider_id CHECK (
        (auth_provider != 'LOCAL' AND provider_id IS NOT NULL) OR auth_provider = 'LOCAL'
    )
);

-- Roles table
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- Permissions table
CREATE TABLE permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Role-Permission mapping
CREATE TABLE role_permissions (
    role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- User-Role mapping
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_id VARCHAR(255),
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_expires ON refresh_tokens(user_id, expires_at);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

-- Seed roles
INSERT INTO roles (name) VALUES ('ROLE_LEARNER'), ('ROLE_MENTOR'), ('ROLE_ADMIN');

-- Seed permissions
INSERT INTO permissions (name, description) VALUES
('user:read', 'Read user profiles'),
('user:ban', 'Ban/unban users'),
('mentor:browse', 'Browse mentor listing'),
('mentor:search', 'Search mentors with filters'),
('mentor:approve', 'Approve mentor applications'),
('mentor:reject', 'Reject mentor applications'),
('mentor:ban', 'Ban mentors'),
('session:book', 'Book a session'),
('session:cancel_own', 'Cancel own session'),
('session:view_own', 'View own sessions'),
('session:accept', 'Accept session (mentor)'),
('session:reject', 'Reject session (mentor)'),
('session:complete', 'Mark session complete'),
('session:view_all', 'View all sessions (admin)'),
('session:cancel_any', 'Cancel any session (admin)'),
('group:create', 'Create learning groups'),
('group:join', 'Join learning groups'),
('group:moderate', 'Moderate groups'),
('group:delete', 'Delete groups'),
('review:submit', 'Submit reviews'),
('review:moderate', 'Moderate reviews'),
('badge:view_own', 'View own badges'),
('payment:pay', 'Make payments'),
('payment:view_own', 'View own payments'),
('payment:view_all', 'View all payments (admin)'),
('payment:refund', 'Issue refunds'),
('earnings:view', 'View mentor earnings'),
('earnings:withdraw', 'Withdraw earnings'),
('analytics:view', 'View analytics dashboard'),
('audit:view', 'View audit logs'),
('profile:edit_own', 'Edit own profile'),
('availability:manage', 'Manage mentor availability'),
('notification:view_own', 'View own notifications'),
('notification:read_own', 'Mark notifications as read');

-- Assign permissions to ROLE_LEARNER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_LEARNER' AND p.name IN (
    'user:read', 'mentor:browse', 'mentor:search', 'session:book', 'session:cancel_own',
    'session:view_own', 'group:create', 'group:join', 'review:submit', 'badge:view_own',
    'payment:pay', 'payment:view_own', 'profile:edit_own', 'notification:view_own', 'notification:read_own'
);

-- Assign permissions to ROLE_MENTOR (includes all learner permissions plus mentor-specific)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MENTOR' AND p.name IN (
    'user:read', 'mentor:browse', 'mentor:search', 'session:book', 'session:cancel_own',
    'session:view_own', 'session:accept', 'session:reject', 'session:complete',
    'group:create', 'group:join', 'review:submit', 'badge:view_own',
    'payment:pay', 'payment:view_own', 'profile:edit_own', 'availability:manage',
    'earnings:view', 'earnings:withdraw', 'notification:view_own', 'notification:read_own'
);

-- Assign ALL permissions to ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN';
