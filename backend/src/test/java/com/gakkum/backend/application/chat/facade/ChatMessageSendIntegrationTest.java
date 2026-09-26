package com.gakkum.backend.application.chat.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse;
import com.gakkum.backend.domain.chat.dto.ChatCommandDto.SendTextMessageCommand;
import com.gakkum.backend.domain.chat.dto.ChatQueryDto.SendMessageResult;
import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.repository.ChatMessageRepository;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

@SpringBootTest
class ChatMessageSendIntegrationTest {

    private static final long JOB_ID = 900004L;
    private static final String OWNER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    @Autowired
    private ChatFacade chatFacade;

    @Autowired
    private ChatRoomRepository roomRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private OwnerRepository ownerRepository;

    @MockitoBean
    private StudentRepository studentRepository;

    private ChatRoom room;

    @BeforeEach
    void setUp() {
        room = roomRepository.saveAndFlush(ChatRoom.create(JOB_ID));
        Job job = Job.builder().id(JOB_ID).title("완료된 의뢰").ownerProfileId(10L)
                .selectedStudentProfileId(20L).status(JobStatus.CLOSED).build();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(userService.getActiveUser("owner")).thenReturn(User.builder().id(OWNER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(OWNER_ID)).thenReturn(Optional.of(Owner.builder().id(10L).build()));

        when(userService.getActiveUser("student"))
                .thenReturn(User.builder().id(STUDENT_ID).role(UserRole.STUDENT).build());
        when(studentRepository.findByUserId(STUDENT_ID))
                .thenReturn(Optional.of(Student.builder().id(20L).userId(STUDENT_ID).build()));
        when(jobRepository.findBySelectedStudentProfileId(20L)).thenReturn(List.of(job));
        when(ownerRepository.findAllById(anyList()))
                .thenReturn(List.of(Owner.builder().id(10L).storeName("매장").build()));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM chat_messages WHERE room_id = ?", room.getId());
        roomRepository.deleteById(room.getId());
    }

    @Test
    @DisplayName("동시 재요청은 메시지 하나만 저장하고 채팅방 목록에 최신 메시지와 안 읽은 개수를 반영한다")
    void concurrentRetryStoresOneMessage() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        SendTextMessageCommand command = SendTextMessageCommand.of(
                "owner", room.getId(), clientMessageId, "안녕하세요");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SendMessageResult> first = executor.submit(() -> {
                start.await();
                return chatFacade.sendTextMessage(command);
            });
            Future<SendMessageResult> second = executor.submit(() -> {
                start.await();
                return chatFacade.sendTextMessage(command);
            });
            start.countDown();

            SendMessageResult one = first.get(10, TimeUnit.SECONDS);
            SendMessageResult two = second.get(10, TimeUnit.SECONDS);
            assertThat(one.getMessage().getId()).isEqualTo(two.getMessage().getId());
            assertThat(List.of(one.isCreated(), two.isCreated())).containsExactlyInAnyOrder(true, false);
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_messages WHERE room_id = ?", Long.class, room.getId());
            assertThat(count).isEqualTo(1L);

            ChatRoomListResponse studentList = chatFacade.getMyChatRooms("student");
            assertThat(studentList.getCount()).isEqualTo(1);
            assertThat(studentList.getRooms().get(0).getLastMessage().getPreview()).isEqualTo("안녕하세요");
            assertThat(studentList.getRooms().get(0).getUnreadCount()).isEqualTo(1L);
            ChatMessage latest = messageRepository.findLatestByRoomIds(List.of(room.getId())).get(0);
            assertThat(latest.getClientMessageId()).isEqualTo(clientMessageId);
        } finally {
            executor.shutdownNow();
        }
    }
}
