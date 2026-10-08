package com.gakkum.backend.application.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.ChatMessageListResponse;
import com.gakkum.backend.application.chat.dto.DeadlineType;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.LastMessage;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.application.chat.facade.ChatFacade;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.PrepareAttachmentUploadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendAttachmentMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.MessageResult;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendAttachmentMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.PrepareAttachmentUploadResult;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendTextMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import org.springframework.test.util.ReflectionTestUtils;

class ChatControllerTest {

    private final ChatFacade service = mock(ChatFacade.class);
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
        when(service.getMyChatRooms("KAKAO_123")).thenReturn(ChatRoomListResponse.of(List.of(roomResponse())));

        mockMvc.perform(get("/me/chat-rooms").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.rooms[0].roomId").value("01K58M6PJV8VAJMXHBHJ2PNB5C"))
                .andExpect(jsonPath("$.data.rooms[0].jobTitle").value("의뢰 제목"))
                .andExpect(jsonPath("$.data.rooms[0].jobStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.rooms[0].counterpartName").value("학생 이름"))
                .andExpect(jsonPath("$.data.rooms[0].counterpartProfileImageUrl").value("student.png"))
                .andExpect(jsonPath("$.data.rooms[0].deadlineType").value("DRAFT"))
                .andExpect(jsonPath("$.data.rooms[0].deadlineDate").value("2026-10-10"))
                .andExpect(jsonPath("$.data.rooms[0].submissionReviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.rooms[0].submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.rooms[0].revisionNumber").value(2))
                .andExpect(jsonPath("$.data.rooms[0].proposalId").value(31))
                .andExpect(jsonPath("$.data.rooms[0].budget").value(300000))
                .andExpect(jsonPath("$.data.rooms[0].revisionCount").value(2))
                .andExpect(jsonPath("$.data.rooms[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.rooms[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.rooms[0].applicationSummary").value("한 줄 요약"))
                .andExpect(jsonPath("$.data.rooms[0].applicationWorkPlan").value("작업계획서"))
                .andExpect(jsonPath("$.data.rooms[0].applicationDeliveryMethod").value("결과물 전달 방법"))
                .andExpect(jsonPath("$.data.rooms[0].applicationContent").doesNotExist())
                .andExpect(jsonPath("$.data.rooms[0].lastMessage.preview").value("안녕하세요"))
                .andExpect(jsonPath("$.data.rooms[0].lastMessage.createdAt").value("2026-09-26T21:30:00+09:00"))
                .andExpect(jsonPath("$.data.rooms[0].unreadCount").value(3));
    }

    @Test
    @DisplayName("채팅방 단건 조회는 목록과 같은 방 정보를 반환한다")
    void returnsChatRoom() throws Exception {
        when(service.getChatRoom("KAKAO_123", "01K58M6PJV8VAJMXHBHJ2PNB5C"))
                .thenReturn(roomResponse());

        mockMvc.perform(get("/chat-rooms/01K58M6PJV8VAJMXHBHJ2PNB5C").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("의뢰 제목"))
                .andExpect(jsonPath("$.data.jobStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.deadlineType").value("DRAFT"))
                .andExpect(jsonPath("$.data.submissionReviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.revisionNumber").value(2))
                .andExpect(jsonPath("$.data.proposalId").value(31))
                .andExpect(jsonPath("$.data.applicationSummary").value("한 줄 요약"))
                .andExpect(jsonPath("$.data.applicationWorkPlan").value("작업계획서"))
                .andExpect(jsonPath("$.data.applicationDeliveryMethod").value("결과물 전달 방법"))
                .andExpect(jsonPath("$.data.applicationContent").doesNotExist());
    }

