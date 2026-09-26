package com.gakkum.backend.domain.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;

class ChatRoomServiceTest {

    private final ChatRoomRepository repository = mock(ChatRoomRepository.class);
    private final ChatRoomService service = new ChatRoomService(repository);

    @Test
    @DisplayName("결제된 의뢰에 채팅방이 없으면 하나를 생성한다")
    void createsRoomWhenAbsent() {
        when(repository.findByJobId(11L)).thenReturn(Optional.empty());

        service.createIfAbsent(11L);

        verify(repository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("같은 의뢰의 채팅방이 이미 있으면 다시 생성하지 않는다")
    void doesNotCreateDuplicateRoom() {
        when(repository.findByJobId(11L)).thenReturn(Optional.of(ChatRoom.create(11L)));

        service.createIfAbsent(11L);

        verify(repository, never()).save(any(ChatRoom.class));
    }
}
