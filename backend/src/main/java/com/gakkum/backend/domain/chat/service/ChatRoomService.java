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
        getOrCreate(jobId);
    }

    /** 의뢰의 채팅방을 반환하고 없으면 만든다. 호출하는 쪽이 의뢰 행을 잠가 같은 의뢰의 생성을 순서대로 처리한다. */
    @Transactional
    public ChatRoom getOrCreate(Long jobId) {
        return chatRoomRepository.findByJobId(jobId)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.create(jobId)));
    }
}
