package com.gakkum.backend.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.job.dto.JobDetailResponse;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobDeclineResult;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 의뢰서 거절의 커밋·롤백·행 잠금을 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 운영 스키마를 바꾸지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ PaymentApprovalService.class, PaymentPreparationService.class, PaymentFacade.class, ProposalFacade.class,
        JobFacade.class, ProposalService.class, JobService.class, PaymentService.class, ChatRoomService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("제안 의뢰서 거절 PostgreSQL 통합 (저장·동시성·롤백·조회)")
class ProposalJobDeclinePersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_015L;
    private static final long STUDENT_PROFILE_ID = 987_111L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2DCLOW";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2DCLST";
    private static final String OWNER_USERNAME = "TEST_DECLINE_OWNER";
    private static final String STUDENT_USERNAME = "TEST_DECLINE_STUDENT";
    private static final String TID = "TDCL000000000000001";
    private static final Instant APPROVED_AT = Instant.parse("2026-08-31T15:30:00Z");

    @Autowired
    private PaymentApprovalService approvalService;

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private ProposalFacade proposalFacade;

    @Autowired
    private JobFacade jobFacade;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private KakaoPayClient kakaoPayClient;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JobSubmissionFileStorageClient jobSubmissionFileStorageClient;

    @MockitoBean
    private ChatAttachmentPolicy chatAttachmentPolicy;

    @MockitoBean
    private CertificateService certificateService;

    @MockitoSpyBean
    private JobService jobService;

    @MockitoSpyBean
    private PaymentService paymentService;

    private final List<Long> proposalIds = new ArrayList<>();
    private String orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID().toString();
        when(userService.getActiveUser(OWNER_USERNAME))
                .thenReturn(User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build());
        when(userService.getActiveUser(STUDENT_USERNAME))
                .thenReturn(User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build());
        Student student = Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build();
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(student));
        when(studentService.getStudentProfilesByIds(any())).thenReturn(Map.of(STUDENT_PROFILE_ID, student));
        when(userService.getUsersByIds(any())).thenReturn(Map.of(STUDENT_USER_ID,
                User.builder().id(STUDENT_USER_ID).name("김학생").role(UserRole.STUDENT).build()));
        when(kakaoPayClient.cid()).thenReturn("TC0ONETIME");
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(Owner.builder().id(OWNER_PROFILE_ID).build());
        Owner jobOwner = Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페")
                .storeAddress("서울시 마포구 1").build();
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(jobOwner);
        when(ownerService.findOwnerProfileByUserId(OWNER_USER_ID)).thenReturn(Optional.of(jobOwner));
        when(specialtyCategoryService.getSpecialtyDetails(any())).thenReturn(Map.of(
                3L, SpecialtyDetail.of(3L, "로고", 1L, "디자인"), 11L, SpecialtyDetail.of(11L, "메뉴판", 1L, "디자인")));
        when(ownerService.getStoreNames(any())).thenReturn(Map.of(OWNER_PROFILE_ID, "가꿈 카페"));
    }

    @AfterEach
    void cleanUp() {
        reset(jobService, paymentService);
        for (Long proposalId : proposalIds) {
            jdbcTemplate.update(
                    "delete from chat_rooms where job_id in (select id from jobs where proposal_id = ?)", proposalId);
            jdbcTemplate.update(
                    "delete from job_specialties where job_id in (select id from jobs where proposal_id = ?)",
                    proposalId);
            jdbcTemplate.update("delete from payments where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from jobs where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposal_specialties where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposals where id = ?", proposalId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 학생이 의뢰서를 거절하면 의뢰 취소·제안 거절·전액 환불이 함께 저장되고 작업 조건과 담당 학생은 보존되며 채팅방은 만들지 않는다")
    void persistsDecline() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        Job before = jobRepository.findById(jobId).orElseThrow();

        ProposalJobDeclineResult result = proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId);

        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getCancelReason()).isEqualTo("학생이 작업 시작 전에 의뢰서를 거절했습니다.");
        assertThat(job.getMessageToStudent()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(STUDENT_PROFILE_ID);
        assertThat(job.getProposalId()).isEqualTo(proposal.getId());
        assertThat(job.getBudget()).isEqualTo(before.getBudget());
        assertThat(job.getDraftDeadline()).isEqualTo(before.getDraftDeadline());
        assertThat(job.getFinalDeadline()).isEqualTo(before.getFinalDeadline());
        assertThat(job.getRevisionCount()).isEqualTo(before.getRevisionCount());
        assertThat(job.getAcceptanceMessage()).isEqualTo("잘 부탁드립니다.");
        Proposal rejected = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        // 학생 거절도 같은 트랜잭션에서 거절 주체와 시각을 남긴다
        assertThat(rejected.getRejectedBy()).isEqualTo(com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy.STUDENT);
        assertThat(rejected.getRejectedAt()).isNotNull();

        Payment payment = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.REFUNDED)
                .orElseThrow();
        assertThat(payment.getJobId()).isEqualTo(jobId);
        assertThat(payment.getAmount()).isEqualTo(50_000L);
        assertThat(payment.getRefundAmount()).isEqualTo(50_000L);
        assertThat(payment.getStudentCompensationAmount()).isZero();
        assertThat(payment.getRefundedAt()).isNotNull();
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PAID'",
                proposal.getId())).isZero();
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isZero();

        assertThat(result.getJobId()).isEqualTo(jobId);
        assertThat(result.getJobStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(result.getProposalStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(result.getPaidAmount()).isEqualTo(50_000L);
        assertThat(result.getStudentCompensationAmount()).isZero();
        assertThat(result.getRefundAmount()).isEqualTo(50_000L);
        // 응답의 거절 시각은 저장된 종료 시각과 같다
        assertThat(result.getDeclinedAt()).isEqualTo(job.getCompletedAt());
    }

    @Test
    @DisplayName("PostgreSQL에서 거절한 의뢰는 의뢰 상세의 환불 기록·사장님 종료 목록·제안 목록·사장님 결제 내역·학생 정산 내역에서 조회된다")
    void declinedJobIsReadable() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId);

        // 의뢰 상세 응답: 의뢰한 사장님과 거절한 학생 모두 학생이 거절한 것으로 보고 전액 환불 기록을 받는다
        Job declinedJob = jobRepository.findById(jobId).orElseThrow();
        for (String username : List.of(OWNER_USERNAME, STUDENT_USERNAME)) {
            JobDetailResponse.Detail detail = JobDetailResponse.Detail.from(jobFacade.getJobDetail(username, jobId));
            assertThat(detail.getStatus()).isEqualTo("CANCELLED");
            assertThat(detail.getCancelledBy()).isEqualTo("STUDENT");
            assertThat(detail.getCancelReason()).isEqualTo("학생이 작업 시작 전에 의뢰서를 거절했습니다.");
            assertThat(detail.getMessageToStudent()).isNull();
            assertThat(detail.getRefundAmount()).isEqualTo(50_000L);
            assertThat(detail.getStudentCompensationAmount()).isZero();
            assertThat(detail.getCancelledAt()).isEqualTo(declinedJob.getCompletedAt());
            assertThat(detail.getBudget()).isEqualTo(50_000L);
        }

        // 의뢰 상세가 읽는 환불 기록
        RefundedPaymentData refund = paymentService.getRefundedPayment(jobId);
        assertThat(refund.amount()).isEqualTo(50_000L);
        assertThat(refund.studentCompensationAmount()).isZero();
        assertThat(refund.refundAmount()).isEqualTo(50_000L);
        assertThat(jobService.getJobDetail(jobId).getJob().getStatus()).isEqualTo(JobStatus.CANCELLED);
        // 제안 상세의 결제 승인 시각은 환불된 결제에서도 읽힌다
        assertThat(paymentService.getProposalPaidAt(proposal.getId())).isEqualTo(APPROVED_AT);

        assertThat(jobService.getClosedJobs(GetClosedJobsCommand.of(OWNER_PROFILE_ID)))
                .anySatisfy(closed -> assertThat(closed.getJob().getId()).isEqualTo(jobId));
        assertThat(proposalService.getMyProposals(GetMyProposalsCommand.of(STUDENT_PROFILE_ID)))
                .anySatisfy(data -> {
                    assertThat(data.getProposal().getId()).isEqualTo(proposal.getId());
                    assertThat(data.getProposal().getStatus()).isEqualTo(ProposalStatus.REJECTED);
                });
        assertThat(proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(OWNER_PROFILE_ID)))
                .anySatisfy(data -> {
                    assertThat(data.getProposal().getId()).isEqualTo(proposal.getId());
                    assertThat(data.getProposal().getStatus()).isEqualTo(ProposalStatus.REJECTED);
                });
        assertThat(jobService.getJobsByProposalIds(List.of(proposal.getId())).get(proposal.getId()).getStatus())
                .isEqualTo(JobStatus.CANCELLED);

        PaymentHistoryItemResult ownerItem = paymentFacade.getPaymentHistory(OWNER_USERNAME).getMonths().stream()
                .flatMap(month -> month.getPayments().stream())
                .filter(item -> item.getJobId().equals(jobId))
                .findFirst().orElseThrow();
        assertThat(ownerItem.getStatus()).isEqualTo(PaymentHistoryStatus.FULLY_REFUNDED);
        assertThat(ownerItem.getAmount()).isEqualTo(50_000L);
        assertThat(ownerItem.getRefundAmount()).isEqualTo(50_000L);
        assertThat(ownerItem.getStudentName()).isEqualTo("김학생");

        SettlementHistoryResult settlement = paymentFacade.getSettlementHistory(STUDENT_USERNAME);
        SettlementHistoryItemResult studentItem = settlement.getMonths().stream()
                .flatMap(month -> month.getSettlements().stream())
                .filter(item -> item.getJobId().equals(jobId))
                .findFirst().orElseThrow();
        assertThat(studentItem.getStatus()).isEqualTo(SettlementHistoryStatus.REFUNDED);
        assertThat(studentItem.getAmount()).isZero();
        assertThat(studentItem.getSettledDate()).isNotNull();
        assertThat(settlement.getSummary().getScheduledAmount()).isZero();
        assertThat(settlement.getSummary().getTotalSettledAmount()).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 의뢰서 거절을 동시에 요청해도 하나만 성공하고 나머지는 JOB_DECLINE_409이며 환불은 한 번만 기록된다")
    void refundsOnceForConcurrentDeclines() throws Exception {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);

        List<Object> outcomes = runConcurrently(List.of(
                () -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                () -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                () -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId)));

        assertThat(outcomes).filteredOn(ProposalJobDeclineResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                .containsExactly(ErrorCode.JOB_DECLINE_NOT_AVAILABLE, ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        Payment refunded = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.REFUNDED)
                .orElseThrow();
        assertThat(refunded.getRefundAmount()).isEqualTo(50_000L);
        assertThat(count("select count(*) from payments where proposal_id = ?", proposal.getId())).isEqualTo(1);
        verify(paymentService, times(1)).refundOnDecline(jobId, proposal.getId(), OWNER_USER_ID);

        // 이후의 중복 거절도 409이고 저장된 환불 기록과 거절 시각을 바꾸지 않는다
        Job declined = jobRepository.findById(jobId).orElseThrow();
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        assertThat(jobRepository.findById(jobId).orElseThrow().getCompletedAt())
                .isEqualTo(declined.getCompletedAt());
        assertThat(paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.REFUNDED)
                .orElseThrow().getRefundedAt()).isEqualTo(refunded.getRefundedAt());
        verify(paymentService, times(1)).refundOnDecline(jobId, proposal.getId(), OWNER_USER_ID);
    }

    @Test
    @DisplayName("PostgreSQL에서 작업 시작과 의뢰서 거절을 동시에 요청하면 하나만 성공하고 의뢰·제안·결제·채팅방이 성공한 쪽의 상태로 일관된다")
    void onlyOneOfConcurrentStartAndDeclineSucceeds() throws Exception {
        for (int round = 0; round < 5; round++) {
            orderId = UUID.randomUUID().toString();
            Proposal proposal = saveProposal();
            Long jobId = approve(proposal);

            List<Object> outcomes = runConcurrently(List.of(
                    () -> proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId)),
                    () -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId)));

            Job job = jobRepository.findById(jobId).orElseThrow();
            ProposalStatus proposalStatus = proposalRepository.findById(proposal.getId()).orElseThrow().getStatus();
            int chatRooms = count("select count(*) from chat_rooms where job_id = ?", jobId);
            if (outcomes.get(1) instanceof ProposalJobDeclineResult) {
                assertThat(outcomes.get(0)).isEqualTo(ErrorCode.JOB_START_NOT_AVAILABLE);
                assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
                assertThat(job.getStartedAt()).isNull();
                assertThat(proposalStatus).isEqualTo(ProposalStatus.REJECTED);
                assertThat(paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.REFUNDED))
                        .isPresent();
                assertThat(chatRooms).isZero();
            } else {
                assertThat(outcomes.get(1)).isEqualTo(ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
                assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
                assertThat(job.getCompletedAt()).isNull();
                assertThat(job.getCancelReason()).isNull();
                assertThat(proposalStatus).isEqualTo(ProposalStatus.ACCEPTED);
                Payment paid = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PAID)
                        .orElseThrow();
                assertThat(paid.getRefundAmount()).isNull();
                assertThat(paid.getRefundedAt()).isNull();
                assertThat(chatRooms).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 환불 기록 저장이 실패하면 의뢰·제안·결제가 모두 원래 상태로 롤백되고 재요청이 한 번만 거절한다")
    void rollsBackDeclineWhenRefundSavingFails() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        // 환불까지 기록한 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("저장 실패");
        }).when(paymentService).refundOnDecline(jobId, proposal.getId(), OWNER_USER_ID);

        assertThatThrownBy(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId))
                .isInstanceOf(IllegalStateException.class);

        assertAwaitingStart(proposal, jobId);

        reset(paymentService);
        ProposalJobDeclineResult result = proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId);
        assertThat(result.getRefundAmount()).isEqualTo(50_000L);
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("PostgreSQL에서 결제 완료 주문이 없으면 500으로 거부하고 이미 바꾼 의뢰와 제안의 상태를 롤백한다")
    void rollsBackDeclineWhenPaidPaymentIsMissing() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        // 승인된 결제가 사라진 데이터 오류 상황
        jdbcTemplate.update("update payments set status = 'READY_FAILED' where proposal_id = ?", proposal.getId());

        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(jobRepository.findById(jobId).orElseThrow().getCompletedAt()).isNull();
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'REFUNDED'",
                proposal.getId())).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 결제한 사장님이 의뢰한 사장님과 다르면 500으로 거부하고 의뢰·제안·결제를 모두 롤백한다")
    void rollsBackDeclineWhenPaymentOwnerMismatches() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        // 결제의 사장님 참조가 의뢰한 사장님과 어긋난 데이터 오류 상황
        jdbcTemplate.update("update payments set owner_user_id = ? where proposal_id = ?",
                "01K58M6PJV8VAJMXHBHJ2OTHER", proposal.getId());

        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.INTERNAL_SERVER_ERROR);

        assertAwaitingStart(proposal, jobId);
        assertThat(count("select count(*) from payments where proposal_id = ? and owner_user_id = ?",
                proposal.getId(), "01K58M6PJV8VAJMXHBHJ2OTHER")).isEqualTo(1);
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 의뢰 저장이 실패해도 제안과 결제는 바뀌지 않는다")
    void rollsBackDeclineWhenJobSavingFails() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("저장 실패");
        }).when(jobService).declineJob(jobId, proposal.getId(), STUDENT_PROFILE_ID);

        assertThatThrownBy(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId))
                .isInstanceOf(IllegalStateException.class);

        assertAwaitingStart(proposal, jobId);
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 학생·사장님·다른 데모 세션의 학생은 의뢰서를 거절할 수 없고 상태와 결제가 바뀌지 않는다")
    void rejectsDeclineByOthers() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);

        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID + 1).userId(STUDENT_USER_ID).build()));
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.JOB_DECLINE_FORBIDDEN);

        assertCode(() -> proposalFacade.declineProposalJob(OWNER_USERNAME, jobId), ErrorCode.JOB_DECLINE_FORBIDDEN);

        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
        when(userService.getActiveUser(STUDENT_USERNAME)).thenReturn(User.builder().id(STUDENT_USER_ID)
                .role(UserRole.STUDENT).demoSessionId("01K6DEMO00000000000000000A").build());
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId), ErrorCode.JOB_NOT_FOUND);

        assertAwaitingStart(proposal, jobId);
    }

    @Test
    @DisplayName("PostgreSQL에서 없는 의뢰는 404, 일반 의뢰와 이미 시작한 의뢰는 JOB_DECLINE_409로 거부하고 시작한 의뢰는 진행 중으로 남는다")
    void rejectsDeclineOfMissingGeneralOrStartedJob() {
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, Long.MAX_VALUE),
                ErrorCode.JOB_NOT_FOUND);

        Proposal proposal = saveProposal();
        Long generalJobId = jdbcTemplate.queryForObject("""
                insert into jobs (owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                        revision_count, status, selected_student_profile_id)
                values (?, '일반 의뢰', '설명', 1000, current_date, current_date, 0, 'MATCHED', ?)
                returning id
                """, Long.class, OWNER_PROFILE_ID, STUDENT_PROFILE_ID);
        try {
            assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, generalJobId),
                    ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
            assertThat(jobRepository.findById(generalJobId).orElseThrow().getStatus()).isEqualTo(JobStatus.MATCHED);
        } finally {
            jdbcTemplate.update("delete from jobs where id = ?", generalJobId);
        }

        Long jobId = approve(proposal);
        proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId));
        assertCode(() -> proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId),
                ErrorCode.JOB_DECLINE_NOT_AVAILABLE);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PAID)).isPresent();
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 거절한 의뢰는 다시 시작할 수 없다")
    void rejectsStartAfterDecline() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        proposalFacade.declineProposalJob(STUDENT_USERNAME, jobId);

        assertCode(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId)),
                ErrorCode.JOB_START_NOT_AVAILABLE);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isZero();
    }

    private void assertAwaitingStart(Proposal proposal, Long jobId) {
        // 거절이 롤백되면 거절 주체와 시각도 남지 않는다
        assertThat(count("select count(*) from proposals where id = ? and rejected_by is null "
                + "and rejected_at is null", proposal.getId())).isEqualTo(1);
        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getCompletedAt()).isNull();
        assertThat(job.getCancelReason()).isNull();
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.AWAITING_START);
        Payment payment = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PAID)
                .orElseThrow();
        assertThat(payment.getRefundAmount()).isNull();
        assertThat(payment.getStudentCompensationAmount()).isNull();
        assertThat(payment.getRefundedAt()).isNull();
    }

    private void assertCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private Long approve(Proposal proposal) {
        Payment payment = Payment.pendingForProposal(proposal.getId(), OWNER_USER_ID, orderId,
                proposal.getProposedFee(), 1, "잘 부탁드립니다.", Instant.parse("2026-08-31T15:00:00Z"));
        payment.recordKakaoTid(TID + proposalIds.size());
        paymentRepository.saveAndFlush(payment);
        when(kakaoPayClient.order(TID + proposalIds.size())).thenReturn(new PaymentResult(
                TID + proposalIds.size(), "TC0ONETIME", orderId, OWNER_USER_ID, 50_000L, "SUCCESS_PAYMENT",
                APPROVED_AT));
        return approvalService.approve(OWNER_USERNAME, orderId, "pg-token").jobId();
    }

    private Proposal saveProposal() {
        Proposal proposal = proposalRepository.saveAndFlush(Proposal.create(
                STUDENT_PROFILE_ID, OWNER_PROFILE_ID, "메뉴판 개선 제안", "문제", "해결", "계획",
                50_000L, 3, 7, List.of(), null));
        proposalIds.add(proposal.getId());
        proposalSpecialtyRepository.saveAllAndFlush(List.of(
                ProposalSpecialty.create(proposal.getId(), 3L), ProposalSpecialty.create(proposal.getId(), 11L)));
        return proposal;
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    // 요청을 동시에 시작해 요청 순서대로 결과를 모은다. 성공은 결과 객체, 업무 오류는 오류 코드로 담는다
    private List<Object> runConcurrently(List<Callable<Object>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    if (!(exception.getCause() instanceof BusinessException business)) {
                        throw exception;
                    }
                    outcomes.add(business.getErrorCode());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
