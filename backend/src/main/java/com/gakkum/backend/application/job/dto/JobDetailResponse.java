package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gakkum.backend.application.job.dto.JobListResponse.SpecialtyCategory;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.WorkHistoryResult;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobDetailResponse {

    private JobDetailResponse() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Detail {

        private final Long id;
        private final String title;
        private final String description;
        private final List<String> referenceImageUrls;
        private final Long budget;
        private final List<SpecialtyCategory> specialtyCategories;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final JobProgressStage progressStage;
        private final String status;
        // 학생 본인의 지원서 상태. 지원 이력이 없거나 학생이 아닌 사용자에게는 필드를 내리지 않는다
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final JobApplicationStatus applied;
        private final String storeName;
        // 주소를 등록하지 않은 매장은 null
        private final String storeAddress;
        // 아래 취소 정보는 취소된 의뢰의 사장님·선정 학생에게만 내리고, 그 외에는 모두 null
        private final String cancelledBy;
        private final String cancelReason;
        private final String messageToStudent;
        private final Long refundAmount;
        private final Long studentCompensationAmount;
        private final LocalDateTime cancelledAt;

        public static Detail from(JobDetailResult result) {
            return Detail.builder()
                    .id(result.getId())
                    .title(result.getTitle())
                    .description(result.getDescription())
                    .referenceImageUrls(result.getReferenceImageUrls())
                    .budget(result.getBudget())
                    .specialtyCategories(result.getSpecialtyCategories().stream()
                            .map(SpecialtyCategory::from)
                            .toList())
                    .draftDeadline(result.getDraftDeadline())
                    .finalDeadline(result.getFinalDeadline())
                    .revisionCount(result.getRevisionCount())
                    .progressStage(result.getProgressStage())
                    .status(result.getStatus())
                    .applied(result.getApplied())
                    .storeName(result.getStoreName())
                    .storeAddress(result.getStoreAddress())
                    .cancelledBy(result.getCancelledBy())
                    .cancelReason(result.getCancelReason())
                    .messageToStudent(result.getMessageToStudent())
                    .refundAmount(result.getRefundAmount())
                    .studentCompensationAmount(result.getStudentCompensationAmount())
                    .cancelledAt(result.getCancelledAt())
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Result {

        private final Long jobId;
        private final String title;
        private final String studentName;
        private final LocalDate completedAt;
        private final boolean normalCompleted;
        private final Long workFee;
        private final List<String> fileUrls;
        private final List<JobSubmissionResponse.File> files;
        private final String message;
        private final List<WorkHistory> workHistory;

        public static Result from(JobResultResult result) {
            return Result.builder()
                    .jobId(result.getJobId())
                    .title(result.getTitle())
                    .studentName(result.getStudentName())
                    .completedAt(result.getCompletedAt())
                    .normalCompleted(result.isNormalCompleted())
                    .workFee(result.getWorkFee())
                    .fileUrls(result.getFileUrls())
                    .files(JobSubmissionResponse.File.listFrom(result.getFiles()))
                    .message(result.getMessage())
                    .workHistory(result.getWorkHistory().stream()
                            .map(WorkHistory::from)
                            .toList())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class WorkHistory {

        private final String type;
        private final LocalDate date;

        public static WorkHistory from(WorkHistoryResult result) {
            return new WorkHistory(result.getType().name(), result.getDate());
        }
    }
}
