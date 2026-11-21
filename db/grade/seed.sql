-- User IDs match db/auth/seed.sql exactly.
--   alice 11111111-1111-1111-1111-111111111111   reyes  22222222-2222-2222-2222-222222222221
--   bob   11111111-1111-1111-1111-111111111112   santos 22222222-2222-2222-2222-222222222222
--   carol 11111111-1111-1111-1111-111111111113
-- Course codes must match db/course/seed.sql (agree them with the course service owner).
-- Terms use the upload page's format, e.g. 'T1 2026-2027'.

INSERT INTO faculty_courses (faculty_id, course_code, course_title) VALUES
  ('22222222-2222-2222-2222-222222222221', 'STDISCM', 'Distributed Computing'),
  ('22222222-2222-2222-2222-222222222221', 'CSOPESY', 'Operating Systems'),
  ('22222222-2222-2222-2222-222222222222', 'CSARCH2', 'Computer Architecture 2'),
  ('22222222-2222-2222-2222-222222222222', 'CCPROG3', 'Object-Oriented Programming')
ON CONFLICT DO NOTHING;

-- Previous grades, so "view previous grades" has something to show on day one.
INSERT INTO grades (student_id, course_code, course_title, term, grade, uploaded_by, uploaded_by_name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'CCPROG3', 'Object-Oriented Programming', 'T2 2025-2026', 3.5,
   '22222222-2222-2222-2222-222222222222', 'Prof. Santos'),
  ('11111111-1111-1111-1111-111111111111', 'CSARCH2', 'Computer Architecture 2',     'T3 2025-2026', 4.0,
   '22222222-2222-2222-2222-222222222222', 'Prof. Santos'),
  ('11111111-1111-1111-1111-111111111112', 'CCPROG3', 'Object-Oriented Programming', 'T2 2025-2026', 2.5,
   '22222222-2222-2222-2222-222222222222', 'Prof. Santos'),
  ('11111111-1111-1111-1111-111111111112', 'CSARCH2', 'Computer Architecture 2',     'T3 2025-2026', 3.0,
   '22222222-2222-2222-2222-222222222222', 'Prof. Santos'),
  ('11111111-1111-1111-1111-111111111113', 'CCPROG3', 'Object-Oriented Programming', 'T2 2025-2026', 1.5,
   '22222222-2222-2222-2222-222222222222', 'Prof. Santos'),
  ('11111111-1111-1111-1111-111111111113', 'CSOPESY', 'Operating Systems',           'T3 2025-2026', 2.0,
   '22222222-2222-2222-2222-222222222221', 'Prof. Reyes')
ON CONFLICT (student_id, course_code, term) DO NOTHING;
