package com.gakkum.backend.application.home.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerClosedJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerOpenJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerReceivedProposal;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.user.entity.UserRole;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 사장님 홈 본문. 섹션이 빈 목록이면 조회에 성공했고 항목이 없는 것이고, null이면 그 섹션의 조회에 실패한 것이다.
 * 실패한 섹션도 생략하지 않고 null로 내린다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OwnerHomeResponse implements HomeResponse {

    // 도착한 결과물을 확인하지 않으면 자동으로 완료되기까지의 일수
    private static final int AUTO_COMPLETE_DAYS = 7;

    private final UserRole role;
    // 활동 이력이 하나도 없으면 true. 이력을 확인하지 못했고 일부 조회가 실패했으면 null
    private final Boolean firstVisit;
    private final List<Todo> todos;
    private final List<Working> working;
    private final List<Waiting> waiting;
    private final List<Done> done;

    public static OwnerHomeResponse of(Boolean firstVisit, List<Todo> todos, List<Working> working,
            List<Waiting> waiting, List<Done> done) {
        return new OwnerHomeResponse(UserRole.OWNER, firstVisit, todos, working, waiting, done);
    }

    /** 확인할 일. type에 따라 필요한 값만 채우고 나머지는 내리지 않는다. */
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
        // 첫 대분류 이름
        private final String field;
        private final Student student;
        // draftArrived: 도착한 결과물이 수정안인지와 자동 완료 예정일
        private final Boolean revision;
        private final LocalDate autoCompleteOn;
        // proposalArrived
        private final Integer likeCount;
        // applicants
        private final Integer applicantCount;
        private final LocalDate draftDeadline;

        /** 검토를 기다리는 결과물이 도착한 진행 중 작업 */
        public static Todo draftArrived(OwnerProgressJob source) {
            Job job = source.getJob();
            JobSubmission submission = source.getPendingSubmission();
            return Todo.builder()
                    .type("draftArrived")
                    .kind(HomeResponse.kindOf(job.getProposalId()))
                    .jobId(job.getId())
                    .proposalId(job.getProposalId())
                    .title(job.getTitle())
                    .field(source.getField())
                    .student(new Student(source.getStudentName(), source.getStudentMajor()))
                    .revision(submission.getSubmissionType() == JobSubmissionType.REVISION)
                    .autoCompleteOn(HomeResponse.koreaDate(submission.getCreatedAt()).plusDays(AUTO_COMPLETE_DAYS))
                    .build();
        }

        /** 결정을 기다리는 받은 제안 */
        public static Todo proposalArrived(OwnerReceivedProposal source) {
            Proposal proposal = source.getProposal();
            return Todo.builder()
                    .type("proposalArrived")
                    .kind(KIND_PROPOSAL)
                    .proposalId(proposal.getId())
                    .title(proposal.getTitle())
                    .field(source.getField())
                    .student(new Student(source.getStudentName(), source.getStudentMajor()))
                    .likeCount(proposal.getLikeCount())
                    .build();
        }

        /** 지원자가 생긴 모집 중 의뢰 */
        public static Todo applicants(OwnerOpenJob source) {
            Job job = source.getJob();
            return Todo.builder()
                    .type("applicants")
                    .kind(KIND_REQUEST)
                    .jobId(job.getId())
                    .title(job.getTitle())
                    .field(source.getField())
                    .applicantCount(source.getApplicantCount())
                    .draftDeadline(job.getDraftDeadline())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Student {

        private final String name;
        private final String major;
    }

    /** 학생이 초안·수정안을 만들고 있는 작업. stage·due는 지금 지킬 마감이다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Working {

        private final Long jobId;
        private final String kind;
        private final Long proposalId;
        private final String title;
        private final String studentName;
        private final String stage;
        private final LocalDate due;

        public static Working of(OwnerProgressJob source, String stage, LocalDate due) {
            Job job = source.getJob();
            return new Working(job.getId(), HomeResponse.kindOf(job.getProposalId()), job.getProposalId(),
                    job.getTitle(), source.getStudentName(), stage, due);
        }
    }

    /** 지원자를 기다리는 모집 중 의뢰 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Waiting {

        private final Long jobId;
        private final String kind;
        private final String title;
        private final LocalDate draftDeadline;

        public static Waiting from(OwnerOpenJob source) {
            Job job = source.getJob();
            return new Waiting(job.getId(), KIND_REQUEST, job.getTitle(), job.getDraftDeadline());
        }
    }

    /** 완료한 작업 */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Done {

        private final Long jobId;
        private final String kind;
        private final Long proposalId;
        private final String title;
        private final String studentName;
        private final LocalDate completedOn;

        public static Done from(OwnerClosedJob source) {
            Job job = source.getJob();
            return new Done(job.getId(), HomeResponse.kindOf(job.getProposalId()), job.getProposalId(),
                    job.getTitle(), source.getStudentName(), HomeResponse.koreaDate(job.getCompletedAt()));
        }
    }
}
