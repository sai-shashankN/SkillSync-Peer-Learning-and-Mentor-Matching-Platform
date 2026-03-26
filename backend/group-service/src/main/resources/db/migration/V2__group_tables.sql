-- groups table
CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    created_by BIGINT NOT NULL,
    max_members INTEGER NOT NULL DEFAULT 50 CHECK (max_members > 1),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_groups_slug ON groups(slug);
CREATE INDEX idx_groups_active ON groups(is_active, created_at DESC);

-- group_skills (skill tags)
CREATE TABLE group_skills (
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, skill_id)
);

-- group_members
CREATE TABLE group_members (
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT chk_member_role CHECK (role IN ('CREATOR', 'MODERATOR', 'MEMBER'))
);

CREATE INDEX idx_group_members_user ON group_members(user_id, joined_at DESC);

-- group_messages
CREATE TABLE group_messages (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_group_created ON group_messages(group_id, created_at DESC);
