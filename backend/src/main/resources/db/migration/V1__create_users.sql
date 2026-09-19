CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('STUDENT', 'OWNER'))
);
