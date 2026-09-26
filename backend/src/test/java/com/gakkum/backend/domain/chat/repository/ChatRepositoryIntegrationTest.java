package com.gakkum.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.chat.entity.ChatMessage;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.user.entity.UserRole;

@SpringBootTest
@Transactional
class ChatRepositoryIntegrationTest {

    private static final String OWNER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    @Autowired
    private ChatRoomRepository roomRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Test
    @DisplayName("PostgreSQL에서 최신 메시지와 상대방의 안 읽은 메시지를 방별로 집계한다")
    void queriesLatestAndUnreadMessages() {
        ChatRoom room = roomRepository.saveAndFlush(ChatRoom.create(900001L));
        ChatMessage first = saveMessage(room.getId(), STUDENT_ID, "첫 메시지");
        saveMessage(room.getId(), OWNER_ID, "내 답장");
        ChatMessage latest = saveMessage(room.getId(), STUDENT_ID, "최근 메시지");

        assertThat(messageRepository.findLatestByRoomIds(List.of(room.getId())))
                .extracting(ChatMessage::getId).containsExactly(latest.getId());
        assertThat(messageRepository.countUnreadByRoomIds(List.of(room.getId()), OWNER_ID, true))
                .singleElement().satisfies(count -> {
                    assertThat(count.getRoomId()).isEqualTo(room.getId());
                    assertThat(count.getUnreadCount()).isEqualTo(2L);
                });
        assertThat(messageRepository.countUnreadByRoomIds(List.of(room.getId()), STUDENT_ID, false))
                .singleElement().extracting(ChatMessageRepository.UnreadCount::getUnreadCount).isEqualTo(1L);

        room.markRead(UserRole.OWNER, first.getId());
        roomRepository.flush();

        assertThat(messageRepository.countUnreadByRoomIds(List.of(room.getId()), OWNER_ID, true))
                .singleElement().extracting(ChatMessageRepository.UnreadCount::getUnreadCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("PostgreSQL은 같은 의뢰의 두 번째 채팅방을 거부한다")
    void rejectsSecondRoomForSameJob() {
        roomRepository.saveAndFlush(ChatRoom.create(900002L));

        assertThatThrownBy(() -> roomRepository.saveAndFlush(ChatRoom.create(900002L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ChatMessage saveMessage(String roomId, String senderId, String content) {
        return messageRepository.saveAndFlush(ChatMessage.builder()
                .roomId(roomId)
                .senderUserId(senderId)
                .type(ChatMessageType.TEXT)
                .content(content)
                .build());
    }
}
