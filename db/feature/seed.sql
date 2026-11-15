-- author_id matches the seed user "reyes" in db/auth/seed.sql
INSERT INTO announcements (id, title, body, author_id, author_name) VALUES
  ('33333333-3333-3333-3333-333333333331', 'Welcome to the new term',
   'Enrollment is open. Check the course list and enroll before slots run out.',
   '22222222-2222-2222-2222-222222222221', 'Prof. Reyes')
ON CONFLICT (id) DO NOTHING;
