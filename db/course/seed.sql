-- Course seed data.
--
-- These courses cover every case the UI and the e2e tests exercise:
--   CS101      open with free slots  -> a normal enrollment succeeds
--   FULL101    open but full         -> Enroll returns FULL
--   CLOSED01   closed                -> Enroll returns CLOSED
--   STDISCM    open with students    -> roster / "my enrollments" data
--   STRESS01   exactly 5 free slots  -> ConcurrentEnrollTest expects 5 successes
--
-- Student IDs must match db/auth/seed.sql and db/auth/seed-loadtest.sql.
-- Grade seed data should assign reyes (22222222-...-222221) to STDISCM so the
-- upload page can fetch its roster (alice + carol).

INSERT INTO course (code, title, capacity, enrolled_count, is_open) VALUES
  ('STDISCM', 'Introduction to Distributed Systems', 30, 2, TRUE),
  ('CS101',   'Introduction to Computing',           25, 0, TRUE),
  ('FULL101', 'Full Course Demo',                    10, 10, TRUE),
  ('CLOSED01','Closed Course Demo',                  20, 3, FALSE),
  ('STRESS01','Concurrency Stress Test',              5, 0, TRUE)
ON CONFLICT (code) DO NOTHING;

-- A few enrollments so "My Enrollments" and ListRoster are non-empty on a
-- fresh database. Random IDs are fine: the (student_id, course_code) unique
-- constraint makes this seed idempotent.
INSERT INTO enrollment (id, student_id, course_code, student_name) VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'STDISCM',  'Alice Tan'),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111113', 'STDISCM',  'Carol Lim'),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'CLOSED01', 'Alice Tan'),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111112', 'CLOSED01', 'Bob Cruz'),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111113', 'CLOSED01', 'Carol Lim')
ON CONFLICT (student_id, course_code) DO NOTHING;

-- load01..load10 fill FULL101, so the demo has a genuinely full course and a
-- ten-student roster. They are not in STRESS01, which stays empty for the race test.
INSERT INTO enrollment (id, student_id, course_code, student_name)
SELECT gen_random_uuid(),
       ('33333333-3333-3333-3333-' || lpad(n::text, 12, '0'))::uuid,
       'FULL101',
       'Load Test ' || lpad(n::text, 2, '0')
FROM generate_series(1, 10) AS n
ON CONFLICT (student_id, course_code) DO NOTHING;
