package com.gakkum.backend.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRoomReadPositionTest {

    @Test
    @DisplayName("사장님 읽음 위치만 갱신하고 학생 읽음 위치는 그대로 둔다")
    void marksOwnerReadPosition() {
        ChatRoom room = ChatRoom.create(42L);

        room.markRead(true, 5L);

        assertThat(room.getOwnerLastReadMessageId()).isEqualTo(5L);
        assertThat(room.getStudentLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("학생 읽음 위치만 갱신하고 사장님 읽음 위치는 그대로 둔다")
    void marksStudentReadPosition() {
        ChatRoom room = ChatRoom.create(42L);

        room.markRead(false, 7L);

        assertThat(room.getStudentLastReadMessageId()).isEqualTo(7L);
        assertThat(room.getOwnerLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("읽음 위치는 앞으로만 이동하고 이전 메시지 ID로는 되돌리지 않는다")
    void neverMovesReadPositionBackward() {
        ChatRoom room = ChatRoom.create(42L);

        room.markRead(true, 5L);
        room.markRead(true, 3L);
        room.markRead(false, 9L);
        room.markRead(false, 9L);
        room.markRead(false, 2L);

        assertThat(room.getOwnerLastReadMessageId()).isEqualTo(5L);
        assertThat(room.getStudentLastReadMessageId()).isEqualTo(9L);
    }
}
