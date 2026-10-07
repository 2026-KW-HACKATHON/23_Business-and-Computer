ALTER TABLE job_submissions
    ADD COLUMN file_sizes JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT job_submissions_file_sizes_check
        CHECK (jsonb_typeof(file_sizes) = 'object');
