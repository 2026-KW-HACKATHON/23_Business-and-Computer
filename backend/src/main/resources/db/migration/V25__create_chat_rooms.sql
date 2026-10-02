CREATE TABLE chat_rooms (
    id VARCHAR(26) PRIMARY KEY,
    job_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_rooms_job_id_key UNIQUE (job_id)
);
