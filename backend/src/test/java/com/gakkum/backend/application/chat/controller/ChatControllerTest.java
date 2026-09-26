package com.gakkum.backend.application.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.LastMessage;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.application.chat.service.ChatService;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

class ChatControllerTest {

    private final ChatService service = mock(ChatService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("KAKAO_123", null);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("채팅방 목록은 개수와 상대방, 의뢰, 최근 메시지 정보를 반환한다")
    void returnsChatRooms() throws Exception {
        when(service.getMyChatRooms("KAKAO_123")).thenReturn(ChatRoomListResponse.of(List.of(
                Room.of("01K58M6PJV8VAJMXHBHJ2PNB5C", 11L, "의뢰 제목", "학생 이름", "student.png",
                        LastMessage.of(ChatMessageType.TEXT, "안녕하세요", LocalDateTime.of(2026, 9, 26, 12, 30)),
                        3L))));

        mockMvc.perform(get("/me/chat-rooms").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.rooms[0].roomId").value("01K58M6PJV8VAJMXHBHJ2PNB5C"))
                .andExpect(jsonPath("$.data.rooms[0].jobTitle").value("의뢰 제목"))
                .andExpect(jsonPath("$.data.rooms[0].counterpartName").value("학생 이름"))
                .andExpect(jsonPath("$.data.rooms[0].counterpartProfileImageUrl").value("student.png"))
                .andExpect(jsonPath("$.data.rooms[0].lastMessage.preview").value("안녕하세요"))
                .andExpect(jsonPath("$.data.rooms[0].lastMessage.createdAt").value("2026-09-26T12:30:00"))
                .andExpect(jsonPath("$.data.rooms[0].unreadCount").value(3));
    }

    @Test
    @DisplayName("읽음 갱신은 확인한 메시지 ID를 서비스에 전달한다")
    void marksRead() throws Exception {
        mockMvc.perform(put("/chat-rooms/room-1/read")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<MarkReadCommand> command = ArgumentCaptor.forClass(MarkReadCommand.class);
        verify(service).markRead(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(command.getValue().getLastReadMessageId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("유효하지 않은 읽음 메시지 ID는 거부한다")
    void rejectsInvalidReadId() throws Exception {
        mockMvc.perform(put("/chat-rooms/room-1/read")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("참여자가 아닌 사용자의 읽음 요청은 403을 반환한다")
    void rejectsNonParticipant() throws Exception {
        doThrow(new BusinessException(ErrorCode.CHAT_FORBIDDEN))
                .when(service).markRead(any(MarkReadCommand.class));

        mockMvc.perform(put("/chat-rooms/room-1/read")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_403"));
    }
}
