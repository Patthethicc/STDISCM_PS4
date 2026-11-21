-- 20 extra students for the concurrent-enrollment test (tests/e2e/ConcurrentEnrollTest.java).
-- Usernames load01..load20, password: password123 (same hash as seed.sql).
INSERT INTO users (id, username, password_hash, role, name)
SELECT ('33333333-3333-3333-3333-' || lpad(n::text, 12, '0'))::uuid,
       'load' || lpad(n::text, 2, '0'),
       '$2a$10$JUpCleHDkhpi8KFoMqZrC.WH5KFIZ6cHTgNyOAakPYpFezKD7V8vK',
       'STUDENT',
       'Load Test ' || lpad(n::text, 2, '0')
FROM generate_series(1, 20) AS n
ON CONFLICT (id) DO NOTHING;
