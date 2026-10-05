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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
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

class SettlementHistoryFacadeTest {

    private static final String USERNAME = "KAKAO_123";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final Long STUDENT_PROFILE_ID = 7L;
    private static final Long OWNER_PROFILE_ID = 3L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final PaymentService paymentService = mock(PaymentService.class);
    private final UserService userService = mock(UserService.class);
    private final JobService jobService = mock(JobService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private PaymentFacade facade = facadeAt("2026-10-15T00:00:00Z");

    private final List<SettlementHistoryData> payments = new ArrayList<>();
    private final Map<Long, Job> jobsById = new HashMap<>();
    private final Map<Long, JobApplication> applicationsById = new HashMap<>();
    private final Map<Long, String> storeNames = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.STUDENT));
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(
                Optional.of(Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
        when(jobService.getJobsBySelectedStudentProfileId(STUDENT_PROFILE_ID)).thenReturn(jobsById);
        when(paymentService.getSettlementHistory(anyCollection())).thenReturn(payments);
        when(jobService.getJobApplicationsByIds(anyCollection())).thenReturn(applicationsById);
        when(ownerService.getStoreNames(anyCollection())).thenReturn(storeNames);
        storeNames.put(OWNER_PROFILE_ID, "가꿈 카페");
    }

    @Test
    @DisplayName("인증된 학생 본인이 담당하는 의뢰 ID로만 정산 내역을 조회한다")
    void queriesOnlyOwnJobs() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);

        facade.getSettlementHistory(USERNAME);

        verify(jobService).getJobsBySelectedStudentProfileId(STUDENT_PROFILE_ID);
        verify(paymentService).getSettlementHistory(Set.of(42L));
        verifyNoMoreInteractions(paymentService);
    }

    @Test
    @DisplayName("학생이 아니면 정산 내역을 조회하지 않고 403 오류로 거부한다")
    void rejectsNonStudent() {
        when(userService.getActiveUser(USERNAME)).thenReturn(user(UserRole.OWNER));

        assertError(ErrorCode.SETTLEMENT_LIST_STUDENT_REQUIRED);
        verifyNoInteractions(paymentService, jobService, studentService, ownerService);
    }

    @Test
    @DisplayName("잠긴 사용자는 정산 내역을 조회하지 않고 401 오류로 거부한다")
    void rejectsLockedUser() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(paymentService, jobService, studentService, ownerService);
    }

    @Test
    @DisplayName("학생 프로필이 없으면 서버 오류로 처리한다")
    void rejectsMissingStudentProfile() {
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(paymentService, jobService, ownerService);
    }

    @Test
    @DisplayName("정산 내역이 없으면 빈 월 목록을 반환하고 의뢰와 매장을 조회하지 않는다")
    void returnsEmptyMonthsWithoutRelatedQueries() {
        SettlementHistoryResult result = facade.getSettlementHistory(USERNAME);

        assertThat(result.getMonths()).isEmpty();
        verify(jobService).getJobsBySelectedStudentProfileId(STUDENT_PROFILE_ID);
        verifyNoMoreInteractions(jobService);
        verifyNoInteractions(ownerService);
    }

    @Test
    @DisplayName("결제·의뢰 상태에 따라 정산 예정·정산 완료·착수 보상의 상태와 수령액, 정산 날짜를 반환한다")
    void calculatesStatusAmountAndSettledDate() {
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, null);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, "2026-10-10T09:30:00");
        // 환불 처리 시각은 UTC로 10월 4일이지만 한국 시간으로는 10월 5일이다
        refunded(41L, "2026-10-01T03:00:00Z", "2026-10-04T15:00:00Z", JobStatus.CANCELLED);

        List<SettlementHistoryItemResult> items =
                facade.getSettlementHistory(USERNAME).getMonths().get(0).getSettlements();

        assertThat(items).extracting(SettlementHistoryItemResult::getStatus).containsExactly(
                SettlementHistoryStatus.SCHEDULED, SettlementHistoryStatus.SETTLED,
                SettlementHistoryStatus.START_COMPENSATION);
        assertThat(items).extracting(SettlementHistoryItemResult::getAmount)
                .containsExactly(100_000L, 100_000L, 20_000L);
        assertThat(items).extracting(SettlementHistoryItemResult::getSettledDate)
                .containsExactly(null, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 5));
        assertThat(items.get(0).getJobId()).isEqualTo(43L);
        assertThat(items.get(0).getTitle()).isEqualTo("의뢰 43");
        assertThat(items.get(0).getStoreName()).isEqualTo("가꿈 카페");
    }

    @Test
    @DisplayName("착수 보상은 원결제 금액으로 다시 계산하지 않고 저장된 학생 보상금을 그대로 반환한다")
    void returnsStoredCompensationWithoutRecalculation() {
        // 원결제 100,000원의 20%(20,000원)와 다른 값이 저장된 경우
        add(refundedStub(12_345L, Instant.parse("2026-10-01T04:00:00Z")), JobStatus.CANCELLED,
                "2026-10-01T13:00:00");

        SettlementHistoryItemResult item =
                facade.getSettlementHistory(USERNAME).getMonths().get(0).getSettlements().get(0);

        assertThat(item.getStatus()).isEqualTo(SettlementHistoryStatus.START_COMPENSATION);
        assertThat(item.getAmount()).isEqualTo(12_345L);
    }

    @Test
    @DisplayName("월말 UTC 승인 시각은 한국 시간 기준 월로 묶고 연도가 바뀌어도 최신 월부터 반환한다")
    void groupsByKoreanMonthAcrossMonthAndYearBoundaries() {
        paid(44L, "2026-09-30T15:00:00Z", JobStatus.MATCHED, null);
        paid(43L, "2026-09-30T14:59:59Z", JobStatus.MATCHED, null);
        paid(42L, "2025-12-31T15:00:00Z", JobStatus.MATCHED, null);
        paid(41L, "2025-12-31T14:59:59Z", JobStatus.MATCHED, null);

        List<SettlementHistoryMonthResult> months = facade.getSettlementHistory(USERNAME).getMonths();

        assertThat(months).extracting(SettlementHistoryMonthResult::getYearMonth)
                .containsExactly("2026-10", "2026-09", "2026-01", "2025-12");
        assertThat(months).extracting(month -> month.getSettlements().get(0).getJobId())
                .containsExactly(44L, 43L, 42L, 41L);
        assertThat(months).allSatisfy(month -> assertThat(month.getSettlements()).hasSize(1));
    }

    @Test
    @DisplayName("같은 월의 정산은 조회된 정렬 순서를 유지하며 승인 시각이 같아도 순서를 바꾸지 않는다")
    void keepsQueryOrderWithinMonth() {
        paid(43L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        paid(41L, "2026-10-01T03:00:00Z", JobStatus.MATCHED, null);

        List<SettlementHistoryMonthResult> months = facade.getSettlementHistory(USERNAME).getMonths();

        assertThat(months).hasSize(1);
        assertThat(months.get(0).getSettlements()).extracting(SettlementHistoryItemResult::getJobId)
                .containsExactly(43L, 42L, 41L);
    }

    @Test
    @DisplayName("다음 달에 완료되거나 취소된 의뢰도 결제 승인 월에 유지한다")
    void keepsSettlementInApprovalMonth() {
        paid(42L, "2026-09-25T03:00:00Z", JobStatus.CLOSED, "2026-10-08T12:00:00");
        refunded(41L, "2026-09-20T03:00:00Z", "2026-10-05T03:00:00Z", JobStatus.CANCELLED);

        List<SettlementHistoryMonthResult> months = facade.getSettlementHistory(USERNAME).getMonths();

        assertThat(months).extracting(SettlementHistoryMonthResult::getYearMonth).containsExactly("2026-09");
        assertThat(months.get(0).getSettlements()).extracting(SettlementHistoryItemResult::getSettledDate)
                .containsExactly(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("의뢰와 매장 이름을 중복 없는 ID 목록으로 한 번씩만 일괄 조회한다")
    void loadsRelatedDataInBatches() {
        storeNames.put(4L, "다른 매장");
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, null);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        paid(41L, "2026-10-01T03:00:00Z", JobStatus.MATCHED, null);
        jobsById.put(41L, job(41L, JobStatus.MATCHED, null, 4L, STUDENT_PROFILE_ID));

        List<SettlementHistoryItemResult> items =
                facade.getSettlementHistory(USERNAME).getMonths().get(0).getSettlements();

        assertThat(items).extracting(SettlementHistoryItemResult::getStoreName)
                .containsExactly("가꿈 카페", "가꿈 카페", "다른 매장");
        verify(jobService).getJobsBySelectedStudentProfileId(STUDENT_PROFILE_ID);
        verify(jobService).getJobApplicationsByIds(List.of(1043L, 1042L, 1041L));
        verify(ownerService).getStoreNames(
                org.mockito.ArgumentMatchers.<List<Long>>argThat(ids -> ids.size() == 2
                        && ids.containsAll(List.of(OWNER_PROFILE_ID, 4L))));
        verifyNoMoreInteractions(jobService, ownerService);
    }

    @Test
    @DisplayName("참조하는 의뢰나 매장이 없으면 내역을 누락하지 않고 서버 오류로 처리한다")
    void rejectsMissingReference() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        when(ownerService.getStoreNames(anyCollection()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        assertError(ErrorCode.INTERNAL_SERVER_ERROR);

        when(jobService.getJobsByIds(anyCollection()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("지원서가 결제와 다른 의뢰의 지원서이면 서버 오류로 처리한다")
    void rejectsApplicationOfAnotherJob() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        applicationsById.put(1042L, application(1042L, 99L));

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("결제가 본인 지원서가 아닌 지원서를 가리키면 서버 오류로 처리한다")
    void rejectsPaymentOfUnknownApplication() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        applicationsById.remove(1042L);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("의뢰에 선택된 학생이 본인이 아니거나 없으면 서버 오류로 처리한다")
    void rejectsJobSelectedForAnotherStudent() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        for (Long selected : new Long[] {8L, null}) {
            jobsById.put(42L, job(42L, JobStatus.MATCHED, null, OWNER_PROFILE_ID, selected));
            assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("결제 상태와 의뢰 상태 조합이 맞지 않으면 서버 오류로 처리한다")
    void rejectsInvalidStatusCombination() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        for (JobStatus invalid : List.of(JobStatus.OPEN, JobStatus.CANCELLED)) {
            jobsById.put(42L, job(42L, invalid, "2026-10-03T00:00:00", OWNER_PROFILE_ID, STUDENT_PROFILE_ID));
            assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        payments.clear();
        refunded(41L, "2026-10-01T03:00:00Z", "2026-10-01T04:00:00Z", JobStatus.CANCELLED);
        for (JobStatus invalid : List.of(JobStatus.OPEN, JobStatus.MATCHED, JobStatus.CLOSED)) {
            jobsById.put(41L, job(41L, invalid, "2026-10-03T00:00:00", OWNER_PROFILE_ID, STUDENT_PROFILE_ID));
            assertError(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("완료된 의뢰에 완료 시각이 없으면 서버 오류로 처리한다")
    void rejectsClosedJobWithoutCompletedAt() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, null);

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("환불된 결제에 학생 보상금이나 환불 시각이 없으면 대체값 없이 서버 오류로 처리한다")
    void rejectsRefundedPaymentWithoutCompensationOrRefundedAt() {
        add(refundedStub(null, Instant.parse("2026-10-01T04:00:00Z")), JobStatus.CANCELLED, "2026-10-01T13:00:00");
        assertError(ErrorCode.INTERNAL_SERVER_ERROR);

        payments.clear();
        add(refundedStub(20_000L, null), JobStatus.CANCELLED, "2026-10-01T13:00:00");
        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("요약은 이번 달 승인 건의 수령액과 전 기간 정산 예정·정산 완료 금액을 합산한다")
    void summarizesThisMonthScheduledAndSettledAmounts() {
        paid(45L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, null, 100_000L);
        paid(44L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, "2026-10-10T09:30:00", 70_000L);
        refunded(43L, "2026-10-01T03:00:00Z", "2026-10-01T04:00:00Z", JobStatus.CANCELLED);
        paid(42L, "2026-09-10T03:00:00Z", JobStatus.MATCHED, null, 50_000L);
        paid(41L, "2026-08-10T03:00:00Z", JobStatus.CLOSED, "2026-08-20T09:30:00", 30_000L);

        SettlementHistorySummaryResult summary = facade.getSettlementHistory(USERNAME).getSummary();

        // 취소 건(10만 원 결제)은 원결제 금액이 아니라 착수 보상 2만 원으로 합산한다
        assertThat(summary.getThisMonthWorkAmount()).isEqualTo(190_000L);
        assertThat(summary.getScheduledAmount()).isEqualTo(150_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("요약의 착수 보상은 다시 계산하지 않고 저장된 학생 보상금으로 합산한다")
    void summarizesStoredCompensationWithoutRecalculation() {
        add(refundedStub(12_345L, Instant.parse("2026-10-01T04:00:00Z")), JobStatus.CANCELLED,
                "2026-10-01T13:00:00");

        SettlementHistorySummaryResult summary = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthWorkAmount()).isEqualTo(12_345L);
        assertThat(summary.getScheduledAmount()).isZero();
        assertThat(summary.getTotalSettledAmount()).isEqualTo(12_345L);
    }

    @Test
    @DisplayName("지난달 승인되어 이번 달 완료·보상된 건은 이번 달 작업비에서 빼고 정산 완료 합계에는 포함한다")
    void excludesLastMonthApprovalFromThisMonthAmount() {
        paid(42L, "2026-09-25T03:00:00Z", JobStatus.CLOSED, "2026-10-08T12:00:00", 70_000L);
        refunded(41L, "2026-09-20T03:00:00Z", "2026-10-05T03:00:00Z", JobStatus.CANCELLED);

        SettlementHistorySummaryResult summary = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthWorkAmount()).isZero();
        assertThat(summary.getScheduledAmount()).isZero();
        assertThat(summary.getTotalSettledAmount()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("이번 달은 한국 시간 기준이며 월말 UTC 시각의 현재 시각과 승인 시각을 한국 시간 월로 판정한다")
    void decidesThisMonthInKoreanTimeAtMonthBoundary() {
        // 현재 시각: 한국 시간 2026-10-01 00:00:00
        facade = facadeAt("2026-09-30T15:00:00Z");
        paid(42L, "2026-09-30T15:00:00Z", JobStatus.MATCHED, null, 100_000L);
        paid(41L, "2026-09-30T14:59:59Z", JobStatus.MATCHED, null, 50_000L);

        assertThat(facade.getSettlementHistory(USERNAME).getSummary().getThisMonthWorkAmount())
                .isEqualTo(100_000L);

        // 현재 시각: 한국 시간 2026-09-30 23:59:59
        facade = facadeAt("2026-09-30T14:59:59Z");

        assertThat(facade.getSettlementHistory(USERNAME).getSummary().getThisMonthWorkAmount())
                .isEqualTo(50_000L);
    }

    @Test
    @DisplayName("연말 UTC 시각도 한국 시간 기준 새해 1월로 판정하고 작년 같은 달과 섞지 않는다")
    void decidesThisMonthInKoreanTimeAtYearBoundary() {
        // 현재 시각: 한국 시간 2026-01-01 00:00:00
        facade = facadeAt("2025-12-31T15:00:00Z");
        paid(43L, "2025-12-31T15:00:00Z", JobStatus.MATCHED, null, 100_000L);
        paid(42L, "2025-12-31T14:59:59Z", JobStatus.MATCHED, null, 50_000L);
        paid(41L, "2025-01-15T03:00:00Z", JobStatus.CLOSED, "2025-01-20T09:00:00", 30_000L);

        SettlementHistorySummaryResult summary = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthWorkAmount()).isEqualTo(100_000L);
        assertThat(summary.getScheduledAmount()).isEqualTo(150_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("정산 내역이 없거나 이번 달 승인 건이 없으면 해당 요약 금액은 0이다")
    void returnsZeroSummaryWithoutSettlements() {
        SettlementHistorySummaryResult empty = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(empty.getThisMonthWorkAmount()).isZero();
        assertThat(empty.getScheduledAmount()).isZero();
        assertThat(empty.getTotalSettledAmount()).isZero();

        paid(41L, "2026-09-10T03:00:00Z", JobStatus.MATCHED, null, 50_000L);
        SettlementHistorySummaryResult noneThisMonth = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(noneThisMonth.getThisMonthWorkAmount()).isZero();
        assertThat(noneThisMonth.getScheduledAmount()).isEqualTo(50_000L);
        assertThat(noneThisMonth.getTotalSettledAmount()).isZero();
    }

    @Test
    @DisplayName("요약 금액 합계가 Integer 범위를 넘어도 정확하게 합산한다")
    void summarizesAmountsBeyondIntegerRange() {
        paid(44L, "2026-10-04T03:00:00Z", JobStatus.MATCHED, null, 2_000_000_000L);
        paid(43L, "2026-10-03T03:00:00Z", JobStatus.MATCHED, null, 2_000_000_000L);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, "2026-10-10T09:30:00", 2_000_000_000L);
        paid(41L, "2026-10-01T03:00:00Z", JobStatus.CLOSED, "2026-10-10T09:30:00", 2_000_000_000L);

        SettlementHistorySummaryResult summary = facade.getSettlementHistory(USERNAME).getSummary();

        assertThat(summary.getThisMonthWorkAmount()).isEqualTo(8_000_000_000L);
        assertThat(summary.getScheduledAmount()).isEqualTo(4_000_000_000L);
        assertThat(summary.getTotalSettledAmount()).isEqualTo(4_000_000_000L);
    }

    private PaymentFacade facadeAt(String now) {
        return new PaymentFacade(mock(PaymentPreparationService.class),
                mock(KakaoPayClient.class), paymentService, mock(PaymentApprovalService.class),
                userService, jobService, studentService, ownerService,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> facade.getSettlementHistory(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void paid(Long jobId, String approvedAt, JobStatus jobStatus, String completedAt) {
        paid(jobId, approvedAt, jobStatus, completedAt, 100_000L);
    }

    private void paid(Long jobId, String approvedAt, JobStatus jobStatus, String completedAt, Long amount) {
        add(payment(jobId, approvedAt, amount), jobStatus, completedAt);
    }

    private void refunded(Long jobId, String approvedAt, String refundedAt, JobStatus jobStatus) {
        Payment payment = payment(jobId, approvedAt);
        payment.refundOnCancel(Instant.parse(refundedAt));
        add(payment, jobStatus, "2026-10-01T00:00:00");
    }

    // 지원서 ID는 의뢰 ID + 1000으로 둔다
    private Payment payment(Long jobId, String approvedAt) {
        return payment(jobId, approvedAt, 100_000L);
    }

    private Payment payment(Long jobId, String approvedAt, Long amount) {
        Payment payment = Payment.pending(jobId, jobId + 1000, OWNER_USER_ID, "order-" + jobId, amount,
                Instant.EPOCH);
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse(approvedAt));
        return payment;
    }

    // 원결제 100,000원인 환불 결제. 보상금과 환불 시각을 엔티티 계산과 무관하게 지정한다
    private Payment refundedStub(Long studentCompensationAmount, Instant refundedAt) {
        Payment broken = mock(Payment.class);
        when(broken.getJobId()).thenReturn(41L);
        when(broken.getJobApplicationId()).thenReturn(1041L);
        when(broken.getProposalId()).thenReturn(null);
        when(broken.getAmount()).thenReturn(100_000L);
        when(broken.getStudentCompensationAmount()).thenReturn(studentCompensationAmount);
        when(broken.getStatus()).thenReturn(PaymentStatus.REFUNDED);
        when(broken.getApprovedAt()).thenReturn(Instant.parse("2026-10-01T03:00:00Z"));
        when(broken.getRefundedAt()).thenReturn(refundedAt);
        return broken;
    }

    private void add(Payment payment, JobStatus jobStatus, String completedAt) {
        payments.add(SettlementHistoryData.from(payment));
        jobsById.put(payment.getJobId(),
                job(payment.getJobId(), jobStatus, completedAt, OWNER_PROFILE_ID, STUDENT_PROFILE_ID));
        applicationsById.put(payment.getJobApplicationId(),
                application(payment.getJobApplicationId(), payment.getJobId()));
    }

    private Job job(Long id, JobStatus status, String completedAt, Long ownerProfileId,
            Long selectedStudentProfileId) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .status(status)
                .ownerProfileId(ownerProfileId)
                .selectedStudentProfileId(selectedStudentProfileId)
                .completedAt(completedAt == null ? null : LocalDateTime.parse(completedAt))
                .build();
    }

    private JobApplication application(Long id, Long jobId) {
        return JobApplication.builder().id(id).jobId(jobId).studentProfileId(STUDENT_PROFILE_ID).build();
    }

    private User user(UserRole role) {
        return User.builder().id(STUDENT_USER_ID).name("김학생").role(role).isLock(false).build();
    }

    // 지원서 없이 제안 결제로 만들어진 의뢰. 본인이 담당 학생이다
    private void proposalPaid(Long jobId, Long proposalId, String approvedAt, JobStatus jobStatus,
            String completedAt, Long amount) {
        Payment payment = Payment.pendingForProposal(proposalId, OWNER_USER_ID, "order-" + jobId, amount, 2, null,
                Instant.EPOCH);
        payment.recordKakaoTid("T" + jobId);
        payment.approve(Instant.parse(approvedAt));
        payment.linkJob(jobId);
        payments.add(SettlementHistoryData.from(payment));
        jobsById.put(jobId, Job.builder().id(jobId).title("의뢰 " + jobId).status(jobStatus)
                .ownerProfileId(OWNER_PROFILE_ID).selectedStudentProfileId(STUDENT_PROFILE_ID)
                .proposalId(proposalId)
                .completedAt(completedAt == null ? null : LocalDateTime.parse(completedAt)).build());
    }

    @Test
    @DisplayName("작업 시작을 기다리는 제안 결제는 지원서 없이 정산 예정(SCHEDULED)으로 반환하고 정산 예정 금액에 합산한다")
    void returnsAwaitingStartProposalPaymentAsScheduled() {
        proposalPaid(51L, 5L, "2026-10-03T03:00:00Z", JobStatus.AWAITING_START, null, 50_000L);
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);

        SettlementHistoryResult result = facade.getSettlementHistory(USERNAME);

        List<SettlementHistoryItemResult> items = result.getMonths().get(0).getSettlements();
        assertThat(items).extracting(SettlementHistoryItemResult::getJobId).containsExactly(51L, 42L);
        assertThat(items).extracting(SettlementHistoryItemResult::getStatus)
                .containsOnly(SettlementHistoryStatus.SCHEDULED);
        assertThat(items.get(0).getAmount()).isEqualTo(50_000L);
        assertThat(items.get(0).getSettledDate()).isNull();
        assertThat(items.get(0).getStoreName()).isEqualTo("가꿈 카페");
        assertThat(result.getSummary().getScheduledAmount()).isEqualTo(150_000L);
        // 지원서는 일반 결제 것만 조회한다
        verify(jobService).getJobApplicationsByIds(List.of(1042L));
    }

    @Test
    @DisplayName("완료된 제안 의뢰의 결제는 의뢰 완료일로 정산 완료(SETTLED)를 반환한다")
    void returnsClosedProposalPaymentAsSettled() {
        proposalPaid(51L, 5L, "2026-10-02T03:00:00Z", JobStatus.CLOSED, "2026-10-10T09:30:00", 50_000L);

        SettlementHistoryItemResult item =
                facade.getSettlementHistory(USERNAME).getMonths().get(0).getSettlements().get(0);

        assertThat(item.getStatus()).isEqualTo(SettlementHistoryStatus.SETTLED);
        assertThat(item.getSettledDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("제안 결제가 가리키는 제안이 의뢰를 만든 제안이 아니면 서버 오류로 처리한다")
    void rejectsProposalPaymentLinkedToOtherProposal() {
        proposalPaid(51L, 5L, "2026-10-03T03:00:00Z", JobStatus.AWAITING_START, null, 50_000L);
        jobsById.put(51L, Job.builder().id(51L).title("의뢰 51").status(JobStatus.AWAITING_START)
                .ownerProfileId(OWNER_PROFILE_ID).selectedStudentProfileId(STUDENT_PROFILE_ID)
                .proposalId(9L).build());

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("일반 결제의 지원서가 본인 지원서가 아니면 서버 오류로 처리한다")
    void rejectsGeneralPaymentWithOtherStudentsApplication() {
        paid(42L, "2026-10-02T03:00:00Z", JobStatus.MATCHED, null);
        applicationsById.put(1042L,
                JobApplication.builder().id(1042L).jobId(42L).studentProfileId(STUDENT_PROFILE_ID + 1).build());

        assertError(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
