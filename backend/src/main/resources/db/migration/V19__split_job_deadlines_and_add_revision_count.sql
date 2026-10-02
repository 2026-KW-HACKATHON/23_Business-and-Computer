ALTER TABLE jobs
    RENAME COLUMN deadline TO final_deadline;

ALTER TABLE jobs
    ADD COLUMN draft_deadline DATE NOT NULL,
    ADD COLUMN revision_count INT NOT NULL;
