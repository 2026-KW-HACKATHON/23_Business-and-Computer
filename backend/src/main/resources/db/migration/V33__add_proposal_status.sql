ALTER TABLE proposals
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE proposals
    ADD CONSTRAINT proposals_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'));
