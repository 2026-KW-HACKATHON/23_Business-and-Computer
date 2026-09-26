package com.gakkum.backend.application.chat.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;

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
        private final DeadlineType deadlineType;
        private final LocalDate deadlineDate;
        private final JobSubmissionReviewStatus submissionReviewStatus;
        private final Long budget;
        private final Integer revisionCount;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final String applicationContent;
        private final LastMessage lastMessage;
        private final long unreadCount;

        public static Room of(ChatRoom room, Job job, String counterpartName, String counterpartProfileImageUrl,
                LastMessage lastMessage, long unreadCount, DeadlineType deadlineType, LocalDate deadlineDate,
                JobSubmissionReviewStatus submissionReviewStatus, String applicationContent) {
            return Room.builder()
                    .roomId(room.getId())
                    .jobId(job.getId())
                    .jobTitle(job.getTitle())
                    .counterpartName(counterpartName)
                    .counterpartProfileImageUrl(counterpartProfileImageUrl)
                    .deadlineType(deadlineType)
                    .deadlineDate(deadlineDate)
                    .submissionReviewStatus(submissionReviewStatus)
                    .budget(job.getBudget())
                    .revisionCount(job.getRevisionCount())
                    .draftDeadline(job.getDraftDeadline())
                    .finalDeadline(job.getFinalDeadline())
                    .applicationContent(applicationContent)
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
