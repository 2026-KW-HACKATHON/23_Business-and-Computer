package com.gakkum.backend.application.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ReadChatRoomRequest;
import com.gakkum.backend.application.chat.service.ChatService;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/me/chat-rooms")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyChatRooms(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMyChatRooms(authentication.getName())));
    }

    @PutMapping("/chat-rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            Authentication authentication,
            @PathVariable String roomId,
            @Valid @RequestBody ReadChatRoomRequest request) {
        chatService.markRead(request.toCommand(authentication.getName(), roomId));
        return ResponseEntity.ok(ApiResponse.success());
    }
}