    @Test
    @DisplayName("제출물이 없는 일반 의뢰는 목록과 단건 모두 제출물·제안 필드를 명시적 null로 반환한다")
    void returnsExplicitNullWithoutSubmissionAndProposal() throws Exception {
        Room room = roomResponse(JobStatus.MATCHED, null, null);
        when(service.getMyChatRooms("KAKAO_123")).thenReturn(ChatRoomListResponse.of(List.of(room)));
        when(service.getChatRoom("KAKAO_123", "01K58M6PJV8VAJMXHBHJ2PNB5C")).thenReturn(room);

        mockMvc.perform(get("/me/chat-rooms").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].submissionReviewStatus").hasJsonPath())
                .andExpect(jsonPath("$.data.rooms[0].submissionReviewStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].submissionType").hasJsonPath())
                .andExpect(jsonPath("$.data.rooms[0].submissionType").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].revisionNumber").hasJsonPath())
                .andExpect(jsonPath("$.data.rooms[0].revisionNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].proposalId").hasJsonPath())
                .andExpect(jsonPath("$.data.rooms[0].proposalId").value(nullValue()));
        mockMvc.perform(get("/chat-rooms/01K58M6PJV8VAJMXHBHJ2PNB5C").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionType").hasJsonPath())
                .andExpect(jsonPath("$.data.submissionType").value(nullValue()))
                .andExpect(jsonPath("$.data.revisionNumber").hasJsonPath())
                .andExpect(jsonPath("$.data.revisionNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.proposalId").hasJsonPath())
                .andExpect(jsonPath("$.data.proposalId").value(nullValue()));
    }

    @Test
    @DisplayName("초안 제출물은 수정 번호 0을 숫자로 반환한다")
    void returnsDraftRevisionNumberZero() throws Exception {
        when(service.getChatRoom("KAKAO_123", "01K58M6PJV8VAJMXHBHJ2PNB5C"))
                .thenReturn(roomResponse(JobStatus.MATCHED, submission(JobSubmissionType.DRAFT, 0), null));

        mockMvc.perform(get("/chat-rooms/01K58M6PJV8VAJMXHBHJ2PNB5C").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.revisionNumber").value(0))
                .andExpect(jsonPath("$.data.proposalId").value(nullValue()));
    }

    @Test
    @DisplayName("채팅방 목록과 단건은 완료와 취소를 서로 다른 작업 상태 문자열로 반환한다")
    void returnsClosedAndCancelledJobStatus() throws Exception {
        when(service.getMyChatRooms("KAKAO_123")).thenReturn(ChatRoomListResponse.of(
                List.of(roomResponse(JobStatus.CLOSED), roomResponse(JobStatus.CANCELLED))));
        when(service.getChatRoom("KAKAO_123", "01K58M6PJV8VAJMXHBHJ2PNB5C"))
                .thenReturn(roomResponse(JobStatus.CANCELLED));

        mockMvc.perform(get("/me/chat-rooms").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].jobStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.rooms[1].jobStatus").value("CANCELLED"));
        mockMvc.perform(get("/chat-rooms/01K58M6PJV8VAJMXHBHJ2PNB5C").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobStatus").value("CANCELLED"));
    }

    @Test
    @DisplayName("메시지 내역은 조회자 식별자와 메시지 배열을 반환한다")
    void returnsMessages() throws Exception {
        when(service.getMessages("KAKAO_123", "room-1"))
                .thenReturn(ChatMessageListResponse.from(
                        List.of(MessageResult.text(savedMessage(UUID.randomUUID()))), "viewer-1"));

        mockMvc.perform(get("/chat-rooms/room-1/messages").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerUserId").value("viewer-1"))
                .andExpect(jsonPath("$.data.messages[0].id").value(17))
                .andExpect(jsonPath("$.data.messages[0].senderUserId").value("sender-1"))
                .andExpect(jsonPath("$.data.messages[0].content").value("안녕하세요"));
    }

    @Test
    @DisplayName("메시지가 없는 대화도 조회자 식별자와 빈 메시지 배열을 반환한다")
    void returnsViewerUserIdForEmptyConversation() throws Exception {
        when(service.getMessages("KAKAO_123", "room-1"))
                .thenReturn(ChatMessageListResponse.from(List.of(), "viewer-1"));

        mockMvc.perform(get("/chat-rooms/room-1/messages").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerUserId").value("viewer-1"))
                .andExpect(jsonPath("$.data.messages").isArray())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    @DisplayName("메시지 내역의 TEXT 메시지는 열람 URL 만료 시각을 null로 반환한다")
    void returnsNullContentExpiresAtForTextMessage() throws Exception {
        when(service.getMessages("KAKAO_123", "room-1"))
                .thenReturn(ChatMessageListResponse.from(
                        List.of(MessageResult.text(savedMessage(UUID.randomUUID()))), "viewer-1"));

        mockMvc.perform(get("/chat-rooms/room-1/messages").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].type").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[0].contentExpiresAt").hasJsonPath())
                .andExpect(jsonPath("$.data.messages[0].contentExpiresAt").value(nullValue()))
                .andExpect(jsonPath("$.data.messages[0].attachmentSize").hasJsonPath())
                .andExpect(jsonPath("$.data.messages[0].attachmentSize").value(nullValue()));
    }

    @Test
    @DisplayName("비참여자의 채팅방 입장과 대화 내역 조회는 403을 반환한다")
    void rejectsRoomReadsFromNonParticipant() throws Exception {
        when(service.getChatRoom("KAKAO_123", "room-1"))
                .thenThrow(new BusinessException(ErrorCode.CHAT_FORBIDDEN));
        when(service.getMessages("KAKAO_123", "room-1"))
                .thenThrow(new BusinessException(ErrorCode.CHAT_FORBIDDEN));

        mockMvc.perform(get("/chat-rooms/room-1").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_403"));
        mockMvc.perform(get("/chat-rooms/room-1/messages").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_403"));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 입장은 404를 반환한다")
    void missingRoomReturnsNotFound() throws Exception {
        when(service.getChatRoom("KAKAO_123", "missing"))
                .thenThrow(new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        mockMvc.perform(get("/chat-rooms/missing").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_404"));
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

    @Test
    @DisplayName("텍스트 메시지를 새로 저장하면 201과 저장된 메시지 정보를 반환한다")
    void sendsTextMessage() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage saved = savedMessage(clientMessageId);
        when(service.sendTextMessage(any(SendTextMessageCommand.class)))
                .thenReturn(SendMessageResult.of(saved, true));

        mockMvc.perform(post("/chat-rooms/room-1/messages")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clientMessageId, "안녕하세요")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(17))
                .andExpect(jsonPath("$.data.roomId").value("room-1"))
                .andExpect(jsonPath("$.data.clientMessageId").value(clientMessageId.toString()))
                .andExpect(jsonPath("$.data.senderUserId").value("sender-1"))
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andExpect(jsonPath("$.data.content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-26T21:30:00+09:00"));

        ArgumentCaptor<SendTextMessageCommand> command = ArgumentCaptor.forClass(SendTextMessageCommand.class);
        verify(service).sendTextMessage(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(command.getValue().getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(command.getValue().getContent()).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("같은 메시지 재시도에는 200과 기존 메시지 정보를 반환한다")
    void repeatedSendReturnsOk() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        when(service.sendTextMessage(any(SendTextMessageCommand.class)))
                .thenReturn(SendMessageResult.of(savedMessage(clientMessageId), false));

        mockMvc.perform(post("/chat-rooms/room-1/messages")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(clientMessageId, "안녕하세요")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(17));
    }

    @Test
    @DisplayName("잘못된 UUID와 공백 본문 및 5000자 초과 본문은 400으로 거부한다")
    void rejectsInvalidMessageRequests() throws Exception {
        String uuid = UUID.randomUUID().toString();
        List<String> payloads = List.of(
                "{\"clientMessageId\":\"invalid\",\"content\":\"안녕\"}",
                "{\"content\":\"안녕\"}",
                "{\"clientMessageId\":\"" + uuid + "\",\"content\":\"   \\n  \"}",
                "{\"clientMessageId\":\"" + uuid + "\",\"content\":\"" + "x".repeat(5001) + "\"}");

        for (String body : payloads) {
            mockMvc.perform(post("/chat-rooms/room-1/messages")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("권한이 없는 사용자의 메시지 전송은 403을 반환한다")
    void rejectsMessageFromNonParticipant() throws Exception {
        when(service.sendTextMessage(any(SendTextMessageCommand.class)))
                .thenThrow(new BusinessException(ErrorCode.CHAT_FORBIDDEN));

        mockMvc.perform(post("/chat-rooms/room-1/messages")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(UUID.randomUUID(), "안녕하세요")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_403"));
    }

    @Test
    @DisplayName("첨부 업로드 준비는 201과 업로드 ID, URL, 헤더, URL 만료 시각을 반환한다")
    void preparesAttachmentUpload() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(service.prepareAttachmentUpload(any(PrepareAttachmentUploadCommand.class)))
                .thenReturn(PrepareAttachmentUploadResult.of(uploadId, "https://upload.example",
                        Map.of("content-type", "image/png", "x-amz-tagging", "chat-upload=pending"),
                        LocalDateTime.of(2026, 9, 27, 14, 10)));

        mockMvc.perform(post("/chat-rooms/room-1/attachments/uploads")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadPayload("IMAGE", "시안.png", "image/png", "482133")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.example"))
                .andExpect(jsonPath("$.data.uploadHeaders['content-type']").value("image/png"))
                .andExpect(jsonPath("$.data.uploadHeaders['x-amz-tagging']").value("chat-upload=pending"))
                .andExpect(jsonPath("$.data.uploadUrlExpiresAt").value("2026-09-27T23:10:00+09:00"));

        ArgumentCaptor<PrepareAttachmentUploadCommand> command =
                ArgumentCaptor.forClass(PrepareAttachmentUploadCommand.class);
        verify(service).prepareAttachmentUpload(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(command.getValue().getType()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(command.getValue().getFileName()).isEqualTo("시안.png");
        assertThat(command.getValue().getContentType()).isEqualTo("image/png");
        assertThat(command.getValue().getSize()).isEqualTo(482133L);
    }

    @Test
    @DisplayName("TEXT 타입, 누락 값, 경로가 들어간 파일명, 0 이하 크기의 업로드 준비는 400으로 거부한다")
    void rejectsInvalidUploadRequests() throws Exception {
        List<String> payloads = List.of(
                uploadPayload("TEXT", "메모.txt", "text/plain", "10"),
                uploadPayload("VIDEO", "영상.mp4", "video/mp4", "10"),
                "{\"fileName\":\"시안.png\",\"contentType\":\"image/png\",\"size\":10}",
                uploadPayload("IMAGE", "   ", "image/png", "10"),
                uploadPayload("IMAGE", "../시안.png", "image/png", "10"),
                uploadPayload("IMAGE", "폴더\\\\시안.png", "image/png", "10"),
                uploadPayload("IMAGE", "x".repeat(252) + ".png", "image/png", "10"),
                uploadPayload("IMAGE", "시안.png", "", "10"),
                uploadPayload("IMAGE", "시안.png", "image/png", "0"));

        for (String body : payloads) {
            mockMvc.perform(post("/chat-rooms/room-1/attachments/uploads")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("허용되지 않은 형식과 크기 초과는 각각의 400 오류 코드로 반환한다")
    void returnsUploadPolicyErrors() throws Exception {
        when(service.prepareAttachmentUpload(any(PrepareAttachmentUploadCommand.class)))
                .thenThrow(new BusinessException(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED))
                .thenThrow(new BusinessException(ErrorCode.CHAT_UPLOAD_TOO_LARGE));

        mockMvc.perform(post("/chat-rooms/room-1/attachments/uploads")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadPayload("IMAGE", "견적서.pdf", "application/pdf", "10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_UPLOAD_400_TYPE"));
        mockMvc.perform(post("/chat-rooms/room-1/attachments/uploads")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadPayload("IMAGE", "시안.png", "image/png", "999999999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_UPLOAD_400_SIZE"));
    }

    @Test
    @DisplayName("첨부 메시지를 새로 저장하면 201과 열람 URL, 파일명, URL 만료 시각을 반환한다")
    void sendsAttachmentMessage() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage saved = savedAttachment(clientMessageId);
        when(service.sendAttachmentMessage(any(SendAttachmentMessageCommand.class)))
                .thenReturn(SendAttachmentMessageResult.of(saved, true, "https://view.example",
                        LocalDateTime.of(2026, 9, 26, 12, 45)));

        mockMvc.perform(post("/chat-rooms/room-1/messages/attachments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachmentPayload(clientMessageId.toString(), "FILE",
                                saved.getAttachmentUploadId().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.roomId").value("room-1"))
                .andExpect(jsonPath("$.data.clientMessageId").value(clientMessageId.toString()))
                .andExpect(jsonPath("$.data.senderUserId").value("sender-1"))
                .andExpect(jsonPath("$.data.type").value("FILE"))
                .andExpect(jsonPath("$.data.content").value("https://view.example"))
                .andExpect(jsonPath("$.data.attachmentName").value("견적서.pdf"))
                .andExpect(jsonPath("$.data.contentExpiresAt").value("2026-09-26T21:45:00+09:00"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-26T21:30:00+09:00"));

        ArgumentCaptor<SendAttachmentMessageCommand> command =
                ArgumentCaptor.forClass(SendAttachmentMessageCommand.class);
        verify(service).sendAttachmentMessage(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(command.getValue().getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(command.getValue().getType()).isEqualTo(ChatMessageType.FILE);
        assertThat(command.getValue().getUploadId()).isEqualTo(saved.getAttachmentUploadId());
    }

    @Test
    @DisplayName("같은 첨부 메시지 재시도에는 200과 기존 메시지 정보를 반환한다")
    void repeatedAttachmentSendReturnsOk() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage saved = savedAttachment(clientMessageId);
        when(service.sendAttachmentMessage(any(SendAttachmentMessageCommand.class)))
                .thenReturn(SendAttachmentMessageResult.of(saved, false, "https://view.example/retry",
                        LocalDateTime.of(2026, 9, 26, 12, 50)));

        mockMvc.perform(post("/chat-rooms/room-1/messages/attachments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachmentPayload(clientMessageId.toString(), "FILE",
                                saved.getAttachmentUploadId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.content").value("https://view.example/retry"));
    }

    @Test
    @DisplayName("TEXT 타입, 잘못된 UUID, 누락 값의 첨부 메시지 요청은 400으로 거부한다")
    void rejectsInvalidAttachmentRequests() throws Exception {
        String uuid = UUID.randomUUID().toString();
        List<String> payloads = List.of(
                attachmentPayload(uuid, "TEXT", uuid),
                attachmentPayload(uuid, "VIDEO", uuid),
                attachmentPayload("invalid", "IMAGE", uuid),
                attachmentPayload(uuid, "IMAGE", "invalid"),
                "{\"type\":\"IMAGE\",\"uploadId\":\"" + uuid + "\"}",
                "{\"clientMessageId\":\"" + uuid + "\",\"uploadId\":\"" + uuid + "\"}",
                "{\"clientMessageId\":\"" + uuid + "\",\"type\":\"IMAGE\"}");

        for (String body : payloads) {
            mockMvc.perform(post("/chat-rooms/room-1/messages/attachments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("첨부 메시지 전송의 업로드 오류는 계약된 상태와 오류 코드로 반환한다")
    void returnsAttachmentSendErrors() throws Exception {
        String uuid = UUID.randomUUID().toString();
        Map<ErrorCode, Integer> errors = Map.of(
                ErrorCode.CHAT_MESSAGE_CONFLICT, 409,
                ErrorCode.CHAT_UPLOAD_NOT_FOUND, 404,
                ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED, 400,
                ErrorCode.CHAT_UPLOAD_ALREADY_USED, 409,
                ErrorCode.CHAT_UPLOAD_NOT_READY, 409,
                ErrorCode.CHAT_UPLOAD_UNAVAILABLE, 502);

        for (Map.Entry<ErrorCode, Integer> error : errors.entrySet()) {
            when(service.sendAttachmentMessage(any(SendAttachmentMessageCommand.class)))
                    .thenThrow(new BusinessException(error.getKey()));

            mockMvc.perform(post("/chat-rooms/room-1/messages/attachments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attachmentPayload(uuid, "IMAGE", uuid)))
                    .andExpect(status().is(error.getValue()))
                    .andExpect(jsonPath("$.error.code").value(error.getKey().getCode()));
        }
    }

    @Test
    @DisplayName("메시지 내역의 IMAGE·FILE 메시지는 열람 URL과 만료 시각을 반환한다")
    void returnsAttachmentUrlInMessages() throws Exception {
        ChatMessage attachment = savedAttachment(UUID.randomUUID());
        when(service.getMessages("KAKAO_123", "room-1")).thenReturn(ChatMessageListResponse.from(List.of(
                MessageResult.text(savedMessage(UUID.randomUUID())),
                MessageResult.attachment(attachment, "https://view.example", LocalDateTime.of(2026, 9, 26, 12, 45),
                        2100000L),
                MessageResult.attachment(savedAttachment(UUID.randomUUID()), null, null, null)),
                "viewer-1"));

        mockMvc.perform(get("/chat-rooms/room-1/messages").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.messages[0].contentExpiresAt").value(nullValue()))
                .andExpect(jsonPath("$.data.messages[1].type").value("FILE"))
                .andExpect(jsonPath("$.data.messages[1].content").value("https://view.example"))
                .andExpect(jsonPath("$.data.messages[1].attachmentName").value("견적서.pdf"))
                .andExpect(jsonPath("$.data.messages[1].attachmentSize").value(2100000))
                .andExpect(jsonPath("$.data.messages[1].attachmentSize").isNumber())
                .andExpect(jsonPath("$.data.messages[1].contentExpiresAt").value("2026-09-26T21:45:00+09:00"))
                // 업로드 정보가 없는 과거 첨부는 필드를 생략하지 않고 null로 내린다
                .andExpect(jsonPath("$.data.messages[2].attachmentSize").hasJsonPath())
                .andExpect(jsonPath("$.data.messages[2].attachmentSize").value(nullValue()));
    }

    @Test
    @DisplayName("메시지 단건 조회는 목록 항목과 같은 형식으로 새 열람 URL을 반환한다")
    void returnsSingleMessage() throws Exception {
        ChatMessage attachment = savedAttachment(UUID.randomUUID());
        when(service.getMessage("KAKAO_123", "room-1", 18L)).thenReturn(ChatMessageListResponse.Message.from(
                MessageResult.attachment(attachment, "https://view.example/new", LocalDateTime.of(2026, 9, 26, 13, 0),
                        1024L)));

        mockMvc.perform(get("/chat-rooms/room-1/messages/18").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(18))
                .andExpect(jsonPath("$.data.roomId").value("room-1"))
                .andExpect(jsonPath("$.data.type").value("FILE"))
                .andExpect(jsonPath("$.data.content").value("https://view.example/new"))
                .andExpect(jsonPath("$.data.attachmentName").value("견적서.pdf"))
                .andExpect(jsonPath("$.data.attachmentSize").value(1024))
                .andExpect(jsonPath("$.data.attachmentSize").isNumber())
                .andExpect(jsonPath("$.data.contentExpiresAt").value("2026-09-26T22:00:00+09:00"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-26T21:30:00+09:00"));
    }

    @Test
    @DisplayName("메시지 단건 조회는 숫자가 아닌 ID를 400으로, 없는 메시지를 404로 반환한다")
    void rejectsInvalidOrMissingSingleMessage() throws Exception {
        when(service.getMessage("KAKAO_123", "room-1", 99L))
                .thenThrow(new BusinessException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));

        mockMvc.perform(get("/chat-rooms/room-1/messages/abc").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        mockMvc.perform(get("/chat-rooms/room-1/messages/99").principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_MESSAGE_404"));
    }

    private ChatMessage savedAttachment(UUID clientMessageId) {
        ChatAttachmentUpload upload = ChatAttachmentUpload.create("room-1", "sender-1", ChatMessageType.FILE,
                "견적서.pdf", "application/pdf", 1024L, LocalDateTime.of(2026, 9, 26, 13, 30));
        ChatMessage message = ChatMessage.createAttachment("sender-1", clientMessageId, upload);
        ReflectionTestUtils.setField(message, "id", 18L);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 9, 26, 12, 30));
        return message;
    }

    private String attachmentPayload(String clientMessageId, String type, String uploadId) {
        return "{\"clientMessageId\":\"" + clientMessageId + "\",\"type\":\"" + type
                + "\",\"uploadId\":\"" + uploadId + "\"}";
    }

    private ChatMessage savedMessage(UUID clientMessageId) {
        return ChatMessage.builder().id(17L).roomId("room-1").clientMessageId(clientMessageId)
                .senderUserId("sender-1").type(ChatMessageType.TEXT).content("안녕하세요")
                .createdAt(LocalDateTime.of(2026, 9, 26, 12, 30)).build();
    }

    private Room roomResponse() {
        return roomResponse(JobStatus.MATCHED);
    }

    private Room roomResponse(JobStatus status) {
        return roomResponse(status, submission(JobSubmissionType.REVISION, 2), 31L);
    }

    private JobSubmission submission(JobSubmissionType type, int revisionNumber) {
        return JobSubmission.builder().jobId(11L).submissionType(type).revisionNumber(revisionNumber)
                .reviewStatus(JobSubmissionReviewStatus.PENDING).build();
    }

    private Room roomResponse(JobStatus status, JobSubmission latestSubmission, Long proposalId) {
        ChatRoom room = ChatRoom.create(11L);
        ReflectionTestUtils.setField(room, "id", "01K58M6PJV8VAJMXHBHJ2PNB5C");
        Job job = Job.builder().id(11L).title("의뢰 제목").status(status).budget(300000L).revisionCount(2)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20)).proposalId(proposalId).build();
        return Room.of(room, job, "학생 이름", "student.png",
                LastMessage.of(ChatMessageType.TEXT, "안녕하세요", LocalDateTime.of(2026, 9, 26, 12, 30)),
                3L, DeadlineType.DRAFT, LocalDate.of(2026, 10, 10),
                latestSubmission, JobApplication.builder().summary("한 줄 요약")
                        .workPlan("작업계획서").deliveryMethod("결과물 전달 방법").build());
    }

    private String uploadPayload(String type, String fileName, String contentType, String size) {
        return "{\"type\":\"" + type + "\",\"fileName\":\"" + fileName + "\",\"contentType\":\""
                + contentType + "\",\"size\":" + size + "}";
    }

    private String payload(UUID clientMessageId, String content) {
        return "{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"" + content + "\"}";
    }
}
