package com.gakkum.backend.domain.chat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;

public interface ChatAttachmentUploadRepository extends JpaRepository<ChatAttachmentUpload, UUID> {
}
