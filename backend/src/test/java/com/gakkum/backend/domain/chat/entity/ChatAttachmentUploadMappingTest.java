package com.gakkum.backend.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatAttachmentUploadMappingTest {

    private static final String ROOM_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    @Test
    @DisplayName("첨부 업로드와 메시지의 업로드 ID는 외래 키 없이 매핑된다")
    void mapsUploadAndMessageUploadIdWithoutForeignKey() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();

        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ChatAttachmentUpload.class)
                    .addAnnotatedClass(ChatMessage.class)
                    .buildMetadata();
            Table uploads = metadata.getEntityBinding(ChatAttachmentUpload.class.getName()).getTable();

            assertThat(uploads.getName()).isEqualTo("chat_attachment_uploads");
            assertThat(uploads.getColumn(new Column("id")).getSqlType(metadata)).isEqualTo("uuid");
            assertThat(uploads.getColumn(new Column("room_id")).getLength()).isEqualTo(26L);
            assertThat(uploads.getColumn(new Column("uploader_user_id")).getLength()).isEqualTo(26L);
            assertThat(uploads.getColumn(new Column("type")).getLength()).isEqualTo(10L);
            assertThat(uploads.getColumn(new Column("storage_key")).getSqlType(metadata)).isEqualTo("TEXT");
            assertThat(uploads.getColumn(new Column("file_name")).getLength()).isEqualTo(255L);
            assertThat(uploads.getColumn(new Column("content_type")).getLength()).isEqualTo(100L);
            assertThat(uploads.getColumn(new Column("status")).getLength()).isEqualTo(10L);
            assertThat(uploads.getColumns()).allSatisfy(column -> assertThat(column.isNullable()).isFalse());
            assertThat(uploads.getForeignKeyCollection()).isEmpty();

            Table messages = metadata.getEntityBinding(ChatMessage.class.getName()).getTable();
            assertThat(messages.getColumn(new Column("attachment_upload_id")).isNullable()).isTrue();
            assertThat(messages.getColumn(new Column("attachment_upload_id")).getSqlType(metadata))
                    .isEqualTo("uuid");
            assertThat(messages.getForeignKeyCollection()).isEmpty();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    @DisplayName("업로드 생성 시 UUID와 원래 파일명이 빠진 저장소 키를 부여하고 대기 상태로 시작한다")
    void createsPendingUploadWithStorageKey() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 27, 15, 0);

        ChatAttachmentUpload upload = ChatAttachmentUpload.create(
                ROOM_ID, USER_ID, ChatMessageType.IMAGE, "시안 최종.PNG", "image/png", 482133L, expiresAt);

        assertThat(upload.getId()).isNotNull();
        assertThat(upload.getStorageKey()).isEqualTo("chat/" + ROOM_ID + "/" + upload.getId() + ".png");
        assertThat(upload.getFileName()).isEqualTo("시안 최종.PNG");
        assertThat(upload.getStatus()).isEqualTo(ChatAttachmentUploadStatus.PENDING);
        assertThat(upload.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("첨부 메시지는 업로드의 키, 파일명, ID를 저장하고 본문은 비워 둔다")
    void createsAttachmentMessageFromUpload() {
        ChatAttachmentUpload upload = ChatAttachmentUpload.create(
                ROOM_ID, USER_ID, ChatMessageType.FILE, "견적서.pdf", "application/pdf", 1024L,
                LocalDateTime.of(2026, 9, 27, 15, 0));
        UUID clientMessageId = UUID.randomUUID();

        ChatMessage message = ChatMessage.createAttachment(USER_ID, clientMessageId, upload);

        assertThat(message.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(message.getSenderUserId()).isEqualTo(USER_ID);
        assertThat(message.getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(message.getType()).isEqualTo(ChatMessageType.FILE);
        assertThat(message.getContent()).isNull();
        assertThat(message.getAttachmentKey()).isEqualTo(upload.getStorageKey());
        assertThat(message.getAttachmentName()).isEqualTo("견적서.pdf");
        assertThat(message.getAttachmentUploadId()).isEqualTo(upload.getId());
    }
}
