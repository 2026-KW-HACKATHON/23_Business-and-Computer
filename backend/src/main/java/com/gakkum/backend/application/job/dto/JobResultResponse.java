package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.WorkHistoryResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobResultResponse {

    private final Long jobId;
    private final String title;
    private final String studentName;
    private final LocalDate completedAt;
    private final boolean normalCompleted;
    private final Long workFee;
    private final List<String> fileUrls;
    private final String message;
    private final List<WorkHistoryResponse> workHistory;

    public static JobResultResponse from(JobResultResult result) {
        return JobResultResponse.builder()
                .jobId(result.getJobId())
                .title(result.getTitle())
                .studentName(result.getStudentName())
                .completedAt(result.getCompletedAt())
                .normalCompleted(result.isNormalCompleted())
                .workFee(result.getWorkFee())
                .fileUrls(result.getFileUrls())
                .message(result.getMessage())
                .workHistory(result.getWorkHistory().stream()
                        .map(WorkHistoryResponse::from)
                        .toList())
                .build();
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class WorkHistoryResponse {

        private final String type;
        private final LocalDate date;

        public static WorkHistoryResponse from(WorkHistoryResult result) {
            return new WorkHistoryResponse(result.getType().name(), result.getDate());
        }
    }
}
