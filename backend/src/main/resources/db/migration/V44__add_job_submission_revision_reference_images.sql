ALTER TABLE job_submissions
    ADD COLUMN revision_reference_image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT job_submissions_revision_reference_image_urls_check
        CHECK (jsonb_typeof(revision_reference_image_urls) = 'array');
