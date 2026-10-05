ALTER TABLE jobs
    ADD COLUMN reference_image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT jobs_reference_image_urls_check CHECK (jsonb_typeof(reference_image_urls) = 'array');
