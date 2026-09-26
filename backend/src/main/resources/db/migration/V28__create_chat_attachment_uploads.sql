CREATE TABLE chat_attachment_uploads (
    id UUID PRIMARY KEY,
    room_id VARCHAR(26) NOT NULL,
    uploader_user_id VARCHAR(26) NOT NULL,
    type VARCHAR(10) NOT NULL,
    storage_key TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chat_attachment_uploads_type_check CHECK (type IN ('IMAGE', 'FILE')),
    CONSTRAINT chat_attachment_uploads_status_check CHECK (status IN ('PENDING', 'ATTACHED')),
    CONSTRAINT chat_attachment_uploads_file_size_check CHECK (file_size > 0)
);

ALTER TABLE chat_messages ADD COLUMN attachment_upload_id UUID;

CREATE UNIQUE INDEX chat_messages_attachment_upload_id_key
    ON chat_messages (attachment_upload_id)
    WHERE attachment_upload_id IS NOT NULL;
