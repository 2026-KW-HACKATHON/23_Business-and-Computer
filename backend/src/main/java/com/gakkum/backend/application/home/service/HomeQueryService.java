package com.gakkum.backend.application.home.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentAppliedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * 홈 집계의 원천 조회. 메서드 하나가 원천 하나를 독립된 읽기 전용 트랜잭션으로 읽어,
 * 한 조회가 실패해도 HomeFacade가 트랜잭션이 끝난 뒤 예외를 받아 나머지 조회를 이어 갈 수 있다.
 * 기존 목록 API와 같은 도메인 조회를 쓰므로 본인 범위와 데모 세션 격리도 같다.
 */
@Service
@RequiredArgsConstructor
public class HomeQueryService {

    // 다른 학생 제안은 공감순 상위에서 이만큼 읽어 본인 제안을 빼고
    private static final int PEER_CANDIDATE_SIZE = 5;
    // 이만큼만 내린다
    private static final int PEER_SIZE = 2;
    // 첫 페이지에서 모든 행이 경계 안에 들도록 쓰는 시각. DB에 저장되는 생성 시각 범위 밖이다
    private static final LocalDateTime LATEST_START = LocalDateTime.of(9999, 1, 1, 0, 0);

    private final JobService jobService;
    private final ProposalService proposalService;
    private final StudentService studentService;
    private final UserService userService;
    private final OwnerService ownerService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final PaymentFacade paymentFacade;

