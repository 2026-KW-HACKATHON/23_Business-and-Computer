package com.gakkum.backend.application.chat.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.application.chat.dto.DeadlineType;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient;
import com.gakkum.backend.domain.chat.client.ChatAttachmentStorageClient.PresignedUpload;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatService;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.MarkReadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.PrepareAttachmentUploadCommand;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendTextMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.PrepareAttachmentUploadResult;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUpload;
import com.gakkum.backend.domain.chat.entity.ChatAttachmentUploadStatus;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.repository.ChatAttachmentUploadRepository;
import com.gakkum.backend.domain.chat.repository.ChatMessageRepository;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class ChatFacadeTest {

    private static final String OWNER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    private final UserService userService = mock(UserService.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository submissionRepository = mock(JobSubmissionRepository.class);
    private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
    private final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    private final ChatAttachmentUploadRepository uploadRepository = mock(ChatAttachmentUploadRepository.class);
    private final ChatAttachmentStorageClient storageClient = mock(ChatAttachmentStorageClient.class);
    private final Instant now = Instant.parse("2026-09-27T05:00:00Z");
    private final ChatService chatService = new ChatService(roomRepository, messageRepository, uploadRepository,
            new ChatAttachmentPolicy(DataSize.ofMegabytes(10), DataSize.ofMegabytes(50), Duration.ofHours(1)),
            Clock.fixed(now, ZoneOffset.UTC));
    private final ChatFacade service = new ChatFacade(userService, ownerRepository, studentRepository,
            jobRepository, applicationRepository, submissionRepository, chatService, storageClient);

    @BeforeEach
    void setUp() {
        when(applicationRepository.findByJobIdInAndStatus(anyList(), eq(JobApplicationStatus.ACCEPTED)))
                .thenReturn(List.of());
        when(submissionRepository.findByJobIdIn(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("사장님 목록은 선택된 학생 정보와 최근 채팅 및 안 읽은 개수를 반환한다")
    void ownerListShowsStudentAndMessages() {
        owner();
        Job open = job(1L, "공개 의뢰", JobStatus.OPEN);
        Job closed = job(2L, "종료 의뢰", JobStatus.CLOSED);
        Job quiet = job(3L, "새 의뢰", JobStatus.MATCHED);
        when(jobRepository.findByOwnerProfileId(10L)).thenReturn(List.of(open, closed, quiet));
        ChatRoom closedRoom = room(2L, LocalDateTime.of(2026, 9, 25, 10, 0));
        ChatRoom quietRoom = room(3L, LocalDateTime.of(2026, 9, 26, 10, 0));
        when(roomRepository.findByJobIdIn(anyList())).thenReturn(List.of(quietRoom, closedRoom));
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student()));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of(STUDENT_ID,
                User.builder().id(STUDENT_ID).name("학생 이름").build()));
        ChatMessage image = message(7L, closedRoom.getId(), ChatMessageType.IMAGE, null, null,
                LocalDateTime.of(2026, 9, 26, 11, 0));
        when(messageRepository.findLatestByRoomIds(anyList())).thenReturn(List.of(image));
        ChatMessageRepository.UnreadCount count = unread(closedRoom.getId(), 2L);
        when(messageRepository.countUnreadByRoomIds(anyList(), eq(OWNER_ID), eq(true)))
                .thenReturn(List.of(count));

        ChatRoomListResponse result = service.getMyChatRooms("owner");

        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getRooms()).extracting(ChatRoomListResponse.Room::getJobId).containsExactly(2L, 3L);
        assertThat(result.getRooms().get(0).getCounterpartName()).isEqualTo("학생 이름");
        assertThat(result.getRooms().get(0).getCounterpartProfileImageUrl()).isEqualTo("student.png");
        assertThat(result.getRooms().get(0).getLastMessage().getPreview()).isEqualTo("사진");
        assertThat(result.getRooms().get(0).getLastMessage().getCreatedAt()).isEqualTo(image.getCreatedAt());
        assertThat(result.getRooms().get(0).getUnreadCount()).isEqualTo(2L);
        assertThat(result.getRooms().get(1).getLastMessage()).isNull();
        assertThat(result.getRooms().get(1).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("학생 목록은 매장 정보와 파일 이름 미리보기를 반환한다")
    void studentListShowsStoreAndFilePreview() {
        studentViewer();
        when(jobRepository.findBySelectedStudentProfileId(20L))
                .thenReturn(List.of(job(2L, "종료 의뢰", JobStatus.CLOSED)));
        ChatRoom room = room(2L, LocalDateTime.of(2026, 9, 25, 10, 0));
        when(roomRepository.findByJobIdIn(anyList())).thenReturn(List.of(room));
        when(ownerRepository.findAllById(anyList()))
                .thenReturn(List.of(Owner.builder().id(10L).storeName("가게 이름")
                        .profileImageUrl("store.png").build()));
        when(messageRepository.findLatestByRoomIds(anyList())).thenReturn(List.of(message(
                9L, room.getId(), ChatMessageType.FILE, null, "견적서.pdf", LocalDateTime.now())));
        when(messageRepository.countUnreadByRoomIds(anyList(), eq(STUDENT_ID), eq(false)))
                .thenReturn(List.of());

        ChatRoomListResponse result = service.getMyChatRooms("student");

        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getRooms().get(0).getCounterpartName()).isEqualTo("가게 이름");
        assertThat(result.getRooms().get(0).getCounterpartProfileImageUrl()).isEqualTo("store.png");
        assertThat(result.getRooms().get(0).getLastMessage().getPreview()).isEqualTo("견적서.pdf");
        assertThat(result.getRooms().get(0).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("초안 승인 전에는 초안 마감일을, 승인 후에는 최종 마감일을 표시한다")
    void deadlineChangesAfterDraftApproval() {
        owner();
        List<Job> jobs = List.of(job(1L, "제출 전", JobStatus.MATCHED),
                job(2L, "검토 중", JobStatus.MATCHED), job(3L, "초안 승인", JobStatus.MATCHED));
        when(jobRepository.findByOwnerProfileId(10L)).thenReturn(jobs);
        when(roomRepository.findByJobIdIn(anyList())).thenReturn(List.of(
                room(1L, LocalDateTime.now()), room(2L, LocalDateTime.now()), room(3L, LocalDateTime.now())));
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student()));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of(STUDENT_ID,
                User.builder().id(STUDENT_ID).name("학생 이름").build()));
        when(submissionRepository.findByJobIdIn(anyList())).thenReturn(List.of(
                submission(2L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING),
                submission(3L, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.APPROVED),
                submission(3L, JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.REVISION_REQUESTED)));
        when(applicationRepository.findByJobIdInAndStatus(anyList(), eq(JobApplicationStatus.ACCEPTED)))
                .thenReturn(List.of(JobApplication.builder().jobId(3L).studentProfileId(20L)
                        .content("선택된 지원서").status(JobApplicationStatus.ACCEPTED).build()));

        Map<Long, ChatRoomListResponse.Room> rooms = service.getMyChatRooms("owner").getRooms().stream()
                .collect(java.util.stream.Collectors.toMap(ChatRoomListResponse.Room::getJobId,
                        java.util.function.Function.identity()));

        assertThat(rooms.get(1L).getDeadlineType()).isEqualTo(DeadlineType.DRAFT);
        assertThat(rooms.get(1L).getDeadlineDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(rooms.get(1L).getSubmissionReviewStatus()).isNull();
        assertThat(rooms.get(1L).getApplicationContent()).isNull();
        assertThat(rooms.get(2L).getDeadlineType()).isEqualTo(DeadlineType.DRAFT);
        assertThat(rooms.get(2L).getSubmissionReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(rooms.get(3L).getDeadlineType()).isEqualTo(DeadlineType.FINAL);
        assertThat(rooms.get(3L).getDeadlineDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(rooms.get(3L).getSubmissionReviewStatus())
                .isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(rooms.get(3L).getApplicationContent()).isEqualTo("선택된 지원서");
        assertThat(rooms.get(3L).getBudget()).isEqualTo(300000L);
        assertThat(rooms.get(3L).getRevisionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("종료된 의뢰에는 현재 마감 유형과 날짜를 표시하지 않는다")
    void closedJobHasNoActiveDeadline() {
        studentViewer();
        Job closed = job(2L, "종료 의뢰", JobStatus.CLOSED);
        when(jobRepository.findBySelectedStudentProfileId(20L)).thenReturn(List.of(closed));
        when(roomRepository.findByJobIdIn(anyList())).thenReturn(List.of(room(2L, LocalDateTime.now())));
        when(ownerRepository.findAllById(anyList())).thenReturn(List.of(
                Owner.builder().id(10L).storeName("가게 이름").profileImageUrl("store.png").build()));

        ChatRoomListResponse.Room result = service.getMyChatRooms("student").getRooms().get(0);

        assertThat(result.getCounterpartName()).isEqualTo("가게 이름");
        assertThat(result.getDeadlineType()).isNull();
        assertThat(result.getDeadlineDate()).isNull();
        assertThat(result.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(result.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("참여자는 목록 조회 없이 채팅방 입장 정보를 조회한다")
    void participantGetsRoomDirectly() {
        owner();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student()));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of(STUDENT_ID,
                User.builder().id(STUDENT_ID).name("학생 이름").build()));

        ChatRoomListResponse.Room result = service.getChatRoom("owner", room.getId());

        assertThat(result.getRoomId()).isEqualTo(room.getId());
        assertThat(result.getJobTitle()).isEqualTo("의뢰");
        assertThat(result.getCounterpartName()).isEqualTo("학생 이름");
        assertThat(result.getDeadlineType()).isEqualTo(DeadlineType.DRAFT);
    }

    @Test
    @DisplayName("비참여자는 입장 정보와 메시지 내역을 조회할 수 없다")
    void nonParticipantCannotReadRoom() {
        when(userService.getActiveUser("owner")).thenReturn(User.builder()
                .id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID))
                .thenReturn(Optional.of(Owner.builder().id(99L).build()));
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));

        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.getChatRoom("owner", room.getId()));
        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.getMessages("owner", room.getId()));
        verify(messageRepository, never()).findByRoomIdOrderByIdAsc(room.getId());
    }

    @Test
    @DisplayName("참여자는 채팅방 메시지 전체를 조회한다")
    void participantGetsMessages() {
        studentViewer();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));
        when(messageRepository.findByRoomIdOrderByIdAsc(room.getId())).thenReturn(List.of(
                message(1L, room.getId(), ChatMessageType.TEXT, "첫 메시지", null, LocalDateTime.now()),
                message(2L, room.getId(), ChatMessageType.FILE, null, "견적서.pdf", LocalDateTime.now())));

        var result = service.getMessages("student", room.getId());

        assertThat(result.getMessages()).extracting(message -> message.getId()).containsExactly(1L, 2L);
        assertThat(result.getMessages().get(1).getAttachmentName()).isEqualTo("견적서.pdf");
    }

    @Test
    @DisplayName("메시지가 있는 방은 최근순이고 빈 방은 생성일 최신순으로 뒤에 온다")
    void sortsRooms() {
        owner();
        List<Job> jobs = List.of(job(1L, "A", JobStatus.MATCHED), job(2L, "B", JobStatus.MATCHED),
                job(3L, "C", JobStatus.MATCHED), job(4L, "D", JobStatus.MATCHED));
        when(jobRepository.findByOwnerProfileId(10L)).thenReturn(jobs);
        ChatRoom first = room(1L, LocalDateTime.of(2026, 9, 1, 0, 0));
        ChatRoom second = room(2L, LocalDateTime.of(2026, 9, 2, 0, 0));
        ChatRoom third = room(3L, LocalDateTime.of(2026, 9, 3, 0, 0));
        ChatRoom fourth = room(4L, LocalDateTime.of(2026, 9, 4, 0, 0));
        when(roomRepository.findByJobIdIn(anyList())).thenReturn(List.of(first, second, third, fourth));
        when(studentRepository.findAllById(anyList())).thenReturn(List.of(student()));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of(STUDENT_ID,
                User.builder().id(STUDENT_ID).name("학생").build()));
        when(messageRepository.findLatestByRoomIds(anyList())).thenReturn(List.of(
                message(1L, first.getId(), ChatMessageType.TEXT, "예전 메시지", null,
                        LocalDateTime.of(2026, 9, 5, 0, 0)),
                message(2L, second.getId(), ChatMessageType.TEXT, "최신 메시지", null,
                        LocalDateTime.of(2026, 9, 6, 0, 0))));
        when(messageRepository.countUnreadByRoomIds(anyList(), eq(OWNER_ID), eq(true))).thenReturn(List.of());

        ChatRoomListResponse result = service.getMyChatRooms("owner");

        assertThat(result.getRooms()).extracting(ChatRoomListResponse.Room::getJobId)
                .containsExactly(2L, 1L, 4L, 3L);
        assertThat(result.getRooms().get(0).getLastMessage().getPreview()).isEqualTo("최신 메시지");
    }

    @Test
    @DisplayName("채팅방이 없으면 빈 목록과 개수 0을 반환한다")
    void returnsEmptyList() {
        owner();
        when(jobRepository.findByOwnerProfileId(10L)).thenReturn(List.of());

        ChatRoomListResponse result = service.getMyChatRooms("owner");

        assertThat(result.getCount()).isZero();
        assertThat(result.getRooms()).isEmpty();
    }

    @Test
    @DisplayName("참여자는 해당 방의 메시지까지 읽음 위치를 앞으로만 이동한다")
    void participantCanAdvanceReadPosition() {
        owner();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message(
                5L, room.getId(), ChatMessageType.TEXT, "내용", null, LocalDateTime.now())));
        when(messageRepository.findById(3L)).thenReturn(Optional.of(message(
                3L, room.getId(), ChatMessageType.TEXT, "이전", null, LocalDateTime.now())));

        service.markRead(MarkReadCommand.of("owner", room.getId(), 5L));
        service.markRead(MarkReadCommand.of("owner", room.getId(), 3L));

        assertThat(room.getOwnerLastReadMessageId()).isEqualTo(5L);
        assertThat(room.getStudentLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("참여자가 아닌 사용자는 읽음 위치를 바꿀 수 없다")
    void rejectsNonParticipant() {
        when(userService.getActiveUser("owner")).thenReturn(User.builder()
                .id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(Owner.builder().id(99L).build()));
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));

        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.markRead(MarkReadCommand.of("owner", room.getId(), 5L)));
        assertThat(room.getOwnerLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("다른 채팅방 메시지로 읽음 위치를 갱신할 수 없다")
    void rejectsOtherRoomMessage() {
        studentViewer();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));
        when(messageRepository.findById(5L)).thenReturn(Optional.of(message(
                5L, "other-room", ChatMessageType.TEXT, "내용", null, LocalDateTime.now())));

        assertCode(ErrorCode.CHAT_MESSAGE_NOT_FOUND, () -> service.markRead(MarkReadCommand.of("student", room.getId(), 5L)));
        assertThat(room.getStudentLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("선택되지 않은 학생은 채팅방 읽음 위치를 바꿀 수 없다")
    void rejectsUnselectedStudent() {
        when(userService.getActiveUser("student")).thenReturn(User.builder()
                .id(STUDENT_ID).role(UserRole.STUDENT).build());
        when(studentRepository.findByUserId(STUDENT_ID))
                .thenReturn(Optional.of(Student.builder().id(99L).userId(STUDENT_ID).build()));
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));

        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.markRead(MarkReadCommand.of("student", room.getId(), 5L)));
        assertThat(room.getStudentLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("사장님은 본인의 채팅방에 공백과 줄바꿈을 보존한 텍스트를 보낸다")
    void ownerSendsText() {
        ChatRoom room = arrangeOwnerSend(JobStatus.MATCHED);
        UUID clientMessageId = UUID.randomUUID();
        String content = "  안녕하세요\n다음 줄  ";
        ChatMessage saved = ChatMessage.builder().id(15L).roomId(room.getId())
                .senderUserId(OWNER_ID).clientMessageId(clientMessageId)
                .type(ChatMessageType.TEXT).content(content).createdAt(LocalDateTime.now()).build();
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        SendMessageResult result = service.sendTextMessage(
                SendTextMessageCommand.of("owner", room.getId(), clientMessageId, content));

        assertThat(result.isCreated()).isTrue();
        assertThat(result.getMessage()).isSameAs(saved);
        ArgumentCaptor<ChatMessage> message = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).saveAndFlush(message.capture());
        assertThat(message.getValue().getRoomId()).isEqualTo(room.getId());
        assertThat(message.getValue().getSenderUserId()).isEqualTo(OWNER_ID);
        assertThat(message.getValue().getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(message.getValue().getType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(message.getValue().getContent()).isEqualTo(content);
        assertThat(room.getOwnerLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("선택된 학생은 종료된 의뢰의 채팅방에도 메시지를 보낼 수 있다")
    void selectedStudentSendsToClosedJob() {
        studentViewer();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.CLOSED)));
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage saved = ChatMessage.builder().id(16L).roomId(room.getId())
                .senderUserId(STUDENT_ID).clientMessageId(clientMessageId)
                .type(ChatMessageType.TEXT).content("완료했습니다").build();
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenReturn(saved);

        SendMessageResult result = service.sendTextMessage(
                SendTextMessageCommand.of("student", room.getId(), clientMessageId, "완료했습니다"));

        assertThat(result.isCreated()).isTrue();
        verify(messageRepository).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("같은 UUID와 본문의 재요청은 저장된 메시지를 반환하고 다시 저장하지 않는다")
    void repeatedSendReturnsExistingMessage() {
        ChatRoom room = arrangeOwnerSend(JobStatus.MATCHED);
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage existing = ChatMessage.builder().id(15L).roomId(room.getId())
                .senderUserId(OWNER_ID).clientMessageId(clientMessageId)
                .type(ChatMessageType.TEXT).content("안녕하세요").build();
        when(messageRepository.findByRoomIdAndSenderUserIdAndClientMessageId(
                room.getId(), OWNER_ID, clientMessageId)).thenReturn(Optional.of(existing));

        SendMessageResult result = service.sendTextMessage(
                SendTextMessageCommand.of("owner", room.getId(), clientMessageId, "안녕하세요"));

        assertThat(result.isCreated()).isFalse();
        assertThat(result.getMessage()).isSameAs(existing);
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("같은 UUID에 다른 본문을 보내면 충돌로 거부한다")
    void rejectsDifferentContentForSameClientId() {
        ChatRoom room = arrangeOwnerSend(JobStatus.MATCHED);
        UUID clientMessageId = UUID.randomUUID();
        when(messageRepository.findByRoomIdAndSenderUserIdAndClientMessageId(
                room.getId(), OWNER_ID, clientMessageId)).thenReturn(Optional.of(
                        ChatMessage.builder().type(ChatMessageType.TEXT).content("기존 내용").build()));

        assertCode(ErrorCode.CHAT_MESSAGE_CONFLICT, () -> service.sendTextMessage(
                SendTextMessageCommand.of("owner", room.getId(), clientMessageId, "다른 내용")));
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("참여자가 아닌 사용자는 메시지를 저장할 수 없다")
    void rejectsMessageFromNonParticipant() {
        when(userService.getActiveUser("owner")).thenReturn(User.builder()
                .id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(Owner.builder().id(99L).build()));
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));

        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.sendTextMessage(
                SendTextMessageCommand.of("owner", room.getId(), UUID.randomUUID(), "안녕하세요")));
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("존재하지 않는 채팅방에는 메시지를 보낼 수 없다")
    void rejectsMessageToMissingRoom() {
        owner();
        when(roomRepository.findLockedById("missing")).thenReturn(Optional.empty());

        assertCode(ErrorCode.CHAT_ROOM_NOT_FOUND, () -> service.sendTextMessage(
                SendTextMessageCommand.of("owner", "missing", UUID.randomUUID(), "안녕하세요")));
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    @DisplayName("참여자는 업로드 기록을 저장하고 서명된 업로드 URL을 받는다")
    void participantPreparesAttachmentUpload() {
        ChatRoom room = arrangeOwnerUpload();
        when(uploadRepository.save(any(ChatAttachmentUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant urlExpiresAt = now.plus(Duration.ofMinutes(10));
        when(storageClient.presignUpload(any(), any(), anyLong())).thenReturn(new PresignedUpload(
                "https://upload.example", Map.of("content-type", "image/png"), urlExpiresAt));

        PrepareAttachmentUploadResult result = service.prepareAttachmentUpload(PrepareAttachmentUploadCommand.of(
                "owner", room.getId(), ChatMessageType.IMAGE, "시안.PNG", "Image/PNG", 482133L));

        ArgumentCaptor<ChatAttachmentUpload> upload = ArgumentCaptor.forClass(ChatAttachmentUpload.class);
        verify(uploadRepository).save(upload.capture());
        ChatAttachmentUpload saved = upload.getValue();
        assertThat(saved.getRoomId()).isEqualTo(room.getId());
        assertThat(saved.getUploaderUserId()).isEqualTo(OWNER_ID);
        assertThat(saved.getType()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(saved.getFileName()).isEqualTo("시안.PNG");
        assertThat(saved.getContentType()).isEqualTo("image/png");
        assertThat(saved.getFileSize()).isEqualTo(482133L);
        assertThat(saved.getStatus()).isEqualTo(ChatAttachmentUploadStatus.PENDING);
        assertThat(saved.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(now.plus(Duration.ofHours(1)), ZoneId.systemDefault()));
        verify(storageClient).presignUpload(saved.getStorageKey(), "image/png", 482133L);

        assertThat(result.getUploadId()).isEqualTo(saved.getId());
        assertThat(result.getUploadUrl()).isEqualTo("https://upload.example");
        assertThat(result.getUploadHeaders()).containsEntry("content-type", "image/png");
        assertThat(result.getUploadUrlExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(urlExpiresAt, ZoneId.systemDefault()));
    }

    @Test
    @DisplayName("허용되지 않은 형식은 업로드 기록을 만들거나 URL을 발급하지 않는다")
    void rejectsDisallowedUploadBeforeSaving() {
        ChatRoom room = arrangeOwnerUpload();

        assertCode(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED, () -> service.prepareAttachmentUpload(
                PrepareAttachmentUploadCommand.of("owner", room.getId(), ChatMessageType.IMAGE,
                        "견적서.pdf", "application/pdf", 1024L)));
        verify(uploadRepository, never()).save(any(ChatAttachmentUpload.class));
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("참여자가 아니거나 없는 채팅방에는 업로드를 준비할 수 없다")
    void rejectsUploadFromNonParticipantOrMissingRoom() {
        when(userService.getActiveUser("owner")).thenReturn(User.builder()
                .id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(Owner.builder().id(99L).build()));
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomRepository.findById("missing")).thenReturn(Optional.empty());
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));

        assertCode(ErrorCode.CHAT_FORBIDDEN, () -> service.prepareAttachmentUpload(PrepareAttachmentUploadCommand.of(
                "owner", room.getId(), ChatMessageType.IMAGE, "시안.png", "image/png", 1024L)));
        assertCode(ErrorCode.CHAT_ROOM_NOT_FOUND, () -> service.prepareAttachmentUpload(
                PrepareAttachmentUploadCommand.of("owner", "missing", ChatMessageType.IMAGE,
                        "시안.png", "image/png", 1024L)));
        verify(uploadRepository, never()).save(any(ChatAttachmentUpload.class));
        verifyNoInteractions(storageClient);
    }

    private ChatRoom arrangeOwnerUpload() {
        owner();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", JobStatus.MATCHED)));
        return room;
    }

    private ChatRoom arrangeOwnerSend(JobStatus status) {
        owner();
        ChatRoom room = room(2L, LocalDateTime.now());
        when(roomRepository.findLockedById(room.getId())).thenReturn(Optional.of(room));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job(2L, "의뢰", status)));
        return room;
    }

    private void owner() {
        when(userService.getActiveUser("owner")).thenReturn(User.builder()
                .id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID))
                .thenReturn(Optional.of(Owner.builder().id(10L).userId(OWNER_ID).build()));
    }

    private void studentViewer() {
        when(userService.getActiveUser("student")).thenReturn(User.builder()
                .id(STUDENT_ID).role(UserRole.STUDENT).build());
        when(studentRepository.findByUserId(STUDENT_ID)).thenReturn(Optional.of(student()));
    }

    private Job job(Long id, String title, JobStatus status) {
        return Job.builder().id(id).title(title).status(status).ownerProfileId(10L)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 20L)
                .budget(300000L).revisionCount(2)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20)).build();
    }

    private Student student() {
        return Student.builder().id(20L).userId(STUDENT_ID).profileImageUrl("student.png").build();
    }

    private ChatRoom room(Long jobId, LocalDateTime createdAt) {
        ChatRoom room = ChatRoom.create(jobId);
        ReflectionTestUtils.setField(room, "createdAt", createdAt);
        return room;
    }

    private ChatMessage message(Long id, String roomId, ChatMessageType type, String content,
            String attachmentName, LocalDateTime createdAt) {
        return ChatMessage.builder().id(id).roomId(roomId).type(type).content(content)
                .attachmentName(attachmentName).createdAt(createdAt).build();
    }

    private JobSubmission submission(Long jobId, JobSubmissionType type, int revisionNumber,
            JobSubmissionReviewStatus status) {
        return JobSubmission.builder().jobId(jobId).submissionType(type)
                .revisionNumber(revisionNumber).reviewStatus(status).build();
    }

    private ChatMessageRepository.UnreadCount unread(String roomId, Long count) {
        ChatMessageRepository.UnreadCount projection = mock(ChatMessageRepository.UnreadCount.class);
        when(projection.getRoomId()).thenReturn(roomId);
        when(projection.getUnreadCount()).thenReturn(count);
        return projection;
    }

    private void assertCode(ErrorCode expected, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
