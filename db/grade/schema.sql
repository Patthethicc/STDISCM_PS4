-- Grade service database (grade_db). Only the grade service reads or writes it.
-- Course code and title are copied in here on purpose, so the grade service never calls
-- the course service: grades keep working while the course node is down.

-- Which faculty member teaches which course. UploadGrades checks this table, and
-- ListMyTeachingCourses reads it to fill the faculty upload page.
CREATE TABLE IF NOT EXISTS faculty_courses (
    faculty_id   UUID         NOT NULL,
    course_code  VARCHAR(20)  NOT NULL,
    course_title VARCHAR(200) NOT NULL,
    PRIMARY KEY (faculty_id, course_code)
);

CREATE TABLE IF NOT EXISTS grades (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID         NOT NULL,
    course_code      VARCHAR(20)  NOT NULL,
    course_title     VARCHAR(200) NOT NULL,
    term             VARCHAR(20)  NOT NULL,      -- e.g. 'T1 2026-2027'
    -- 0.0 to 4.0 in 0.5 steps. The service validates first; this is the last line of defence.
    grade            NUMERIC(2,1) NOT NULL
                     CHECK (grade BETWEEN 0.0 AND 4.0 AND grade * 2 = TRUNC(grade * 2)),
    uploaded_by      UUID         NOT NULL,      -- faculty user ID, taken from the token
    uploaded_by_name VARCHAR(100) NOT NULL,
    uploaded_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- One grade per student, course and term. UploadGrades upserts on this key.
    CONSTRAINT uq_grades_student_course_term UNIQUE (student_id, course_code, term)
);

-- GetMyGrades needs no extra index: the unique constraint's index starts with student_id.
