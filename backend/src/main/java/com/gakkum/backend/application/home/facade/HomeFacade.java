package com.gakkum.backend.application.home.facade;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerClosedJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerOpenJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerReceivedProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.PeerProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentApplication;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentProgressJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSentProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettledJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettlements;
import com.gakkum.backend.application.home.dto.HomeResponse;
import com.gakkum.backend.application.home.dto.OwnerHomeResponse;
import com.gakkum.backend.application.home.dto.StudentHomeResponse;
import com.gakkum.backend.application.home.service.HomeQueryService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 홈 본문 집계. 원천 데이터를 요청당 한 번씩 읽어 여러 섹션에 나눠 쓴다.
 * 전체를 한 트랜잭션으로 묶지 않고 원천 조회마다 HomeQueryService의 독립된 트랜잭션을 쓰며,
 * 실패한 원천에 의존하는 섹션만 null로 내린다. 사용자·역할·프로필 확인은 부분 실패로 숨기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeFacade {

    private static final String DRAFTING = "drafting";
    private static final String REVISING = "revising";

    private final UserService userService;
    private final OwnerService ownerService;
    private final StudentService studentService;
    private final HomeQueryService homeQueryService;

    /** 로그인 사용자의 역할에 맞는 홈 본문. 가입을 끝내지 않은 사용자는 403으로 거부한다. */
    public HomeResponse getHome(String username) {
        User user = userService.getActiveUser(username);
        return switch (user.getRole()) {
            case OWNER -> getOwnerHome(user);
            case STUDENT -> getStudentHome(user);
            case PENDING -> throw new BusinessException(ErrorCode.HOME_ROLE_REQUIRED);
        };
    }

    private OwnerHomeResponse getOwnerHome(User user) {
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Long ownerProfileId = owner.getId();
        List<OwnerOpenJob> open = load("owner.openJobs", ownerProfileId,
                () -> homeQueryService.getOwnerOpenJobs(ownerProfileId));
        List<OwnerReceivedProposal> proposals = load("owner.receivedProposals", ownerProfileId,
                () -> homeQueryService.getOwnerReceivedProposals(ownerProfileId));
        List<OwnerProgressJob> progress = load("owner.progressJobs", ownerProfileId,
                () -> homeQueryService.getOwnerProgressJobs(ownerProfileId));
        List<OwnerClosedJob> closed = load("owner.closedJobs", ownerProfileId,
                () -> homeQueryService.getOwnerClosedJobs(ownerProfileId));

        // 지금 지킬 마감이 빠른 것부터, 모집 중 의뢰는 초안 마감이 빠른 것부터. 같은 날이면 목록 순서를 지킨다
        List<OwnerProgressJob> progressByDue = progress == null ? null : progress.stream()
                .sorted(Comparator.comparing(HomeFacade::ownerDue))
                .toList();
        List<OwnerOpenJob> openByDraftDeadline = open == null ? null : open.stream()
                .sorted(Comparator.comparing(job -> job.getJob().getDraftDeadline()))
                .toList();

        List<OwnerHomeResponse.Todo> todos = null;
        if (progressByDue != null && proposals != null && openByDraftDeadline != null) {
            // 도착한 결과물 → 새 제안 → 지원자가 생긴 의뢰
            todos = Stream.of(
                            progressByDue.stream()
                                    .filter(job -> job.getPendingSubmission() != null)
                                    .map(OwnerHomeResponse.Todo::draftArrived),
                            proposals.stream()
                                    .filter(proposal -> proposal.getProposal().getStatus() == ProposalStatus.PENDING)
                                    .map(OwnerHomeResponse.Todo::proposalArrived),
                            openByDraftDeadline.stream()
                                    .filter(job -> job.getApplicantCount() > 0)
                                    .map(OwnerHomeResponse.Todo::applicants))
                    .flatMap(stream -> stream)
                    .toList();
        }
        List<OwnerHomeResponse.Working> working = progressByDue == null ? null : progressByDue.stream()
                .filter(job -> job.getPendingSubmission() == null)
                .map(job -> OwnerHomeResponse.Working.of(job, ownerDueStage(job), ownerDue(job)))
                .toList();
        List<OwnerHomeResponse.Waiting> waiting = openByDraftDeadline == null ? null : openByDraftDeadline.stream()
                .filter(job -> job.getApplicantCount() == 0)
                .map(OwnerHomeResponse.Waiting::from)
                .toList();
        // 끝난 의뢰는 이미 종료 최신순이다
        List<OwnerHomeResponse.Done> done = closed == null ? null : closed.stream()
                .filter(job -> job.getJob().getStatus() == JobStatus.CLOSED)
                .map(OwnerHomeResponse.Done::from)
                .toList();

        requireAnySection(ownerProfileId, todos, working, waiting, done);
        return OwnerHomeResponse.of(firstVisit(open, proposals, progress, closed), todos, working, waiting, done);
    }

    private StudentHomeResponse getStudentHome(User user) {
        // 학생 계정에 학생 프로필이 없으면 기존 목록 API와 같이 데이터 오류(500)다
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        Long studentProfileId = student.getId();
        List<StudentSentProposal> proposals = load("student.sentProposals", studentProfileId,
                () -> homeQueryService.getStudentSentProposals(studentProfileId));
        List<StudentApplication> applications = load("student.applications", studentProfileId,
                () -> homeQueryService.getStudentApplications(studentProfileId, user.getDemoSessionId()));
        List<StudentProgressJob> progress = load("student.progressJobs", studentProfileId,
                () -> homeQueryService.getStudentProgressJobs(studentProfileId));
        StudentSettlements settlements = load("student.settlements", studentProfileId,
                () -> homeQueryService.getStudentSettlements(user.getUsername()));
        List<PeerProposal> peers = load("student.peerProposals", studentProfileId,
                () -> homeQueryService.getPeerProposals(studentProfileId, user.getDemoSessionId()));

        List<StudentHomeResponse.Todo> todos = null;
        if (proposals != null && progress != null) {
            // 의뢰서가 온 제안이 먼저, 그다음 초안·수정안 차례인 작업을 마감이 빠른 것부터
            todos = Stream.concat(
                            proposals.stream()
                                    .filter(HomeFacade::awaitsAgreement)
                                    .map(StudentHomeResponse.Todo::proposalAgreement),
                            progress.stream()
                                    .filter(job -> !isSubmitted(job))
                                    .sorted(Comparator.comparing(HomeFacade::studentDue))
                                    .map(job -> StudentHomeResponse.Todo.work(job,
                                            isRevising(job) ? REVISING : DRAFTING, studentDueStage(job),
                                            studentDue(job))))
                    .toList();
        }
        List<StudentHomeResponse.Checking> checking = progress == null ? null : progress.stream()
                .filter(HomeFacade::isSubmitted)
                .map(StudentHomeResponse.Checking::from)
                .toList();
        List<StudentHomeResponse.Waiting> waiting = null;
        if (proposals != null && applications != null) {
            waiting = Stream.concat(
                            proposals.stream()
                                    .filter(proposal -> proposal.getProposal().getStatus() == ProposalStatus.PENDING)
                                    .map(StudentHomeResponse.Waiting::proposal),
                            applications.stream()
                                    .filter(StudentApplication::isPending)
                                    .map(StudentHomeResponse.Waiting::application))
                    .toList();
        }
        List<StudentHomeResponse.PeerProposal> peerProposals = peers == null ? null : peers.stream()
                .map(StudentHomeResponse.PeerProposal::from)
                .toList();
        // 정산 내역은 결제한 달로 묶여 있어 완료일 최신순으로 다시 놓는다
        List<StudentHomeResponse.Done> done = settlements == null ? null : settlements.getSettledJobs().stream()
                .sorted(Comparator.comparing(StudentSettledJob::getCompletedOn).reversed())
                .map(StudentHomeResponse.Done::from)
                .toList();

        requireAnySection(studentProfileId, todos, checking, waiting, peerProposals, done);
        // 다른 학생 제안은 내 이력이 아니다. 정산 내역은 한 줄이라도 있으면 이력이다
        List<?> settlementHistory = settlements == null ? null
                : settlements.isHasHistory() ? List.of(Boolean.TRUE) : List.of();
        return StudentHomeResponse.of(firstVisit(proposals, applications, progress, settlementHistory),
                todos, checking, waiting, peerProposals, done);
    }

    /**
     * 원천 조회 하나를 실행한다. 실패하면 조회 단위와 원인을 기록하고 null을 돌려준다.
     * 조회의 트랜잭션은 예외가 여기 닿기 전에 끝나므로 다음 조회는 영향받지 않는다.
     * 인증·권한 거부(401·403)는 섹션 실패로 숨기지 않고 그대로 던진다.
     */
    private <T> T load(String unit, Long profileId, Supplier<T> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            if (e instanceof BusinessException business && isAccessDenial(business.getErrorCode())) {
                throw e;
            }
            log.error("Home query failed: unit={}, profileId={}", unit, profileId, e);
            return null;
        }
    }

    private static boolean isAccessDenial(ErrorCode errorCode) {
        return errorCode.getStatus() == HttpStatus.UNAUTHORIZED || errorCode.getStatus() == HttpStatus.FORBIDDEN;
    }

    // 모든 섹션이 실패했으면 부분 성공이 아니라 공통 500이다
    private static void requireAnySection(Long profileId, List<?>... sections) {
        if (Stream.of(sections).allMatch(Objects::isNull)) {
            log.error("Home query failed for every section: profileId={}", profileId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 활동 이력이 하나도 없는 계정인지. 성공한 조회에서 이력이 하나라도 보이면 false,
     * 모든 조회가 성공했고 모두 비었으면 true, 이력을 보지 못했는데 실패한 조회가 있으면 알 수 없으므로 null이다.
     */
    private static Boolean firstVisit(Collection<?>... histories) {
        List<Collection<?>> loaded = new ArrayList<>();
        for (Collection<?> history : histories) {
            if (history != null) {
                loaded.add(history);
            }
        }
        if (loaded.stream().anyMatch(history -> !history.isEmpty())) {
            return false;
        }
        return loaded.size() == histories.length ? true : null;
    }

    // 사장님이 보는 지금 지킬 마감. 첫 초안이 오기 전에는 초안 마감, 그 뒤에는 최종 마감이다
    private static boolean ownerAwaitsFirstDraft(OwnerProgressJob job) {
        return job.getPendingSubmission() == null && job.getProgressStage() != JobProgressStage.REVISION;
    }

    private static String ownerDueStage(OwnerProgressJob job) {
        return ownerAwaitsFirstDraft(job) ? HomeResponse.STAGE_DRAFT : HomeResponse.STAGE_FINAL;
    }

    private static LocalDate ownerDue(OwnerProgressJob job) {
        return ownerAwaitsFirstDraft(job) ? job.getJob().getDraftDeadline() : job.getJob().getFinalDeadline();
    }

    // 의뢰서가 왔고 그 의뢰가 취소되지 않은 제안
    private static boolean awaitsAgreement(StudentSentProposal proposal) {
        Job job = proposal.getJob();
        return proposal.getProposal().getStatus() == ProposalStatus.AWAITING_START
                && (job == null || job.getStatus() != JobStatus.CANCELLED);
    }

    // 마지막 결과물에 수정 요청을 받았으면 수정안 차례다
    private static boolean isRevising(StudentProgressJob job) {
        JobSubmission latest = job.getLatestSubmission();
        return latest != null && latest.getReviewStatus() == JobSubmissionReviewStatus.REVISION_REQUESTED;
    }

    // 결과물을 냈고 수정 요청을 받지 않았으면 사장님 확인을 기다리는 중이다
    private static boolean isSubmitted(StudentProgressJob job) {
        return job.getLatestSubmission() != null && !isRevising(job);
    }

    // 학생이 지킬 마감. 아무것도 내지 않았으면 초안 마감, 그 뒤에는 최종 마감이다
    private static String studentDueStage(StudentProgressJob job) {
        return job.getLatestSubmission() == null ? HomeResponse.STAGE_DRAFT : HomeResponse.STAGE_FINAL;
    }

    private static LocalDate studentDue(StudentProgressJob job) {
        return job.getLatestSubmission() == null ? job.getJob().getDraftDeadline() : job.getJob().getFinalDeadline();
    }
}
