package com.gakkum.backend.domain.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.repository.ChatRoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public void createIfAbsent(Long jobId) {
        if (chatRoomRepository.findByJobId(jobId).isEmpty()) {
            chatRoomRepository.save(ChatRoom.create(jobId));
        }
    }
}
