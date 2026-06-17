-- Seed data (migrated from the former data.sql). IDs are auto-generated starting at 1,
-- so the literal foreign keys below line up with insertion order.

-- Users
INSERT INTO users (username, email, password, created_at) VALUES
    ('alice',   'alice@example.com',   '$2a$12$npKGfBzbKIrxqWZTCF/WN.zGQdrxf3VMBHfm3IJKjIHsaEiK42iqe', NOW()),
    ('bob',     'bob@example.com',     '$2a$12$npKGfBzbKIrxqWZTCF/WN.zGQdrxf3VMBHfm3IJKjIHsaEiK42iqe', NOW()),
    ('charlie', 'charlie@example.com', '$2a$12$npKGfBzbKIrxqWZTCF/WN.zGQdrxf3VMBHfm3IJKjIHsaEiK42iqe', NOW());

-- Projects (owner_id references users.id)
INSERT INTO projects (name, description, owner_id, created_at) VALUES
    ('Website Redesign', 'Redesign the company marketing site', 1, NOW()),
    ('Mobile App',       'Build the iOS and Android client',    1, NOW()),
    ('Data Pipeline',    'ETL pipeline for analytics',          2, NOW());

-- Tasks
INSERT INTO tasks (title, description, status, priority, due_date, project_id, assignee_id, created_at) VALUES
    ('Set up repo',         'Init git and CI',               'COMPLETED',   'HIGH',   '2026-01-10', 1, 1, NOW()),
    ('Design mockups',      'Figma wireframes for homepage', 'IN_PROGRESS', 'MEDIUM', '2026-06-20', 1, 2, NOW()),
    ('Write landing copy',  'Copywriting for hero section',  'TODO',        'LOW',    '2026-07-01', 1, 3, NOW()),
    ('Auth screen',         'Login and signup flows',        'TODO',        'HIGH',   '2026-06-30', 2, 2, NOW()),
    ('Push notifications',  'FCM integration',               'TODO',        'MEDIUM', '2026-07-15', 2, 1, NOW()),
    ('Define schema',       'ERD and table definitions',     'COMPLETED',   'HIGH',   '2026-05-01', 3, 3, NOW()),
    ('Build ingestion job', 'Kafka consumer writing to PG',  'IN_PROGRESS', 'HIGH',   '2026-06-25', 3, 2, NOW());
