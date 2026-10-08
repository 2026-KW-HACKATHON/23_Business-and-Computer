package com.gakkum.backend.application.chat.dto;

import java.util.List;

import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;
import com.gakkum.backend.application.job.dto.JobSubmissionResponse;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionHistoryResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅 작업 카드의 작업 이력 화면이 요청 하나로 그려지도록 방 정보와 제출물 이력을 함께 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatWorkHistoryResponse {

    private final Room room;
    /** 수정 번호 오름차순. 제출물이 없으면 빈 배열 */
    private final List<JobSubmissionResponse.Latest> submissions;
    /** 이 작업에 사장님 후기가 등록됐는지 */
    private final boolean reviewed;

    public static ChatWorkHistoryResponse of(Room room, JobSubmissionHistoryResult submissions, boolean reviewed) {
        return new ChatWorkHistoryResponse(room, submissions.getSubmissions().stream()
                .map(JobSubmissionResponse.Latest::from)
                .toList(), reviewed);
    }
}
