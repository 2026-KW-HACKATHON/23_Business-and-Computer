package com.gakkum.backend.application.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.application.chat.dto.ChatMessageListResponse;
import com.gakkum.backend.application.chat.dto.PrepareAttachmentUploadRequest;
import com.gakkum.backend.application.chat.dto.PrepareAttachmentUploadResponse;
import com.gakkum.backend.application.chat.dto.ReadChatRoomRequest;
import com.gakkum.backend.application.chat.dto.SendAttachmentMessageRequest;
import com.gakkum.backend.application.chat.dto.SendAttachmentMessageResponse;
import com.gakkum.backend.application.chat.dto.SendTextMessageRequest;
import com.gakkum.backend.application.chat.dto.SendTextMessageResponse;
import com.gakkum.backend.application.chat.facade.ChatFacade;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendAttachmentMessageResult;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatFacade chatFacade;

    /**
     * 내 채팅방 목록 조회 API
     */
    @GetMapping("/me/chat-rooms")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyChatRooms(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(chatFacade.getMyChatRooms(authentication.getName())));
    }

    /** 채팅방 입장 정보 조회 API */
    @GetMapping("/chat-rooms/{roomId}")
    public ResponseEntity<ApiResponse<Room>> getChatRoom(
            Authentication authentication, @PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(chatFacade.getChatRoom(authentication.getName(), roomId)));
    }

    /** 채팅방 대화 내역 조회 API */
    @GetMapping("/chat-rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageListResponse>> getMessages(
            Authentication authentication, @PathVariable String roomId) {
        return ResponseEntity.ok(ApiResponse.success(chatFacade.getMessages(authentication.getName(), roomId)));
    }

    /** 채팅 메시지 단건 조회 API. 만료된 첨부 열람 URL을 다시 받을 때 사용한다. */
    @GetMapping("/chat-rooms/{roomId}/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageListResponse.Message>> getMessage(
            Authentication authentication, @PathVariable String roomId, @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(
                chatFacade.getMessage(authentication.getName(), roomId, messageId)));
    }

    /** 채팅방 메시지 읽음 처리 API */
    @PutMapping("/chat-rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody ReadChatRoomRequest request) {
        chatFacade.markRead(request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 텍스트 메시지 전송 API */
    @PostMapping("/chat-rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<SendTextMessageResponse>> sendTextMessage(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody SendTextMessageRequest request) {
        SendMessageResult result = chatFacade.sendTextMessage(request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(SendTextMessageResponse.from(result)));
    }

    /** 첨부 파일 업로드 준비 API(PresignedURL 반환) */
    @PostMapping("/chat-rooms/{roomId}/attachments/uploads")
    public ResponseEntity<ApiResponse<PrepareAttachmentUploadResponse>> prepareAttachmentUpload(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody PrepareAttachmentUploadRequest request) {
        PrepareAttachmentUploadResponse response = PrepareAttachmentUploadResponse.from(
                chatFacade.prepareAttachmentUpload(request.toCommand(authentication.getName(), roomId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /** 사진·파일 메시지 전송 API */
    @PostMapping("/chat-rooms/{roomId}/messages/attachments")
    public ResponseEntity<ApiResponse<SendAttachmentMessageResponse>> sendAttachmentMessage(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody SendAttachmentMessageRequest request) {
        SendAttachmentMessageResult result = chatFacade.sendAttachmentMessage(
                request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(SendAttachmentMessageResponse.from(result)));
    }
}