    /** 사장님의 모집 중 의뢰를 대기 중 지원자 수와 함께 생성 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<OwnerOpenJob> getOwnerOpenJobs(Long ownerProfileId) {
        List<OpenJobData> jobs = jobService.getOpenJobs(GetOpenJobsCommand.of(ownerProfileId));
        Map<Long, SpecialtyDetail> specialtiesById = specialtyDetails(
                jobs.stream().map(OpenJobData::getSpecialtyIds).toList());
        return jobs.stream()
                .map(data -> OwnerOpenJob.of(data.getJob(), data.getApplicantCount(),
                        firstCategoryName(data.getSpecialtyIds(), specialtiesById)))
                .toList();
    }

    /** 사장님이 받은 제안 중 취소되지 않은 제안을 제안한 학생의 현재 이름·전공과 함께 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<OwnerReceivedProposal> getOwnerReceivedProposals(Long ownerProfileId) {
        List<ExploreProposalData> proposals =
                proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(ownerProfileId));
        if (proposals.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(proposals.stream()
                .map(data -> data.getProposal().getStudentProfileId())
                .distinct()
                .toList());
        Map<String, User> studentUsersById = studentUsers(studentsById.values());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyDetails(
                proposals.stream().map(ExploreProposalData::getSpecialtyIds).toList());
        return proposals.stream()
                .map(data -> {
                    Student student = studentsById.get(data.getProposal().getStudentProfileId());
                    return OwnerReceivedProposal.of(data.getProposal(),
                            studentUsersById.get(student.getUserId()).getName(), student.getMajor(),
                            firstCategoryName(data.getSpecialtyIds(), specialtiesById));
                })
                .toList();
    }

    /** 사장님의 진행 중 의뢰를 검토 대기 제출물, 맡은 학생의 현재 이름·전공과 함께 생성 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<OwnerProgressJob> getOwnerProgressJobs(Long ownerProfileId) {
        List<MatchedJobData> jobs = jobService.getMatchedJobs(GetMatchedJobsCommand.of(ownerProfileId));
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(data -> data.getJob().getSelectedStudentProfileId())
                .distinct()
                .toList());
        Map<String, User> studentUsersById = studentUsers(studentsById.values());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyDetails(
                jobs.stream().map(MatchedJobData::getSpecialtyIds).toList());
        return jobs.stream()
                .map(data -> {
                    Student student = studentsById.get(data.getJob().getSelectedStudentProfileId());
                    return OwnerProgressJob.of(data.getJob(), data.getPendingSubmission(), data.getProgressStage(),
                            studentUsersById.get(student.getUserId()).getName(), student.getMajor(),
                            firstCategoryName(data.getSpecialtyIds(), specialtiesById));
                })
                .toList();
    }

    /** 사장님의 끝난 의뢰(완료·취소)를 맡았던 학생의 현재 이름과 함께 종료 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<OwnerClosedJob> getOwnerClosedJobs(Long ownerProfileId) {
        List<ClosedJobData> jobs = jobService.getClosedJobs(GetClosedJobsCommand.of(ownerProfileId));
        if (jobs.isEmpty()) {
            return List.of();
        }
        // 모집 중에 취소된 의뢰는 매칭된 학생이 없다
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(data -> data.getJob().getSelectedStudentProfileId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<String, User> studentUsersById = studentUsers(studentsById.values());
        return jobs.stream()
                .map(data -> {
                    Long studentProfileId = data.getJob().getSelectedStudentProfileId();
                    return OwnerClosedJob.of(data.getJob(), studentProfileId == null
                            ? null
                            : studentUsersById.get(studentsById.get(studentProfileId).getUserId()).getName());
                })
                .toList();
    }

    /** 학생이 보낸 제안 중 취소하지 않은 제안을 매장 이름, 결제로 만들어진 의뢰와 함께 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<StudentSentProposal> getStudentSentProposals(Long studentProfileId) {
        List<ExploreProposalData> proposals = proposalService.getMyProposals(GetMyProposalsCommand.of(studentProfileId));
        if (proposals.isEmpty()) {
            return List.of();
        }
        Map<Long, String> storeNames = ownerService.getStoreNames(proposals.stream()
                .map(data -> data.getProposal().getOwnerProfileId())
                .collect(Collectors.toSet()));
        Map<Long, SpecialtyDetail> specialtiesById = specialtyDetails(
                proposals.stream().map(ExploreProposalData::getSpecialtyIds).toList());
        // 결제 전 제안은 키가 없다
        Map<Long, Job> jobsByProposalId = jobService.getJobsByProposalIds(proposals.stream()
                .map(data -> data.getProposal().getId())
                .toList());
        return proposals.stream()
                .map(data -> StudentSentProposal.of(data.getProposal(),
                        storeNames.get(data.getProposal().getOwnerProfileId()),
                        categoryNames(data.getSpecialtyIds(), specialtiesById),
                        jobsByProposalId.get(data.getProposal().getId())))
                .toList();
    }

    /** 학생의 모집 중 대기 지원과 미선정 지원 이력을 매장 이름과 함께 최신 지원순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<StudentApplication> getStudentApplications(Long studentProfileId, String demoSessionId) {
        List<StudentAppliedJobData> jobs = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(studentProfileId, demoSessionId));
        Map<Long, String> storeNames = ownerService.getStoreNames(jobs.stream()
                .map(data -> data.getJob().getOwnerProfileId())
                .collect(Collectors.toSet()));
        return jobs.stream()
                .map(data -> StudentApplication.of(data.getJob(), data.getApplication(),
                        data.getApplicationStatus() == JobApplicationStatus.PENDING,
                        storeNames.get(data.getJob().getOwnerProfileId())))
                .toList();
    }

    /** 학생과 매칭된 진행 중 의뢰를 최신 제출물, 매장 이름과 함께 생성 최신순으로 읽는다. */
    @Transactional(readOnly = true)
    public List<StudentProgressJob> getStudentProgressJobs(Long studentProfileId) {
        List<StudentMatchedJobData> jobs = jobService.getStudentMatchedJobs(
                GetStudentMatchedJobsCommand.of(studentProfileId));
        Map<Long, String> storeNames = ownerService.getStoreNames(jobs.stream()
                .map(data -> data.getJob().getOwnerProfileId())
                .collect(Collectors.toSet()));
        Map<Long, SpecialtyDetail> specialtiesById = specialtyDetails(
                jobs.stream().map(StudentMatchedJobData::getSpecialtyIds).toList());
        return jobs.stream()
                .map(data -> StudentProgressJob.of(data.getJob(), data.getLatestSubmission(),
                        storeNames.get(data.getJob().getOwnerProfileId()),
                        categoryNames(data.getSpecialtyIds(), specialtiesById)))
                .toList();
    }

