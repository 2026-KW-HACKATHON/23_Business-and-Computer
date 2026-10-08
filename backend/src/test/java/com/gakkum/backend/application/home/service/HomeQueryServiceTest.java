package com.gakkum.backend.application.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerClosedJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.OwnerOpenJob;
import com.gakkum.backend.application.home.dto.HomeQueryDto.PeerProposal;
import com.gakkum.backend.application.home.dto.HomeQueryDto.StudentSettlements;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

@DisplayName("홈 집계 원천 조회")
class HomeQueryServiceTest {

    private static final Long ME = 7L;
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PDEMO";

    private final JobService jobService = mock(JobService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final PaymentFacade paymentFacade = mock(PaymentFacade.class);
    private final HomeQueryService service = new HomeQueryService(jobService, proposalService, studentService,
            userService, ownerService, specialtyCategoryService, paymentFacade);

    @Test
    @DisplayName("다른 학생 제안은 내 데모 범위의 공감순 상위 5개에서 본인 제안을 뺀 앞의 2개만 반환한다")
    void returnsTopTwoPeerProposalsExcludingMine() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(
                peer(1L, ME), peer(2L, 8L), peer(3L, ME), peer(4L, 9L), peer(5L, 8L)));
        when(studentService.getStudentProfilesByIds(List.of(8L, 9L))).thenReturn(Map.of(
                8L, Student.builder().id(8L).userId("U8").build(),
                9L, Student.builder().id(9L).userId("U9").build()));
        when(userService.getUsersByIds(any())).thenReturn(Map.of(
                "U8", User.builder().id("U8").name("팔학생").build(),
                "U9", User.builder().id("U9").name("구학생").build()));
        when(ownerService.getStoreNames(Set.of(5L))).thenReturn(Map.of(5L, "가꿈 카페"));
        when(proposalService.getLikedProposalIds(ME, List.of(2L, 4L))).thenReturn(Set.of(4L));

        List<PeerProposal> peers = service.getPeerProposals(ME, DEMO_SESSION_ID);

