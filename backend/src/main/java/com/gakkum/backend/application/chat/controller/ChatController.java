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
import com.gakkum.backend.application.chat.dto.ReadChatRoomRequest;
import com.gakkum.backend.application.chat.dto.SendTextMessageRequest;
import com.gakkum.backend.application.chat.dto.SendTextMessageResponse;
import com.gakkum.backend.application.chat.facade.ChatFacade;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatFacade chatFacade;

    @GetMapping("/me/chat-rooms")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyChatRooms(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(chatFacade.getMyChatRooms(authentication.getName())));
    }

    @PutMapping("/chat-rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody ReadChatRoomRequest request) {
        chatFacade.markRead(request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/chat-rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<SendTextMessageResponse>> sendTextMessage(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody SendTextMessageRequest request) {
        SendMessageResult result = chatFacade.sendTextMessage(request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(SendTextMessageResponse.from(result)));
    }
}
