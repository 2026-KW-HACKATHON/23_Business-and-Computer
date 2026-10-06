package com.gakkum.backend.domain.job.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gakkum.backend.domain.proposal.entity.Proposal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class JobCommandDto {

    private JobCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetOpenJobsCommand {

        private final Long ownerProfileId;

        public static GetOpenJobsCommand of(Long ownerProfileId) {
            return GetOpenJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetMatchedJobsCommand {

        private final Long ownerProfileId;

        public static GetMatchedJobsCommand of(Long ownerProfileId) {
            return GetMatchedJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetStudentMatchedJobsCommand {

        private final Long studentProfileId;

        public static GetStudentMatchedJobsCommand of(Long studentProfileId) {
            return GetStudentMatchedJobsCommand.builder()
                    .studentProfileId(studentProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetStudentAppliedJobsCommand {

        private final Long studentProfileId;
        private final String demoSessionId;

        /** demoSessionId는 조회하는 학생의 격리 범위다. 실제 학생은 null이다. */
        public static GetStudentAppliedJobsCommand of(Long studentProfileId, String demoSessionId) {
            return GetStudentAppliedJobsCommand.builder()
                    .studentProfileId(studentProfileId)
                    .demoSessionId(demoSessionId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetClosedJobsCommand {

        private final Long ownerProfileId;

        public static GetClosedJobsCommand of(Long ownerProfileId) {
            return GetClosedJobsCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetJobApplicationsCommand {

        private final Long jobId;
        private final Long ownerProfileId;

        public static GetJobApplicationsCommand of(Long jobId, Long ownerProfileId) {
            return GetJobApplicationsCommand.builder()
                    .jobId(jobId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetJobApplicantProfileCommand {

        private final Long jobId;
        private final Long jobApplicationId;
        private final Long ownerProfileId;

        public static GetJobApplicantProfileCommand of(Long jobId, Long jobApplicationId, Long ownerProfileId) {
            return GetJobApplicantProfileCommand.builder()
                    .jobId(jobId)
                    .jobApplicationId(jobApplicationId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetJobSubmissionCommand {

        private final Long jobId;
        private final Long ownerProfileId;

        public static GetJobSubmissionCommand of(Long jobId, Long ownerProfileId) {
            return GetJobSubmissionCommand.builder()
                    .jobId(jobId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetJobResultCommand {

        private final Long jobId;
        private final Long ownerProfileId;
        private final Long studentProfileId;

        /** 의뢰한 사장님으로 결과물을 조회한다. */
        public static GetJobResultCommand ofOwner(Long jobId, Long ownerProfileId) {
            return GetJobResultCommand.builder()
                    .jobId(jobId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }

        /** 담당 학생으로 결과물을 조회한다. */
        public static GetJobResultCommand ofStudent(Long jobId, Long studentProfileId) {
            return GetJobResultCommand.builder()
                    .jobId(jobId)
                    .studentProfileId(studentProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class RequestJobSubmissionRevisionCommand {

        private final Long jobId;
        private final Long submissionId;
        private final Long ownerProfileId;

        public static RequestJobSubmissionRevisionCommand of(Long jobId, Long submissionId, Long ownerProfileId) {
            return RequestJobSubmissionRevisionCommand.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CompleteJobSubmissionCommand {

        private final Long jobId;
        private final Long submissionId;
        private final Long ownerProfileId;

        public static CompleteJobSubmissionCommand of(Long jobId, Long submissionId, Long ownerProfileId) {
            return CompleteJobSubmissionCommand.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .ownerProfileId(ownerProfileId)
                    .build();
        }
    }

    /** 결제가 승인된 제안으로 수락 대기 의뢰를 만드는 요청. 값은 모두 제안과 승인된 결제에서 서버가 정한다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateProposalJobCommand {

        private final Long ownerProfileId;
        private final Long studentProfileId;
        private final Long proposalId;
        private final String title;
        private final String description;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final String acceptanceMessage;
        private final List<Long> specialtyIds;
        private final String demoSessionId;

        /**
         * @param budget 승인된 주문의 결제 금액. 학생 희망 금액이 아니라 사장님이 결제한 작업비다
         * @param approvedDate 결제 승인 시각의 한국 날짜. 여기에 제안 기간을 더해 마감일을 확정한다
         * @param revisionCount 사장님이 결제 시 입력한 수정 횟수
         * @param acceptanceMessage 사장님이 결제 시 남긴 한마디, 없으면 null
         */
        public static CreateProposalJobCommand of(Proposal proposal, List<Long> specialtyIds, Long budget,
                LocalDate approvedDate, Integer revisionCount, String acceptanceMessage) {
            return CreateProposalJobCommand.builder()
                    .ownerProfileId(proposal.getOwnerProfileId())
                    .studentProfileId(proposal.getStudentProfileId())
                    .proposalId(proposal.getId())
                    .demoSessionId(proposal.getDemoSessionId())
                    .title(proposal.getTitle())
                    .description(proposal.toJobDescription())
                    .budget(budget)
                    .draftDeadline(proposal.draftDeadlineFrom(approvedDate))
                    .finalDeadline(proposal.finalDeadlineFrom(approvedDate))
                    .revisionCount(revisionCount)
                    .acceptanceMessage(acceptanceMessage)
                    .specialtyIds(List.copyOf(specialtyIds))
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CancelJobCommand {

        private final String username;
        private final Long jobId;
        private final String cancelReason;
        private final String messageToStudent;

        public static CancelJobCommand of(
                String username, Long jobId, String cancelReason, String messageToStudent) {
            return CancelJobCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .cancelReason(cancelReason)
                    .messageToStudent(messageToStudent)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobApplicationCommand {

        private final String username;
        private final Long jobId;
        private final String summary;
        private final String workPlan;
        private final String deliveryMethod;

        public static CreateJobApplicationCommand of(
                String username, Long jobId, String summary, String workPlan, String deliveryMethod) {
            return CreateJobApplicationCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .summary(summary)
                    .workPlan(workPlan)
                    .deliveryMethod(deliveryMethod)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareSubmissionFileUploadCommand {

        private final String username;
        private final Long jobId;
        private final JobSubmissionFileType type;
        private final String fileName;
        private final String contentType;
        private final long size;

        public static PrepareSubmissionFileUploadCommand of(
                String username, Long jobId, JobSubmissionFileType type, String fileName, String contentType,
                long size) {
            return PrepareSubmissionFileUploadCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .type(type)
                    .fileName(fileName)
                    .contentType(contentType)
                    .size(size)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobSubmissionCommand {

        private final String username;
        private final Long jobId;
        private final List<String> fileUrls;
        private final String message;

        public static CreateJobSubmissionCommand of(
                String username, Long jobId, List<String> fileUrls, String message) {
            return CreateJobSubmissionCommand.builder()
                    .username(username)
                    .jobId(jobId)
                    .fileUrls(List.copyOf(fileUrls))
                    .message(message)
                    .build();
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateJobCommand {

        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final String title;
        private final String description;
        private final Long budget;
        private final LocalDate draftDeadline;
        private final LocalDate finalDeadline;
        private final Integer revisionCount;
        private final List<String> referenceImageUrls;

        public static CreateJobCommand of(
                Long ownerProfileId,
                List<Long> specialtyIds,
                String title,
                String description,
                Long budget,
                LocalDate draftDeadline,
                LocalDate finalDeadline,
                Integer revisionCount) {
            return of(ownerProfileId, specialtyIds, title, description, budget, draftDeadline, finalDeadline,
                    revisionCount, List.of());
        }

        public static CreateJobCommand of(
                Long ownerProfileId,
                List<Long> specialtyIds,
                String title,
                String description,
                Long budget,
                LocalDate draftDeadline,
                LocalDate finalDeadline,
                Integer revisionCount,
                List<String> referenceImageUrls) {
            return CreateJobCommand.builder()
                    .ownerProfileId(ownerProfileId)
                    .specialtyIds(specialtyIds)
                    .title(title)
                    .description(description)
                    .budget(budget)
                    .draftDeadline(draftDeadline)
                    .finalDeadline(finalDeadline)
                    .revisionCount(revisionCount)
                    .referenceImageUrls(List.copyOf(referenceImageUrls))
                    .build();
        }
    }

    /** 탐색 목록의 의뢰 조회 조건. 경계 값은 이전 페이지 마지막 카드 위치이고 그 뒤의 의뢰만 limit개까지 읽는다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetExploreJobsCommand {

        private final String demoSessionId;
        private final Long specialtyCategoryId;
        private final boolean oldestFirst;
        private final LocalDateTime createdAtBound;
        private final Long idBound;
        private final int limit;

        public static GetExploreJobsCommand of(String demoSessionId, Long specialtyCategoryId, boolean oldestFirst,
                LocalDateTime createdAtBound, Long idBound, int limit) {
            return GetExploreJobsCommand.builder()
                    .demoSessionId(demoSessionId)
                    .specialtyCategoryId(specialtyCategoryId)
                    .oldestFirst(oldestFirst)
                    .createdAtBound(createdAtBound)
                    .idBound(idBound)
                    .limit(limit)
                    .build();
        }
    }
}
