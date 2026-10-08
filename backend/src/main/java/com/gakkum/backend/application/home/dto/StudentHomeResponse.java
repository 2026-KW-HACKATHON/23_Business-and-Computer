package com.gakkum.backend.application.home.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentApplication;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSentProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettledJob;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.user.entity.UserRole;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 학생 홈 본문. 섹션이 빈 목록이면 조회에 성공했고 항목이 없는 것이고, null이면 그 섹션의 조회에 실패한 것이다.
 * 실패한 섹션도 생략하지 않고 null로 내린다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class StudentHomeResponse implements HomeResponse {

    private final UserRole role;
    // 활동 이력이 하나도 없으면 true. 이력을 확인하지 못했고 일부 조회가 실패했으면 null
    private final Boolean firstVisit;
    private final List<Todo> todos;
    private final List<Checking> checking;
    private final List<Waiting> waiting;
    private final List<PeerProposal> peerProposals;
    private final List<Done> done;

    public static StudentHomeResponse of(Boolean firstVisit, List<Todo> todos, List<Checking> checking,
            List<Waiting> waiting, List<PeerProposal> peerProposals, List<Done> done) {
        return new StudentHomeResponse(UserRole.STUDENT, firstVisit, todos, checking, waiting, peerProposals, done);
    }

    /** 확인할 일. stage·due는 지금 지킬 마감이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Todo {

        private final String type;
        private final String kind;
        private final Long jobId;
        private final Long proposalId;
        private final String title;
        private final String storeName;
        // 겹치지 않는 대분류 이름
        private final List<String> categories;
        private final String stage;
        private final LocalDate due;

        /** 사장님이 결제해 의뢰서가 온 내 제안. 마감은 의뢰서의 초안 마감이다. */
        public static Todo proposalAgreement(StudentSentProposal source) {
            Proposal proposal = source.getProposal();
            Job job = source.getJob();
            return Todo.builder()
                    .type("proposalAgreement")
                    .kind(KIND_PROPOSAL)
                    .jobId(job == null ? null : job.getId())
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .storeName(source.getStoreName())
                    .categories(source.getCategories())
                    .stage(job == null ? null : STAGE_DRAFT)
                    .due(job == null ? null : job.getDraftDeadline())
                    .build();
        }

        /** 초안(drafting) 또는 수정안(revising)을 만들 차례인 작업 */
        public static Todo work(StudentProgressJob source, String type, String stage, LocalDate due) {
            Job job = source.getJob();
            return Todo.builder()
                    .type(type)
                    .kind(HomeResponse.kindOf(job.getProposalId()))
                    .jobId(job.getId())
                    .proposalId(job.getProposalId())
                    .title(job.getTitle())
                    .storeName(source.getStoreName())
                    .categories(source.getCategories())
                    .stage(stage)
                    .due(due)
                    .build();
        }
    }

    /** 결과물을 내고 사장님 확인을 기다리는 작업 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Checking {

        private final Long jobId;
        private final String kind;
        private final Long proposalId;
        private final String title;
        private final String storeName;
        private final JobSubmissionType submissionType;
        private final LocalDate submittedOn;

        public static Checking from(StudentProgressJob source) {
            Job job = source.getJob();
            JobSubmission submission = source.getLatestSubmission();
            return new Checking(job.getId(), HomeResponse.kindOf(job.getProposalId()), job.getProposalId(),
                    job.getTitle(), source.getStoreName(), submission.getSubmissionType(),
                    HomeResponse.koreaDate(submission.getCreatedAt()));
        }
    }

    /** 사장님의 결정을 기다리는 보낸 제안(proposal) 또는 지원(application) */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Waiting {

        private final String type;
        private final Long proposalId;
        private final Long jobId;
        private final Long jobApplicationId;
        private final String title;
        private final String storeName;
        // proposal
        private final Integer likeCount;
        // application
        private final LocalDate draftDeadline;

        public static Waiting proposal(StudentSentProposal source) {
            Proposal proposal = source.getProposal();
            return Waiting.builder()
                    .type("proposal")
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .storeName(source.getStoreName())
                    .likeCount(proposal.getLikeCount())
                    .build();
        }

        public static Waiting application(StudentApplication source) {
            Job job = source.getJob();
            return Waiting.builder()
                    .type("application")
                    .jobId(job.getId())
                    .jobApplicationId(source.getApplication().getId())
                    .title(job.getTitle())
                    .storeName(source.getStoreName())
                    .draftDeadline(job.getDraftDeadline())
                    .build();
        }
    }

    /** 공감 많은 다른 학생의 제안 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PeerProposal {

        private final Long proposalId;
        private final String title;
        private final String studentName;
        private final String storeName;
        private final ProposalStatus status;
        private final Integer likeCount;
        private final boolean likedByMe;

        public static PeerProposal from(HomeQueryDto.PeerProposal source) {
            Proposal proposal = source.getProposal();
            return new PeerProposal(proposal.getId(), proposal.getTitle(), source.getStudentName(),
                    source.getStoreName(), proposal.getStatus(), proposal.getLikeCount(), source.isLikedByMe());
        }
    }

    /** 정산까지 끝난 작업 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Done {

        private final Long jobId;
        private final String kind;
        private final Long proposalId;
        private final String title;
        private final String storeName;
        private final LocalDate completedOn;

        public static Done from(StudentSettledJob source) {
            return new Done(source.getJobId(), HomeResponse.kindOf(source.getProposalId()), source.getProposalId(),
                    source.getTitle(), source.getStoreName(), source.getCompletedOn());
        }
    }
}
