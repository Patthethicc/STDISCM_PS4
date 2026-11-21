-- Course node database.
--
-- enrolled_count is a stored counter so Enroll can reserve a seat with one
-- conditional UPDATE (no read-then-write race). The CHECK constraint is a second
-- line of defence against overbooking; the unique constraint stops a student
-- from enrolling in the same course twice even when two requests race.

CREATE TABLE IF NOT EXISTS course (
    code           VARCHAR(20)  PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    capacity       INTEGER      NOT NULL CHECK (capacity >= 0),
    enrolled_count INTEGER      NOT NULL DEFAULT 0 CHECK (enrolled_count >= 0),
    is_open        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT course_not_overbooked CHECK (enrolled_count <= capacity)
);

CREATE TABLE IF NOT EXISTS enrollment (
    id           UUID         PRIMARY KEY,
    student_id   UUID         NOT NULL,
    course_code  VARCHAR(20)  NOT NULL REFERENCES course(code) ON DELETE CASCADE,
    -- Taken from the verified JWT at enrollment time. The course node does not
    -- call the auth node, so rosters keep working while auth is down.
    student_name VARCHAR(100) NOT NULL,
    enrolled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_enrollment_student_course UNIQUE (student_id, course_code)
);

CREATE INDEX IF NOT EXISTS idx_enrollment_course_code ON enrollment (course_code);
CREATE INDEX IF NOT EXISTS idx_enrollment_student_id ON enrollment (student_id);