    /**
     * 학생 정산 내역에서 정산 완료(SETTLED) 작업을 읽는다. 정산 판정은 정산 내역 API와 같은 PaymentFacade에 맡기고,
     * 작업 종류를 가릴 제안 ID만 의뢰에서 덧붙인다.
     */
    @Transactional(readOnly = true)
    public StudentSettlements getStudentSettlements(String username) {
        List<SettlementHistoryItemResult> items = paymentFacade.getSettlementHistory(username).getMonths().stream()
                .flatMap(month -> month.getSettlements().stream())
                .toList();
        List<SettlementHistoryItemResult> settled = items.stream()
                .filter(item -> item.getStatus() == SettlementHistoryStatus.SETTLED)
                .toList();
        if (settled.isEmpty()) {
            return StudentSettlements.of(!items.isEmpty(), List.of());
        }
        Map<Long, Job> jobsById = jobService.getJobsByIds(settled.stream()
                .map(SettlementHistoryItemResult::getJobId)
                .collect(Collectors.toSet()));
        return StudentSettlements.of(true, settled.stream()
                .map(item -> StudentSettledJob.of(item.getJobId(), jobsById.get(item.getJobId()).getProposalId(),
                        item.getTitle(), item.getStoreName(), item.getSettledDate()))
                .toList());
    }

    /**
     * 공감 많은 제안 상위 5개에서 본인 제안을 뺀 앞의 2개를 읽는다. 탐색(GET /explore)의 제안 공감순과 같은 조회다.
     * 본인 제안은 제안의 학생 프로필로 가려, 보낸 제안 조회가 실패해도 영향받지 않는다.
     */
    @Transactional(readOnly = true)
    public List<PeerProposal> getPeerProposals(Long studentProfileId, String demoSessionId) {
        List<Proposal> peers = proposalService.getExploreProposals(GetExploreProposalsCommand.of(
                        demoSessionId, null, ProposalExploreOrder.LIKES, Integer.MAX_VALUE, LATEST_START,
                        Long.MAX_VALUE, PEER_CANDIDATE_SIZE)).stream()
                .map(ExploreProposalData::getProposal)
                .filter(proposal -> !studentProfileId.equals(proposal.getStudentProfileId()))
                .limit(PEER_SIZE)
                .toList();
        if (peers.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(peers.stream()
                .map(Proposal::getStudentProfileId)
                .distinct()
                .toList());
        Map<String, User> studentUsersById = studentUsers(studentsById.values());
        Map<Long, String> storeNames = ownerService.getStoreNames(peers.stream()
                .map(Proposal::getOwnerProfileId)
                .collect(Collectors.toSet()));
        Set<Long> likedProposalIds = proposalService.getLikedProposalIds(
                studentProfileId, peers.stream().map(Proposal::getId).toList());
        return peers.stream()
                .map(proposal -> PeerProposal.of(proposal,
                        studentUsersById.get(studentsById.get(proposal.getStudentProfileId()).getUserId()).getName(),
                        storeNames.get(proposal.getOwnerProfileId()),
                        likedProposalIds.contains(proposal.getId())))
                .toList();
    }

    private Map<String, User> studentUsers(Collection<Student> students) {
        return userService.getUsersByIds(students.stream().map(Student::getUserId).distinct().toList());
    }

    private Map<Long, SpecialtyDetail> specialtyDetails(List<List<Long>> specialtyIds) {
        return specialtyCategoryService.getSpecialtyDetails(specialtyIds.stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet()));
    }

    private static String firstCategoryName(List<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        List<String> names = categoryNames(specialtyIds, specialtiesById);
        return names.isEmpty() ? null : names.get(0);
    }

    // 기존 목록 응답의 대분류 순서(대분류 ID 오름차순)와 같게 겹치지 않는 대분류 이름을 만든다
    private static List<String> categoryNames(List<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        return specialtyIds.stream()
                .map(id -> {
                    SpecialtyDetail detail = specialtiesById.get(id);
                    if (detail == null) {
                        throw new IllegalStateException("Specialty not found: " + id);
                    }
                    return detail;
                })
                .sorted(Comparator.comparing(SpecialtyDetail::getCategoryId))
                .map(SpecialtyDetail::getCategoryName)
                .distinct()
                .toList();
    }
}
