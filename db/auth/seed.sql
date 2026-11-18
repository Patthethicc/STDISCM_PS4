-- Seed users. IDs MUST be identical in every service's seed data.
-- All dev passwords are: password123
INSERT INTO users (id, username, password_hash, role, name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'alice',  '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK', 'STUDENT', 'Alice Tan'),
  ('11111111-1111-1111-1111-111111111112', 'bob',    '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK', 'STUDENT', 'Bob Cruz'),
  ('11111111-1111-1111-1111-111111111113', 'carol',  '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK', 'STUDENT', 'Carol Lim'),
  ('22222222-2222-2222-2222-222222222221', 'reyes',  '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK', 'FACULTY', 'Prof. Reyes'),
  ('22222222-2222-2222-2222-222222222222', 'santos', '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK', 'FACULTY', 'Prof. Santos')
ON CONFLICT (id) DO NOTHING;
