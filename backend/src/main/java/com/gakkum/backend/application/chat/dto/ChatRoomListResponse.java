package com.gakkum.backend.application.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.chat.entity.ChatMessageType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomListResponse {

    private final int count;
    private final List<Room> rooms;

    public static ChatRoomListResponse of(List<Room> rooms) {
        return ChatRoomListResponse.builder().count(rooms.size()).rooms(rooms).build();
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Room {
        private final String roomId;
        private final Long jobId;
        private final String jobTitle;
        private final String counterpartName;
        private final String counterpartProfileImageUrl;
        private final LastMessage lastMessage;
        private final long unreadCount;

        public static Room of(String roomId, Long jobId, String jobTitle, String counterpartName,
                String counterpartProfileImageUrl, LastMessage lastMessage, long unreadCount) {
            return Room.builder()
                    .roomId(roomId)
                    .jobId(jobId)
                    .jobTitle(jobTitle)
                    .counterpartName(counterpartName)
                    .counterpartProfileImageUrl(counterpartProfileImageUrl)
                    .lastMessage(lastMessage)
                    .unreadCount(unreadCount)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class LastMessage {
        private final ChatMessageType type;
        private final String preview;
        private final LocalDateTime createdAt;

        public static LastMessage of(ChatMessageType type, String preview, LocalDateTime createdAt) {
            return LastMessage.builder().type(type).preview(preview).createdAt(createdAt).build();
        }
    }
}
