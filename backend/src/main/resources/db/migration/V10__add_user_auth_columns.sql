ALTER TABLE users
    ADD COLUMN username VARCHAR(255) NOT NULL,
    ADD COLUMN is_lock BOOLEAN NOT NULL,
    ADD COLUMN social_provider_type VARCHAR(255),
    ADD CONSTRAINT users_username_key UNIQUE (username);
