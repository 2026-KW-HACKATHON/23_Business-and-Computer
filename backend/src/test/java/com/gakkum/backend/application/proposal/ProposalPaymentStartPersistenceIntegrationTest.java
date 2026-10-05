package com.gakkum.backend.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobStartResult;
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
 * 결제 승인과 작업 시작의 커밋·롤백·행 잠금을 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 운영 스키마를 바꾸지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ PaymentApprovalService.class, PaymentPreparationService.class, PaymentFacade.class, ProposalFacade.class,
        ProposalService.class, JobService.class, PaymentService.class, ChatRoomService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("제안 결제·작업 시작 PostgreSQL 통합 (제약·저장·동시성·롤백 복구)")
class ProposalPaymentStartPersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_005L;
    private static final long STUDENT_PROFILE_ID = 987_101L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2V37OW";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2V37ST";
    private static final String OWNER_USERNAME = "TEST_V37_OWNER";
    private static final String STUDENT_USERNAME = "TEST_V37_STUDENT";
    private static final String TID = "TV37000000000000001";
    // UTC로는 8월 31일 15:30, 한국 시간으로는 9월 1일 00:30
    private static final Instant APPROVED_AT = Instant.parse("2026-08-31T15:30:00Z");

    @Autowired
    private PaymentApprovalService approvalService;

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private ProposalFacade proposalFacade;

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

    @MockitoSpyBean
    private JobService jobService;

    @MockitoSpyBean
    private ChatRoomService chatRoomService;

    private final List<Long> proposalIds = new ArrayList<>();
    private String orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID().toString();
        when(userService.getActiveUser(OWNER_USERNAME))
                .thenReturn(User.builder().id(OWNER_USER_ID).role(UserRole.OWNER).build());
        when(userService.getActiveUser(STUDENT_USERNAME))
                .thenReturn(User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build());
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
        when(kakaoPayClient.cid()).thenReturn("TC0ONETIME");
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(Owner.builder().id(OWNER_PROFILE_ID).build());
    }

    @AfterEach
    void cleanUp() {
        reset(jobService, chatRoomService);
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
    @DisplayName("PostgreSQL에서 제안 결제를 승인하면 수락 대기 의뢰·분류·결제·제안 상태가 함께 저장되고 지원서와 채팅방은 만들지 않는다")
    void persistsAwaitingStartJobOnApproval() {
        Proposal proposal = saveProposal();
        savePendingPayment(proposal, 2, "매장 분위기에 맞춰 주세요.");
        givenProviderApproved();

        ApprovedOrderData result = approvalService.approve(OWNER_USERNAME, orderId, "pg-token");

        Job job = jobRepository.findByProposalId(proposal.getId()).orElseThrow();
        assertThat(result.jobId()).isEqualTo(job.getId());
        assertThat(result.jobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getOwnerProfileId()).isEqualTo(OWNER_PROFILE_ID);
        assertThat(job.getSelectedStudentProfileId()).isEqualTo(STUDENT_PROFILE_ID);
        assertThat(job.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(job.getDescription()).isEqualTo("[고객 문제]\n문제\n\n[해결 방안]\n해결\n\n[작업 계획]\n계획");
        assertThat(job.getBudget()).isEqualTo(50_000L);
        // 마감일은 UTC 날짜(8월 31일)가 아니라 승인 시각의 한국 날짜(9월 1일)에 3일·7일을 더한다
        assertThat(job.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(job.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(job.getRevisionCount()).isEqualTo(2);
        assertThat(job.getAcceptanceMessage()).isEqualTo("매장 분위기에 맞춰 주세요.");
        assertThat(job.getMessageToStudent()).isNull();
        assertThat(job.getStartedAt()).isNull();
        assertThat(count("select count(*) from job_specialties where job_id = ? and specialty_id in (3, 11)",
                job.getId())).isEqualTo(2);
        assertThat(count("select count(*) from job_applications where job_id = ?", job.getId())).isZero();
        assertThat(count("select count(*) from chat_rooms where job_id = ?", job.getId())).isZero();

        Payment payment = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PAID)
                .orElseThrow();
        assertThat(payment.getJobId()).isEqualTo(job.getId());
        assertThat(payment.getJobApplicationId()).isNull();
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.AWAITING_START);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 주문을 동시에 승인해도 의뢰는 한 번만 만들어지고 모든 요청이 같은 의뢰를 반환한다")
    void createsOneJobForConcurrentApprovals() throws Exception {
        Proposal proposal = saveProposal();
        savePendingPayment(proposal, 1, null);
        givenProviderApproved();

        List<ApprovedOrderData> results = runConcurrently(3,
                () -> approvalService.approve(OWNER_USERNAME, orderId, "pg-token"));

        assertThat(count("select count(*) from jobs where proposal_id = ?", proposal.getId())).isEqualTo(1);
        Long jobId = jobRepository.findByProposalId(proposal.getId()).orElseThrow().getId();
        assertThat(results).extracting(ApprovedOrderData::jobId).containsOnly(jobId);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PAID'",
                proposal.getId())).isEqualTo(1);
        assertThat(count("select count(*) from job_specialties where job_id = ?", jobId)).isEqualTo(2);
    }

    @Test
    @DisplayName("PostgreSQL에서 외부 승인 후 서버 저장이 실패하면 의뢰·결제·제안이 모두 롤백되고 재요청이 조회 결과로 한 번만 복구한다")
    void rollsBackAndRecoversWhenSavingFails() {
        Proposal proposal = saveProposal();
        savePendingPayment(proposal, 1, null);
        givenProviderApproved();
        // 의뢰를 저장한 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("저장 실패");
        }).when(jobService).createAwaitingStartJob(any());

        assertThatThrownBy(() -> approvalService.approve(OWNER_USERNAME, orderId, "pg-token"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from jobs where proposal_id = ?", proposal.getId())).isZero();
        assertThat(paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PENDING)).isPresent();
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.PENDING);

        reset(jobService);
        ApprovedOrderData recovered = approvalService.approve(OWNER_USERNAME, orderId, "pg-token");
        ApprovedOrderData repeated = approvalService.approve(OWNER_USERNAME, orderId, "pg-token");

        assertThat(recovered.jobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(repeated.jobId()).isEqualTo(recovered.jobId());
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposal.getId())).isEqualTo(1);
        // 이미 승인된 주문이므로 카카오페이에 승인을 다시 요청하지 않는다
        verify(kakaoPayClient, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PostgreSQL에서 외부 승인 후 저장이 실패한 주문은 결제를 다시 준비해도 대체되지 않고 그 주문으로 의뢰가 복구된다")
    void recoversPaidOrderInsteadOfSupersedingOnReprepare() {
        Proposal proposal = saveProposal();
        savePendingPayment(proposal, 2, "매장 분위기에 맞춰 주세요.");
        givenProviderApproved();
        // 카카오페이 승인은 성공했지만 서버 저장이 실패해 주문이 PENDING으로 남는다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("저장 실패");
        }).when(jobService).createAwaitingStartJob(any());
        assertThatThrownBy(() -> approvalService.approve(OWNER_USERNAME, orderId, "pg-token"))
                .isInstanceOf(IllegalStateException.class);
        reset(jobService);

        // 사장님이 결제를 다시 준비한다
        assertThatThrownBy(() -> paymentFacade.prepareProposalPayment(
                PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposal.getId(), 0, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PAID));

        // 새 주문과 결제창을 만들지 않고, 이미 결제된 주문과 그 주문의 입력값으로 의뢰를 복구한다
        assertThat(count("select count(*) from payments where proposal_id = ?", proposal.getId())).isEqualTo(1);
        Payment payment = paymentRepository.findByProposalIdAndStatus(proposal.getId(), PaymentStatus.PAID)
                .orElseThrow();
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        Job job = jobRepository.findByProposalId(proposal.getId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(job.getRevisionCount()).isEqualTo(2);
        assertThat(job.getAcceptanceMessage()).isEqualTo("매장 분위기에 맞춰 주세요.");
        assertThat(payment.getJobId()).isEqualTo(job.getId());
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.AWAITING_START);
        verify(kakaoPayClient, never()).ready(any());

        // 기존 주문의 승인 재요청도 거부되지 않고 같은 의뢰를 반환한다
        assertThat(approvalService.approve(OWNER_USERNAME, orderId, "pg-token").jobId()).isEqualTo(job.getId());
    }

    @Test
    @DisplayName("PostgreSQL에서 아직 결제되지 않은 이전 대기 주문은 결제를 다시 준비하면 새 주문으로 대체된다")
    void supersedesUnpaidOrderOnReprepare() {
        Proposal proposal = saveProposal();
        savePendingPayment(proposal, 2, null);
        when(kakaoPayClient.order(TID)).thenReturn(new PaymentResult(
                TID, "TC0ONETIME", orderId, OWNER_USER_ID, 50_000L, "READY", null));
        when(kakaoPayClient.ready(any())).thenReturn(new ReadyResult("TV37000000000000002", "pc", "mobile"));

        PreparePaymentResult result = paymentFacade.prepareProposalPayment(
                PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposal.getId(), 1, null));

        assertThat(result.getOrderId()).isNotEqualTo(orderId);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'SUPERSEDED' "
                + "and order_id = ?", proposal.getId(), orderId)).isEqualTo(1);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PENDING' "
                + "and order_id = ?", proposal.getId(), result.getOrderId())).isEqualTo(1);
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposal.getId())).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생이 작업을 시작하면 의뢰·제안·시작 시각·채팅방이 함께 저장되고 승인 시 확정한 마감일은 유지된다")
    void persistsStart() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);

        ProposalJobStartResult result =
                proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId));

        Job job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getStartedAt()).isNotNull();
        // 시작일(현재)이 최종 마감일 이후인 늦은 시작이어도 마감일을 바꾸지 않는다
        assertThat(job.getStartedAt().toLocalDate()).isAfter(job.getFinalDeadline());
        assertThat(job.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(job.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(result.getJobStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(result.getProposalStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(result.getDraftDeadline()).isEqualTo(job.getDraftDeadline());
        assertThat(result.getFinalDeadline()).isEqualTo(job.getFinalDeadline());
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(jdbcTemplate.queryForObject("select id from chat_rooms where job_id = ?", String.class, jobId))
                .isEqualTo(result.getChatRoomId());
        // 한마디는 조건 확인 데이터로만 남고 자동 채팅 메시지를 만들지 않는다
        assertThat(count("select count(*) from chat_messages where room_id = ?", result.getChatRoomId()))
                .isZero();

        // 승인 재요청은 시작된 의뢰를 그대로 반환하고 상태를 되돌리지 않는다
        ApprovedOrderData repeated = approvalService.approve(OWNER_USERNAME, orderId, "pg-token");
        assertThat(repeated.jobId()).isEqualTo(jobId);
        assertThat(repeated.jobStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.MATCHED);
    }

    @Test
    @DisplayName("PostgreSQL에서 작업 시작을 동시에 요청해도 채팅방은 하나만 만들어지고 모든 요청이 같은 시작 시각과 채팅방을 받는다")
    void createsOneChatRoomForConcurrentStarts() throws Exception {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);

        List<ProposalJobStartResult> results = runConcurrently(3,
                () -> proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId)));

        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isEqualTo(1);
        assertThat(results).extracting(ProposalJobStartResult::getChatRoomId).containsOnly(
                jdbcTemplate.queryForObject("select id from chat_rooms where job_id = ?", String.class, jobId));
        assertThat(results).extracting(ProposalJobStartResult::getStartedAt)
                .containsOnly(jobRepository.findById(jobId).orElseThrow().getStartedAt());
        assertThat(results).extracting(ProposalJobStartResult::getProposalStatus)
                .containsOnly(ProposalStatus.ACCEPTED);
    }

    @Test
    @DisplayName("PostgreSQL에서 채팅방 생성이 실패하면 의뢰와 제안의 전환도 롤백되고 재요청이 한 번만 시작한다")
    void rollsBackStartWhenChatRoomFails() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        doThrow(new IllegalStateException("채팅방 생성 실패")).when(chatRoomService).getOrCreate(anyLong());

        assertThatThrownBy(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId)))
                .isInstanceOf(IllegalStateException.class);

        Job waiting = jobRepository.findById(jobId).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(waiting.getStartedAt()).isNull();
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isZero();

        reset(chatRoomService);
        ProposalJobStartResult first =
                proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId));
        ProposalJobStartResult second =
                proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId));

        assertThat(second.getStartedAt()).isEqualTo(first.getStartedAt());
        assertThat(second.getChatRoomId()).isEqualTo(first.getChatRoomId());
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 학생은 수락 대기 의뢰를 시작할 수 없고 상태가 바뀌지 않는다")
    void rejectsStartByOtherStudent() {
        Proposal proposal = saveProposal();
        Long jobId = approve(proposal);
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(STUDENT_PROFILE_ID + 1).userId(STUDENT_USER_ID).build()));

        assertThatThrownBy(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(STUDENT_USERNAME, jobId)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_START_FORBIDDEN));

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(count("select count(*) from chat_rooms where job_id = ?", jobId)).isZero();
    }

    @Test
    @DisplayName("V37 제약은 제안의 새 상태를 허용하고 제안당 의뢰·대기 결제·승인 결제를 하나로 제한한다")
    void enforcesProposalConstraints() {
        Proposal proposal = saveProposal();
        Long proposalId = proposal.getId();

        assertThat(jdbcTemplate.update("update proposals set status = 'AWAITING_START' where id = ?", proposalId))
                .isEqualTo(1);
        assertViolation("update proposals set status = 'UNKNOWN' where id = ?", proposalId);

        insertJob(proposalId);
        assertThatThrownBy(() -> insertJob(proposalId)).isInstanceOf(DataIntegrityViolationException.class);
        Long jobId = jdbcTemplate.queryForObject("select id from jobs where proposal_id = ?", Long.class, proposalId);

        insertProposalPayment(proposalId, "PENDING", null);
        assertThatThrownBy(() -> insertProposalPayment(proposalId, "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertProposalPayment(proposalId, "PAID", jobId);
        assertThatThrownBy(() -> insertProposalPayment(proposalId, "PAID", jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 대체된 주문은 여러 건 남을 수 있다
        insertProposalPayment(proposalId, "SUPERSEDED", null);
        insertProposalPayment(proposalId, "SUPERSEDED", null);
    }

    @Test
    @DisplayName("V37 제약은 일반 결제의 의뢰·지원서 참조를 필수로 유지하고 제안 결제의 수정 횟수와 승인 후 의뢰 연결을 요구한다")
    void enforcesPaymentReferenceConstraints() {
        Proposal proposal = saveProposal();
        Long proposalId = proposal.getId();

        // 일반 결제: 의뢰 또는 지원서가 없으면 거부
        assertViolation("""
                insert into payments (job_id, job_application_id, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                values (null, 21, ?, ?, 1000, now(), 'READY_FAILED')
                """, OWNER_USER_ID, UUID.randomUUID().toString());
        assertViolation("""
                insert into payments (job_id, job_application_id, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                values (987000001, null, ?, ?, 1000, now(), 'READY_FAILED')
                """, OWNER_USER_ID, UUID.randomUUID().toString());
        // 제안 결제: 수정 횟수가 없거나 음수이면 거부, 지원서를 가리키면 거부
        assertViolation("""
                insert into payments (proposal_id, owner_user_id, order_id, amount, refund_policy_agreed_at, status)
                values (?, ?, ?, 1000, now(), 'READY_FAILED')
                """, proposalId, OWNER_USER_ID, UUID.randomUUID().toString());
        assertViolation("""
                insert into payments (proposal_id, revision_count, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                values (?, -1, ?, ?, 1000, now(), 'READY_FAILED')
                """, proposalId, OWNER_USER_ID, UUID.randomUUID().toString());
        assertViolation("""
                insert into payments (proposal_id, job_application_id, revision_count, owner_user_id, order_id,
                        amount, refund_policy_agreed_at, status)
                values (?, 21, 1, ?, ?, 1000, now(), 'READY_FAILED')
                """, proposalId, OWNER_USER_ID, UUID.randomUUID().toString());
        // 승인된 제안 결제는 의뢰에 연결되어 있어야 한다
        assertThatThrownBy(() -> insertProposalPayment(proposalId, "PAID", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
    }

    private Long approve(Proposal proposal) {
        savePendingPayment(proposal, 1, "잘 부탁드립니다.");
        givenProviderApproved();
        return approvalService.approve(OWNER_USERNAME, orderId, "pg-token").jobId();
    }

    // 카카오페이에서는 이미 승인된 주문으로 조회된다
    private void givenProviderApproved() {
        when(kakaoPayClient.order(TID)).thenReturn(new PaymentResult(
                TID, "TC0ONETIME", orderId, OWNER_USER_ID, 50_000L, "SUCCESS_PAYMENT", APPROVED_AT));
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

    private void savePendingPayment(Proposal proposal, int revisionCount, String messageToStudent) {
        Payment payment = Payment.pendingForProposal(proposal.getId(), OWNER_USER_ID, orderId,
                proposal.getProposedFee(), revisionCount, messageToStudent, Instant.parse("2026-08-31T15:00:00Z"));
        payment.recordKakaoTid(TID);
        paymentRepository.saveAndFlush(payment);
    }

    private void insertJob(Long proposalId) {
        jdbcTemplate.update("""
                insert into jobs (owner_profile_id, title, description, budget, draft_deadline, final_deadline,
                        revision_count, status, selected_student_profile_id, proposal_id)
                values (?, '제약 확인', '설명', 1000, current_date, current_date, 0, 'AWAITING_START', ?, ?)
                """, OWNER_PROFILE_ID, STUDENT_PROFILE_ID, proposalId);
    }

    private void insertProposalPayment(Long proposalId, String status, Long jobId) {
        jdbcTemplate.update("""
                insert into payments (proposal_id, job_id, revision_count, owner_user_id, order_id, amount,
                        refund_policy_agreed_at, status)
                values (?, ?, 1, ?, ?, 1000, now(), ?)
                """, proposalId, jobId, OWNER_USER_ID, UUID.randomUUID().toString(), status);
    }

    private void assertViolation(String sql, Object... args) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private <T> List<T> runConcurrently(int requests, Callable<T> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