        assertThat(peers).extracting(peer -> peer.getProposal().getId()).containsExactly(2L, 4L);
        assertThat(peers).extracting(PeerProposal::getStudentName).containsExactly("팔학생", "구학생");
        assertThat(peers).extracting(PeerProposal::getStoreName).containsOnly("가꿈 카페");
        assertThat(peers).extracting(PeerProposal::isLikedByMe).containsExactly(false, true);
        ArgumentCaptor<GetExploreProposalsCommand> command = ArgumentCaptor.forClass(GetExploreProposalsCommand.class);
        verify(proposalService).getExploreProposals(command.capture());
        assertThat(command.getValue().getDemoSessionId()).isEqualTo(DEMO_SESSION_ID);
        assertThat(command.getValue().getOrder()).isEqualTo(ProposalExploreOrder.LIKES);
        assertThat(command.getValue().getSpecialtyCategoryId()).isNull();
        assertThat(command.getValue().getLimit()).isEqualTo(5);
    }

    @Test
    @DisplayName("상위 제안이 모두 본인 제안이면 빈 목록을 반환하고 학생·매장·공감을 조회하지 않는다")
    void returnsNoPeersWhenAllMine() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(peer(1L, ME), peer(2L, ME)));

        assertThat(service.getPeerProposals(ME, null)).isEmpty();
        verifyNoInteractions(studentService, userService, ownerService);
    }

    @Test
    @DisplayName("정산 내역에서 정산 완료만 완료 작업으로 읽고 의뢰의 제안 ID를 붙인다")
    void returnsSettledJobsWithProposalId() {
        when(paymentFacade.getSettlementHistory("KAKAO_67890")).thenReturn(history(
                item(41L, SettlementHistoryStatus.SCHEDULED, null),
                item(42L, SettlementHistoryStatus.SETTLED, LocalDate.of(2026, 9, 28)),
                item(43L, SettlementHistoryStatus.START_COMPENSATION, LocalDate.of(2026, 9, 3)),
                item(44L, SettlementHistoryStatus.REFUNDED, LocalDate.of(2026, 9, 1))));
        when(jobService.getJobsByIds(Set.of(42L))).thenReturn(Map.of(42L, Job.builder().id(42L).proposalId(66L).build()));

        StudentSettlements settlements = service.getStudentSettlements("KAKAO_67890");

        assertThat(settlements.isHasHistory()).isTrue();
        assertThat(settlements.getSettledJobs()).hasSize(1);
        assertThat(settlements.getSettledJobs().get(0).getJobId()).isEqualTo(42L);
        assertThat(settlements.getSettledJobs().get(0).getProposalId()).isEqualTo(66L);
        assertThat(settlements.getSettledJobs().get(0).getTitle()).isEqualTo("의뢰 42");
        assertThat(settlements.getSettledJobs().get(0).getStoreName()).isEqualTo("가꿈 카페");
        assertThat(settlements.getSettledJobs().get(0).getCompletedOn()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    @DisplayName("정산 완료가 없어도 정산 내역이 있으면 이력으로 보고, 내역이 없으면 이력이 없다")
    void reportsSettlementHistoryWithoutSettledJobs() {
        when(paymentFacade.getSettlementHistory("SCHEDULED")).thenReturn(
                history(item(41L, SettlementHistoryStatus.SCHEDULED, null)));
        when(paymentFacade.getSettlementHistory("NONE")).thenReturn(history());

        assertThat(service.getStudentSettlements("SCHEDULED").isHasHistory()).isTrue();
        assertThat(service.getStudentSettlements("SCHEDULED").getSettledJobs()).isEmpty();
        assertThat(service.getStudentSettlements("NONE").isHasHistory()).isFalse();
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("모집 중 의뢰의 분야는 대분류 ID가 가장 작은 대분류 이름이고 특기가 없으면 null이다")
    void returnsFirstCategoryNameAsField() {
        when(jobService.getOpenJobs(any())).thenReturn(List.of(
                OpenJobData.of(Job.builder().id(31L).build(), List.of(12L, 3L), 2, JobProgressStage.REQUESTED),
                OpenJobData.of(Job.builder().id(32L).build(), List.of(), 0, JobProgressStage.REQUESTED)));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(12L, 3L))).thenReturn(Map.of(
                12L, SpecialtyDetail.of(12L, "프론트엔드", 2L, "개발"),
                3L, SpecialtyDetail.of(3L, "포스터", 1L, "디자인")));

        List<OwnerOpenJob> jobs = service.getOwnerOpenJobs(5L);

        assertThat(jobs).extracting(OwnerOpenJob::getField).containsExactly("디자인", null);
        assertThat(jobs).extracting(OwnerOpenJob::getApplicantCount).containsExactly(2, 0);
    }

    @Test
    @DisplayName("끝난 의뢰는 맡았던 학생 이름을 붙이고 모집 중에 취소한 의뢰는 학생 이름이 null이다")
    void returnsClosedJobsWithStudentName() {
        when(jobService.getClosedJobs(any())).thenReturn(List.of(
                ClosedJobData.of(closed(41L, JobStatus.CLOSED, 8L), List.of(), JobProgressStage.COMPLETED),
                ClosedJobData.of(closed(42L, JobStatus.CANCELLED, null), List.of(), JobProgressStage.CANCELLED)));
        when(studentService.getStudentProfilesByIds(List.of(8L)))
                .thenReturn(Map.of(8L, Student.builder().id(8L).userId("U8").build()));
        when(userService.getUsersByIds(List.of("U8")))
                .thenReturn(Map.of("U8", User.builder().id("U8").name("팔학생").build()));

        List<OwnerClosedJob> jobs = service.getOwnerClosedJobs(5L);

        assertThat(jobs).extracting(OwnerClosedJob::getStudentName).containsExactly("팔학생", null);
    }

    private static ExploreProposalData peer(Long id, Long studentProfileId) {
        return ExploreProposalData.of(Proposal.builder()
                .id(id)
                .studentProfileId(studentProfileId)
                .ownerProfileId(5L)
                .title("제안 " + id)
                .status(ProposalStatus.PENDING)
                .likeCount(10)
                .build(), List.of());
    }

    private static Job closed(Long id, JobStatus status, Long selectedStudentProfileId) {
        return Job.builder()
                .id(id)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .completedAt(LocalDateTime.of(2026, 9, 30, 15, 30))
                .build();
    }

    private static SettlementHistoryItemResult item(Long jobId, SettlementHistoryStatus status, LocalDate settledDate) {
        return SettlementHistoryItemResult.of(jobId, "의뢰 " + jobId, 50000L, settledDate, "가꿈 카페", status);
    }

    private static SettlementHistoryResult history(SettlementHistoryItemResult... items) {
        return SettlementHistoryResult.of(SettlementHistorySummaryResult.of(0L, 0L, 0L),
                items.length == 0 ? List.of() : List.of(SettlementHistoryMonthResult.of("2026-09", List.of(items))));
    }
}
