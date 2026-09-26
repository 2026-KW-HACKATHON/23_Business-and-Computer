ALTER TABLE chat_messages ADD COLUMN client_message_id UUID;

CREATE UNIQUE INDEX chat_messages_room_sender_client_message_id_key
    ON chat_messages (room_id, sender_user_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
