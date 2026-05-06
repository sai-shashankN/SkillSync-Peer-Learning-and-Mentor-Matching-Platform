-- Seed default skills so booking/profile dropdowns are usable in a fresh database.
INSERT INTO skills (name, category_id, description, slug, is_active)
SELECT seed.name, category.id, seed.description, seed.slug, TRUE
FROM (
    VALUES
        ('Java', 'programming', 'Enterprise-grade OOP language for backend systems', 'java'),
        ('Python', 'programming', 'Versatile language for web, AI, and scripting', 'python'),
        ('React', 'programming', 'Component-based UI library for modern web apps', 'react'),
        ('Spring Boot', 'programming', 'Opinionated Java framework for microservices', 'spring-boot'),
        ('TypeScript', 'programming', 'Typed superset of JavaScript for scalable apps', 'typescript'),
        ('Node.js', 'programming', 'Server-side JavaScript runtime', 'nodejs'),
        ('Machine Learning', 'data-science', 'Building predictive models from data', 'machine-learning'),
        ('SQL & Databases', 'data-science', 'Relational data modeling and query optimization', 'sql-databases'),
        ('Data Visualization', 'data-science', 'Turning data into actionable visual insights', 'data-visualization'),
        ('UI/UX Design', 'design', 'User-centered design for digital products', 'ui-ux-design'),
        ('Figma', 'design', 'Collaborative interface design tool', 'figma'),
        ('Agile & Scrum', 'business', 'Iterative project management methodology', 'agile-scrum'),
        ('Project Management', 'business', 'Planning, tracking, and delivering team projects', 'project-management'),
        ('Communication', 'languages', 'Professional communication and presentation skills', 'communication'),
        ('Leadership', 'business', 'Team leadership and management skills', 'leadership')
) AS seed(name, category_slug, description, slug)
JOIN skill_categories category ON category.slug = seed.category_slug
ON CONFLICT DO NOTHING;
