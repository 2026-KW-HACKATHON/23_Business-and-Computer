package com.gakkum.backend.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gakkum.backend.domain.chat.entity.ChatRoom;

import jakarta.persistence.LockModeType;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    Optional<ChatRoom> findByJobId(Long jobId);

    List<ChatRoom> findByJobIdIn(List<Long> jobIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatRoom> findLockedById(String roomId);
}
