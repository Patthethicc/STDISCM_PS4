CREATE TABLE IF NOT EXISTS announcements (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    body        VARCHAR(5000) NOT NULL,
    author_id   UUID          NOT NULL,
    author_name VARCHAR(100)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_announcements_created_at ON announcements (created_at DESC);
