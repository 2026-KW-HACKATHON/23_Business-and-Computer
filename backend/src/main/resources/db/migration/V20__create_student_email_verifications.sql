CREATE TABLE student_email_verifications (
    user_id VARCHAR(26) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255),
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    code_expires_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INTEGER NOT NULL DEFAULT 0
);
