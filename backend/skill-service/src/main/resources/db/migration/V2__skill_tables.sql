-- Skill categories
CREATE TABLE skill_categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_skill_categories_display_order ON skill_categories(display_order);

-- Skills
CREATE TABLE skills (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category_id INTEGER NOT NULL REFERENCES skill_categories(id),
    description TEXT,
    slug VARCHAR(120) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skills_category_active ON skills(category_id, is_active);

-- Seed default categories
INSERT INTO skill_categories (name, slug, display_order) VALUES
    ('Programming', 'programming', 1),
    ('Data Science', 'data-science', 2),
    ('Design', 'design', 3),
    ('Business', 'business', 4),
    ('Languages', 'languages', 5);
