package com.gakkum.backend.application.home.dto;

import java.time.LocalDate;
import java.util.List;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.proposal.entity.Proposal;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 홈 집계가 원천 조회 하나에서 읽어 여러 섹션에 나눠 쓰는 값. 섹션 분류와 정렬은 HomeFacade가 한다. */
public final class HomeQueryDto {

    private HomeQueryDto() {
    }

    /** 사장님의 모집 중 의뢰와 대기 중 지원자 수 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OwnerOpenJob {

        private final Job job;
        private final int applicantCount;
        // 첫 대분류 이름. 특기가 없으면 null
        private final String field;

        public static OwnerOpenJob of(Job job, int applicantCount, String field) {
            return new OwnerOpenJob(job, applicantCount, field);
        }
    }

    /** 사장님이 받은 제안(취소 제외)과 제안한 학생의 현재 프로필 값 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OwnerReceivedProposal {

        private final Proposal proposal;
        private final String studentName;
        private final String studentMajor;
        private final String field;

        public static OwnerReceivedProposal of(Proposal proposal, String studentName, String studentMajor,
                String field) {
            return new OwnerReceivedProposal(proposal, studentName, studentMajor, field);
        }
    }

    /** 사장님의 진행 중(MATCHED) 의뢰. pendingSubmission은 검토 대기 제출물이고 없으면 null */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OwnerProgressJob {

        private final Job job;
        private final JobSubmission pendingSubmission;
        private final JobProgressStage progressStage;
        private final String studentName;
        private final String studentMajor;
        private final String field;

        public static OwnerProgressJob of(Job job, JobSubmission pendingSubmission, JobProgressStage progressStage,
                String studentName, String studentMajor, String field) {
            return new OwnerProgressJob(job, pendingSubmission, progressStage, studentName, studentMajor, field);
        }
    }

    /** 사장님의 끝난 의뢰(완료·취소). 모집 중에 취소한 의뢰는 studentName이 null */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OwnerClosedJob {

        private final Job job;
        private final String studentName;

        public static OwnerClosedJob of(Job job, String studentName) {
            return new OwnerClosedJob(job, studentName);
        }
    }

    /** 학생이 보낸 제안(취소 제외). job은 결제로 만들어진 의뢰이고 결제 전이면 null */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentSentProposal {

        private final Proposal proposal;
        private final String storeName;
        private final List<String> categories;
        private final Job job;

        public static StudentSentProposal of(Proposal proposal, String storeName, List<String> categories, Job job) {
            return new StudentSentProposal(proposal, storeName, List.copyOf(categories), job);
        }
    }

    /** 학생의 대기·미선정 지원. pending은 목록에 내리는 지원 상태가 대기(PENDING)인지다 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentApplication {

        private final Job job;
        private final JobApplication application;
        private final boolean pending;
        private final String storeName;

        public static StudentApplication of(Job job, JobApplication application, boolean pending, String storeName) {
            return new StudentApplication(job, application, pending, storeName);
        }
    }

    /** 학생과 매칭된 진행 중(MATCHED) 의뢰. latestSubmission은 제출물이 없으면 null */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentProgressJob {

        private final Job job;
        private final JobSubmission latestSubmission;
        private final String storeName;
        private final List<String> categories;

        public static StudentProgressJob of(Job job, JobSubmission latestSubmission, String storeName,
                List<String> categories) {
            return new StudentProgressJob(job, latestSubmission, storeName, List.copyOf(categories));
        }
    }

    /** 학생 정산 내역에서 읽은 값. settledJobs는 정산 완료(SETTLED)만 담는다 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentSettlements {

        // 정산 내역이 한 줄이라도 있는지(정산 예정·착수 보상·환불 포함)
        private final boolean hasHistory;
        private final List<StudentSettledJob> settledJobs;

        public static StudentSettlements of(boolean hasHistory, List<StudentSettledJob> settledJobs) {
            return new StudentSettlements(hasHistory, List.copyOf(settledJobs));
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StudentSettledJob {

        private final Long jobId;
        // 의뢰를 만든 제안. 일반 의뢰는 null
        private final Long proposalId;
        private final String title;
        private final String storeName;
        private final LocalDate completedOn;

        public static StudentSettledJob of(Long jobId, Long proposalId, String title, String storeName,
                LocalDate completedOn) {
            return new StudentSettledJob(jobId, proposalId, title, storeName, completedOn);
        }
    }

    /** 공감 많은 다른 학생의 제안 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PeerProposal {

        private final Proposal proposal;
        private final String studentName;
        private final String storeName;
        private final boolean likedByMe;

        public static PeerProposal of(Proposal proposal, String studentName, String storeName, boolean likedByMe) {
            return new PeerProposal(proposal, studentName, storeName, likedByMe);
        }
    }
}
