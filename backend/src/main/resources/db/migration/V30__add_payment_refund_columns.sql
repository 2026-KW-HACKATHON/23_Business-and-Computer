ALTER TABLE payments
    ADD COLUMN refund_amount BIGINT,
    ADD COLUMN student_compensation_amount BIGINT,
    ADD COLUMN refunded_at TIMESTAMP WITH TIME ZONE;
