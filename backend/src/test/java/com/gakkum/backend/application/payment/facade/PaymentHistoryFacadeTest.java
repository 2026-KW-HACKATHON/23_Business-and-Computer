package com.gakkum.backend.application.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistorySummaryResult;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.entity.PaymentStatus;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentHistoryFacadeTest {

    private static final String USERNAME = "KAKAO_123";
    private static final String OWNER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final UserService userService = mock(UserService.class);
    private final JobService jobService = mock(JobService.class);
    private final StudentService studentService = mock(StudentService.class);
    private PaymentFacade facade = facadeAt("2026-10-15T00:00:00Z");

    private final List<PaymentHistoryData> payments = new ArrayList<>();
    private final Map<Long, Job> jobsById = new HashMap<>();
    private final Map<Long, JobApplication> applicationsById = new HashMap<>();
    private final Map<Long, Student> studentsById = new HashMap<>();
    private final Map<String, User> studentUsersById = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(OWNER_ID, "김사장", UserRole.OWNER));
        when(paymentService.getPaymentHistory(OWNER_ID)).thenReturn(payments);
        when(jobService.getJobsByIds(anyCollection())).thenReturn(jobsById);
        when(jobService.getJobApplicationsByIds(anyCollection())).thenReturn(applicationsById);
        when(studentService.getStudentProfilesByIds(anyCollection())).thenReturn(studentsById);
        when(userService.getUsersByIds(anyCollection())).thenReturn(studentUsersById);
        student(STUDENT_PROFILE_ID, STUDENT_USER_ID, "김학생");
    }

    @Test
    @DisplayName("인증된 사장님 본인의 사용자 ID로만 결제 내역을 조회한다")
    void queriesOnlyOwnPayments() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);

        facade.getPaymentHistory(USERNAME);

        verify(paymentService).getPaymentHistory(OWNER_ID);
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("사장님이 아니면 결제 내역을 조회하지 않고 403 오류로 거부한다")
    void rejectsNonOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(STUDENT_USER_ID, "김학생", UserRole.STUDENT));

        assertError(ErrorCode.PAYMENT_LIST_OWNER_REQUIRED);
        verifyNoInteractions(paymentService, jobService, studentService);
    }

    @Test
    @DisplayName("잠긴 사용자는 결제 내역을 조회하지 않고 401 오류로 거부한다")
    void rejectsLockedUser() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(paymentService, jobService, studentService);
    }

    @Test
    @DisplayName("결제 내역이 없으면 빈 월 목록을 반환하고 연관 데이터를 조회하지 않는다")
    void returnsEmptyMonthsWithoutRelatedQueries() {
        PaymentHistoryResult result = facade.getPaymentHistory(USERNAME);

        assertThat(result.getMonths()).isEmpty();
        verifyNoInteractions(jobService, studentService);
    }

    @Test
    @DisplayName("결제·의뢰 상태에 따라 보관중·정산 완료·부분 환불과 최초 결제·환불 금액을 반환한다")
    void calculatesDisplayStatusAndAmounts() {
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.CLOSED);
        refunded(41L, "2026-10-01T03:00:00Z", "2026-10-01T04:00:00Z", JobStatus.CANCELLED);

        List<PaymentHistoryItemResult> items = facade.getPaymentHistory(USERNAME).getMonths().get(0).getPayments();

        assertThat(items).extracting(PaymentHistoryItemResult::getStatus).containsExactly(
                PaymentHistoryStatus.HELD, PaymentHistoryStatus.SETTLED, PaymentHistoryStatus.PARTIALLY_REFUNDED);
        assertThat(items).extracting(PaymentHistoryItemResult::getAmount)
                .containsExactly(100_000L, 100_000L, 100_000L);
        assertThat(items).extracting(PaymentHistoryItemResult::getRefundAmount).containsExactly(0L, 0L, 80_000L);
        assertThat(items.get(0).getJobId()).isEqualTo(43L);
        assertThat(items.get(0).getTitle()).isEqualTo("의뢰 43");
        assertThat(items.get(0).getStudentName()).isEqualTo("김학생");
        assertThat(items.get(0).getApprovedAt()).isEqualTo(Instant.parse("2026-10-03T03:00:00Z"));
    }

    @Test
    @DisplayName("월말 UTC 승인 시각은 한국 시간 기준 월로 묶고 연도가 바뀌어도 최신 월부터 반환한다")
    void groupsByKoreanMonthAcrossMonthAndYearBoundaries() {
        paid(44L, "2026-09-30T15:00:00Z", JobStatus.MATCHED);
        paid(43L, "2026-09-30T14:59:59Z", JobStatus.MATCHED);
        paid(42L, "2025-12-31T15:00:00Z", JobStatus.CLOSED);
        paid(41L, "2025-12-31T14:59:59Z", JobStatus.CLOSED);

        List<PaymentHistoryMonthResult> months = facade.getPaymentHistory(USERNAME).getMonths();

        assertThat(months).extracting(PaymentHistoryMonthResult::getYearMonth)
                .containsExactly("2026-10", "2026-09", "2026-01", "2025-12");
        assertThat(months).extracting(month -> month.getPayments().get(0).getJobId())
                .containsExactly(44L, 43L, 42L, 41L);
        assertThat(months).allSatisfy(month -> assertThat(month.getPayments()).hasSize(1));
    }

    @Test
    @DisplayName("같은 월의 결제는 조회된 정렬 순서를 유지하며 승인 시각이 같아도 순서를 바꾸지 않는다")
    void keepsQueryOrderWithinMonth() {
        paid(43L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        paid(41L, "2026-10-01T03:00:00Z", JobStatus.MATCHED);

        List<PaymentHistoryMonthResult> months = facade.getPaymentHistory(USERNAME).getMonths();

        assertThat(months).hasSize(1);
        assertThat(months.get(0).getPayments()).extracting(PaymentHistoryItemResult::getJobId)
                .containsExactly(43L, 42L, 41L);
    }

    @Test
    @DisplayName("다음 달에 환불된 결제도 최초 결제 승인 월에 유지한다")
    void keepsRefundedPaymentInApprovalMonth() {
        refunded(41L, "2026-09-20T03:00:00Z", "2026-10-05T03:00:00Z", JobStatus.CANCELLED);

        List<PaymentHistoryMonthResult> months = facade.getPaymentHistory(USERNAME).getMonths();

        assertThat(months).extracting(PaymentHistoryMonthResult::getYearMonth).containsExactly("2026-09");
        assertThat(months.get(0).getPayments().get(0).getStatus())
                .isEqualTo(PaymentHistoryStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("의뢰·지원서·학생·사용자를 중복 없는 ID 목록으로 한 번씩만 일괄 조회한다")
    void loadsRelatedDataInBatches() {
        student(8L, "01K58M6PJV8VAJMXHBHJ2PNB5E", "이학생");
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        add(payment(41L, "2026-10-01T03:00:00Z"), JobStatus.MATCHED, 8L);

        List<PaymentHistoryItemResult> items = facade.getPaymentHistory(USERNAME).getMonths().get(0).getPayments();

        assertThat(items).extracting(PaymentHistoryItemResult::getStudentName)
                .containsExactly("김학생", "김학생", "이학생");
        verify(jobService).getJobsByIds(List.of(43L, 42L, 41L));
        verify(jobService).getJobApplicationsByIds(List.of(1043L, 1042L, 1041L));
        verify(studentService).getStudentProfilesByIds(
                ArgumentMatchers.<List<Long>>argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(7L, 8L))));
        verify(userService).getUsersByIds(ArgumentMatchers.<List<String>>argThat(ids -> ids.size() == 2));
        verifyNoMoreInteractions(jobService, studentService);
    }

    @Test
    @DisplayName("참조하는 의뢰가 없으면 내역을 누락하지 않고 서버 오류로 처리한다")
    void rejectsMissingReference() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        when(jobService.getJobsByIds(anyCollection()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("지원서가 결제와 다른 의뢰의 지원서이면 서버 오류로 처리한다")
    void rejectsApplicationOfAnotherJob() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        applicationsById.put(1042L, application(1042L, 99L, STUDENT_PROFILE_ID));

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("결제 상태와 의뢰 상태 조합이 맞지 않으면 서버 오류로 처리한다")
    void rejectsInvalidStatusCombination() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);
        for (JobStatus invalid : List.of(JobStatus.OPEN, JobStatus.CANCELLED)) {
            jobsById.put(42L, job(42L, invalid));
            assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        payments.clear();
        refunded(41L, "2026-10-01T03:00:00Z", "2026-10-01T04:00:00Z", JobStatus.CANCELLED);
        for (JobStatus invalid : List.of(JobStatus.OPEN, JobStatus.MATCHED, JobStatus.CLOSED)) {
            jobsById.put(41L, job(41L, invalid));
            assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("환불된 결제에 환불 금액이 없으면 0으로 대체하지 않고 서버 오류로 처리한다")
    void rejectsRefundedPaymentWithoutRefundAmount() {
        Payment broken = mock(Payment.class);
        when(broken.getJobId()).thenReturn(41L);
        when(broken.getJobApplicationId()).thenReturn(1041L);
        when(broken.getAmount()).thenReturn(100_000L);
        when(broken.getRefundAmount()).thenReturn(null);
        when(broken.getStatus()).thenReturn(PaymentStatus.REFUNDED);
        when(broken.getApprovedAt()).thenReturn(Instant.parse("2026-10-01T03:00:00Z"));
        add(broken, JobStatus.CANCELLED, STUDENT_PROFILE_ID);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("요약은 이번 달 최초 결제액과 전 기간 보관·정산 완료 금액을 합산한다")
    void summarizesThisMonthHeldAndSettledAmounts() {
        paid(45L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, 100_000L);
        paid(44L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, 70_000L);
        refunded(43L, "2026-10-01T03:00:00Z", "2026-10-01T04:00:00Z", JobStatus.CANCELLED);
        paid(42L, "2026-09-10T03:00:00Z", JobStatus.MATCHED, 50_000L);
        paid(41L, "2026-08-10T03:00:00Z", JobStatus.CLOSED, 30_000L);

        PaymentHistorySummaryResult summary = facade.getPaymentHistory(USERNAME).getSummary();

        // 부분 환불 건(10만 원)은 환불액을 빼지 않고 이번 달 결제액에만 포함된다
        assertThat(summary.getThisMonthPaymentAmount()).isEqualTo(270_000L);
        assertThat(summary.getHeldAmount()).isEqualTo(150_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("지난달 승인되어 이번 달 환불된 결제는 이번 달 결제액에 포함하지 않는다")
    void excludesPaymentRefundedThisMonthFromThisMonthAmount() {
        refunded(41L, "2026-09-20T03:00:00Z", "2026-10-05T03:00:00Z", JobStatus.CANCELLED);

        PaymentHistorySummaryResult summary = facade.getPaymentHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthPaymentAmount()).isZero();
        assertThat(summary.getHeldAmount()).isZero();
        assertThat(summary.getTotalSettledAmount()).isZero();
    }

    @Test
    @DisplayName("이번 달은 한국 시간 기준이며 월말 UTC 시각의 현재 시각과 승인 시각을 한국 시간 월로 판정한다")
    void decidesThisMonthInKoreanTimeAtMonthBoundary() {
        // 현재 시각: 한국 시간 2026-10-01 00:00:00
        facade = facadeAt("2026-09-30T15:00:00Z");
        paid(42L, "2026-09-30T15:00:00Z", JobStatus.MATCHED, 100_000L);
        paid(41L, "2026-09-30T14:59:59Z", JobStatus.MATCHED, 50_000L);

        assertThat(facade.getPaymentHistory(USERNAME).getSummary().getThisMonthPaymentAmount())
                .isEqualTo(100_000L);

        // 현재 시각: 한국 시간 2026-09-30 23:59:59
        facade = facadeAt("2026-09-30T14:59:59Z");

        assertThat(facade.getPaymentHistory(USERNAME).getSummary().getThisMonthPaymentAmount())
                .isEqualTo(50_000L);
    }

    @Test
    @DisplayName("연말 UTC 시각도 한국 시간 기준 새해 1월로 판정하고 작년 같은 달과 섞지 않는다")
    void decidesThisMonthInKoreanTimeAtYearBoundary() {
        // 현재 시각: 한국 시간 2026-01-01 00:00:00
        facade = facadeAt("2025-12-31T15:00:00Z");
        paid(43L, "2025-12-31T15:00:00Z", JobStatus.MATCHED, 100_000L);
        paid(42L, "2025-12-31T14:59:59Z", JobStatus.MATCHED, 50_000L);
        paid(41L, "2025-01-15T03:00:00Z", JobStatus.CLOSED, 30_000L);

        PaymentHistorySummaryResult summary = facade.getPaymentHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthPaymentAmount()).isEqualTo(100_000L);
        assertThat(summary.getHeldAmount()).isEqualTo(150_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("결제 내역이 없거나 이번 달 결제가 없으면 해당 요약 금액은 0이다")
    void returnsZeroSummaryWithoutPayments() {
        PaymentHistorySummaryResult empty = facade.getPaymentHistory(USERNAME).getSummary();

        assertThat(empty.getThisMonthPaymentAmount()).isZero();
        assertThat(empty.getHeldAmount()).isZero();
        assertThat(empty.getTotalSettledAmount()).isZero();

        paid(41L, "2026-09-10T03:00:00Z", JobStatus.MATCHED, 50_000L);
        PaymentHistorySummaryResult noneThisMonth = facade.getPaymentHistory(USERNAME).getSummary();

        assertThat(noneThisMonth.getThisMonthPaymentAmount()).isZero();
        assertThat(noneThisMonth.getHeldAmount()).isEqualTo(50_000L);
        assertThat(noneThisMonth.getTotalSettledAmount()).isZero();
    }

    @Test
    @DisplayName("요약 금액 합계가 Integer 범위를 넘어도 정확하게 합산한다")
    void summarizesAmountsBeyondIntegerRange() {
        paid(44L, "2026-10-04T03:00:00Z", JobStatus.MATCHED, 2_000_000_000L);
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, 2_000_000_000L);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, 2_000_000_000L);
        paid(41L, "2026-10-01T03:00:00Z", JobStatus.CLOSED, 2_000_000_000L);

        PaymentHistorySummaryResult summary = facade.getPaymentHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthPaymentAmount()).isEqualTo(8_000_000_000L);
        assertThat(summary.getHeldAmount()).isEqualTo(4_000_000_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(4_000_000_000L);
    }

    private PaymentFacade facadeAt(String now) {
        return new PaymentFacade(mock(PaymentPreparationService.class),
                mock(KakaoPayClient.class), paymentService, mock(PaymentApprovalService.class),
                userService, jobService, studentService, mock(OwnerService.class),
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> facade.getPaymentHistory(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void paid(Long jobId, String approvedAt, JobStatus jobStatus) {
        paid(jobId, approvedAt, jobStatus, 100_000L);
    }

    private void paid(Long jobId, String approvedAt, JobStatus jobStatus, Long amount) {
        add(payment(jobId, approvedAt, amount), jobStatus, STUDENT_PROFILE_ID);
    }

    private void refunded(Long jobId, String approvedAt, String refundedAt, JobStatus jobStatus) {
        Payment payment = payment(jobId, approvedAt);
        payment.refundOnCancel(Instant.parse(refundedAt));
        add(payment, jobStatus, STUDENT_PROFILE_ID);
    }

    // 지원서 ID는 의뢰 ID + 1000으로 둔다
    private Payment payment(Long jobId, String approvedAt) {
        return payment(jobId, approvedAt, 100_000L);
    }

    private Payment payment(Long jobId, String approvedAt, Long amount) {
        Payment payment = Payment.pending(jobId, jobId + 1000, OWNER_ID, "order-" + jobId, amount, Instant.EPOCH);
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse(approvedAt));
        return payment;
    }

    private void add(Payment payment, JobStatus jobStatus, Long studentProfileId) {
        payments.add(PaymentHistoryData.from(payment));
        jobsById.put(payment.getJobId(), job(payment.getJobId(), jobStatus));
        applicationsById.put(payment.getJobApplicationId(),
                application(payment.getJobApplicationId(), payment.getJobId(), studentProfileId));
    }

    private Job job(Long id, JobStatus status) {
        return Job.builder().id(id).title("의뢰 " + id).status(status).build();
    }

    private JobApplication application(Long id, Long jobId, Long studentProfileId) {
        return JobApplication.builder().id(id).jobId(jobId).studentProfileId(studentProfileId).build();
    }

    private void student(Long studentProfileId, String userId, String name) {
        studentsById.put(studentProfileId, Student.builder().id(studentProfileId).userId(userId).build());
        studentUsersById.put(userId, user(userId, name, UserRole.STUDENT));
    }

    private User user(String id, String name, UserRole role) {
        return User.builder().id(id).name(name).role(role).isLock(false).build();
    }

    // 지원서 없이 제안 결제로 만들어진 의뢰. 담당 학생은 의뢰에서 읽는다
    private void proposalPaid(Long jobId, Long proposalId, String approvedAt, JobStatus jobStatus, Long amount) {
        Payment payment = Payment.pendingForProposal(proposalId, OWNER_ID, "order-" + jobId, amount, 2, null,
                Instant.EPOCH);
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse(approvedAt));
        payment.linkJob(jobId);
        payments.add(PaymentHistoryData.from(payment));
        jobsById.put(jobId, Job.builder().id(jobId).title("의뢰 " + jobId).status(jobStatus)
                .proposalId(proposalId).selectedStudentProfileId(STUDENT_PROFILE_ID).build());
    }

    @Test
    @DisplayName("학생의 작업 시작을 기다리는 제안 결제는 지원서 없이 의뢰의 담당 학생 이름과 함께 보관 중(HELD)으로 반환하고 보관 금액에 합산한다")
    void returnsAwaitingStartProposalPaymentAsHeld() {
        proposalPaid(51L, 5L, "2026-10-03T03:00:00Z", JobStatus.AWAITING_START, 50_000L);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED);

        PaymentHistoryResult result = facade.getPaymentHistory(USERNAME);

        List<PaymentHistoryItemResult> items = result.getMonths().get(0).getPayments();
        assertThat(items).extracting(PaymentHistoryItemResult::getJobId, PaymentHistoryItemResult::getStatus,
                        PaymentHistoryItemResult::getStudentName)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(51L, PaymentHistoryStatus.HELD, "김학생"),
                        org.assertj.core.api.Assertions.tuple(42L, PaymentHistoryStatus.HELD, "김학생"));
        assertThat(result.getSummary().getHeldAmount()).isEqualTo(150_000L);
        assertThat(result.getSummary().getTotalSettledAmount()).isZero();
    }

    @Test
    @DisplayName("작업을 시작했거나 완료된 제안 결제도 일반 결제와 같은 기준으로 보관 중·정산 완료를 반환한다")
    void returnsStartedAndClosedProposalPayments() {
        proposalPaid(52L, 6L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, 50_000L);
        proposalPaid(51L, 5L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, 70_000L);

        PaymentHistoryResult result = facade.getPaymentHistory(USERNAME);

        assertThat(result.getMonths().get(0).getPayments()).extracting(PaymentHistoryItemResult::getStatus)
                .containsExactly(PaymentHistoryStatus.HELD, PaymentHistoryStatus.SETTLED);
        assertThat(result.getSummary().getHeldAmount()).isEqualTo(50_000L);
        assertThat(result.getSummary().getTotalSettledAmount()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("제안 결제가 가리키는 제안이 의뢰를 만든 제안이 아니면 서버 오류로 처리한다")
    void rejectsProposalPaymentLinkedToOtherProposal() {
        proposalPaid(51L, 5L, "2026-10-03T03:00:00Z", JobStatus.AWAITING_START, 50_000L);
        jobsById.put(51L, Job.builder().id(51L).title("의뢰 51").status(JobStatus.AWAITING_START)
                .proposalId(9L).selectedStudentProfileId(STUDENT_PROFILE_ID).build());

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
