package com.gakkum.backend.application.payment.facade;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    // 결제·정산 내역을 묶는 월과 정산 날짜의 기준 시간대
    private static final ZoneId HISTORY_ZONE = ZoneId.of("Asia/Seoul");

    private final PaymentPreparationService preparationService;
    private final KakaoPayClient kakaoPayClient;
    private final PaymentService paymentService;
    private final PaymentApprovalService approvalService;
    private final UserService userService;
    private final JobService jobService;
    private final StudentService studentService;
    private final OwnerService ownerService;
    private final Clock clock;

    public ApprovedOrderData approvePayment(String username, String orderId, String pgToken) {
        return approvalService.approve(username, orderId, pgToken);
    }

    public PreparePaymentResult preparePayment(String username, Long jobId, PaymentPrepareRequest request) {
        PendingPaymentData pending;
        try {
            pending = preparationService.createPending(username, jobId, request);
        } catch (BusinessException exception) {
            recoverIfAlreadyPaid(exception, username, () -> paymentService.findPendingPayment(jobId));
            throw exception;
        }
        return openPaymentWindow(pending);
    }

    /** 사장님이 받은 제안의 결제를 준비한다. 결제창 준비는 일반 결제와 같은 경로를 쓴다. */
    public PreparePaymentResult prepareProposalPayment(PrepareProposalPaymentCommand command) {
        PendingPaymentData pending;
        try {
            pending = preparationService.createPendingForProposal(command);
        } catch (BusinessException exception) {
            recoverIfAlreadyPaid(exception, command.getUsername(),
                    () -> paymentService.findPendingProposalPayment(command.getProposalId()));
            throw exception;
        }
        return openPaymentWindow(pending);
    }

    /**
     * 결제 준비가 '이미 결제됨'으로 거부됐을 때, 외부 승인 후 저장하지 못해 PENDING으로 남은 주문이 있으면 조회 결과로 복구한다.
     * 준비 트랜잭션이 롤백된 뒤 별도 트랜잭션으로 기록해야 복구가 남는다. 복구에 실패해도 주문은 PENDING으로 남아
     * 승인 재요청이나 다음 준비 요청에서 다시 복구할 수 있으므로 원래 거부 응답을 그대로 돌려준다.
     */
    private void recoverIfAlreadyPaid(
            BusinessException exception, String username, Supplier<Optional<Payment>> pendingPayment) {
        if (exception.getErrorCode() != ErrorCode.PAYMENT_ALREADY_PAID) {
            return;
        }
        try {
            pendingPayment.get().ifPresent(payment -> approvalService.recoverPaidOrder(username, payment.getOrderId()));
        } catch (RuntimeException recoveryFailure) {
            log.warn("Paid order recovery failed: {}", recoveryFailure.getMessage());
        }
    }

    // 외부 결제창 준비가 DB 트랜잭션과 커넥션을 붙잡지 않도록 대기 주문 저장과 분리한다
    private PreparePaymentResult openPaymentWindow(PendingPaymentData pending) {
        ReadyResult ready;
        try {
            ready = kakaoPayClient.ready(pending);
        } catch (BusinessException exception) {
            paymentService.failReady(pending.orderId());
            throw exception;
        }
        paymentService.recordKakaoTid(pending.orderId(), ready.tid());
        return PreparePaymentResult.of(pending.orderId(), pending.amount(), pending.orderName(),
                ready.nextRedirectPcUrl(), ready.nextRedirectMobileUrl());
    }

    /**
     * 사장님 본인의 결제 내역. 승인 시각의 한국 시간 월별로 묶어 최신순으로 반환한다.
     * 결제가 있으면 의뢰·지원서·학생 프로필·사용자를 중복 없이 모아 한 번씩만 조회한다. 제안 결제의 학생은 의뢰의 담당 학생이다. 참조 누락은 각 일괄 조회가 500으로 거부한다.
     * 요약 금액은 같은 내역에서 합산한다. 이번 달 결제액은 환불액을 빼지 않은 최초 결제액이고, 보관·정산 완료 금액은 전 기간 합계다.
     */
    @Transactional(readOnly = true)
    public PaymentHistoryResult getPaymentHistory(String username) {
        User user = userService.getActiveUser(username);

        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_LIST_OWNER_REQUIRED);
        }
        List<PaymentHistoryData> payments = paymentService.getPaymentHistory(user.getId());
        if (payments.isEmpty()) {
            return PaymentHistoryResult.of(PaymentHistorySummaryResult.of(0L, 0L, 0L), List.of());
        }

        Map<Long, Job> jobsById = jobService.getJobsByIds(payments.stream()
                .map(PaymentHistoryData::getJobId)
                .distinct()
                .toList());
        // 제안 결제는 지원서가 없다
        Map<Long, JobApplication> applicationsById = jobService.getJobApplicationsByIds(payments.stream()
                .map(PaymentHistoryData::getJobApplicationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, Long> studentProfileIdsByJobId = new HashMap<>();
        for (PaymentHistoryData payment : payments) {
            studentProfileIdsByJobId.put(payment.getJobId(),
                    paidStudentProfileId(payment, jobsById.get(payment.getJobId()), applicationsById));
        }
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(
                studentProfileIdsByJobId.values().stream()
                .distinct()
                .toList());
        Map<String, User> studentUsersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());

        // 결제는 이미 승인 시각 최신순이라 처음 만나는 순서가 곧 월 내림차순이다
        Map<YearMonth, List<PaymentHistoryItemResult>> itemsByMonth = new LinkedHashMap<>();
        YearMonth thisMonth = YearMonth.now(clock.withZone(HISTORY_ZONE));
        long thisMonthPaymentAmount = 0;
        long heldAmount = 0;
        long totalSettledAmount = 0;
        for (PaymentHistoryData payment : payments) {
            Job job = jobsById.get(payment.getJobId());
            Student student = studentsById.get(studentProfileIdsByJobId.get(payment.getJobId()));
            PaymentHistoryStatus status = historyStatus(payment, job.getStatus());
            YearMonth approvedMonth = YearMonth.from(payment.getApprovedAt().atZone(HISTORY_ZONE));
            itemsByMonth.computeIfAbsent(approvedMonth, month -> new ArrayList<>())
                    .add(PaymentHistoryItemResult.of(payment, job.getTitle(), refundAmount(payment),
                            studentUsersById.get(student.getUserId()).getName(), status,
                            paymentSettledDate(job, status), paymentRefundedDate(payment, status)));

            if (approvedMonth.equals(thisMonth)) {
                thisMonthPaymentAmount += payment.getAmount();
            }
            if (status == PaymentHistoryStatus.HELD) {
                heldAmount += payment.getAmount();
            } else if (status == PaymentHistoryStatus.SETTLED) {
                totalSettledAmount += payment.getAmount();
            }
        }

        return PaymentHistoryResult.of(
                PaymentHistorySummaryResult.of(thisMonthPaymentAmount, heldAmount, totalSettledAmount),
                itemsByMonth.entrySet().stream()
                .map(entry -> PaymentHistoryMonthResult.of(entry.getKey().toString(), entry.getValue()))
                .toList());
    }

    /**
     * 학생 본인의 정산 내역. 결제 승인 시각의 한국 시간 월별로 묶어 최신순으로 반환한다.
     * 본인이 담당하는 의뢰에 연결된 결제만 조회해 제안 결제도 포함하고, 지원서와 매장 이름은 중복 없이 모아 한 번씩만 조회한다.
     * 요약 금액은 같은 내역의 학생 수령액을 합산한다. 이번 달 작업비는 이번 달 승인 건, 정산 예정·완료 금액은 전 기간 합계다.
     */
    @Transactional(readOnly = true)
    public SettlementHistoryResult getSettlementHistory(String username) {
        User user = userService.getActiveUser(username);

        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.SETTLEMENT_LIST_STUDENT_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        Map<Long, Job> jobsById = jobService.getJobsBySelectedStudentProfileId(student.getId());
        List<SettlementHistoryData> payments = paymentService.getSettlementHistory(jobsById.keySet());
        if (payments.isEmpty()) {
            return SettlementHistoryResult.of(SettlementHistorySummaryResult.of(0L, 0L, 0L), List.of());
        }

        // 제안 결제는 지원서가 없다
        Map<Long, JobApplication> applicationsById = jobService.getJobApplicationsByIds(payments.stream()
                .map(SettlementHistoryData::getJobApplicationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, String> storeNamesByOwnerProfileId = ownerService.getStoreNames(payments.stream()
                .map(payment -> jobsById.get(payment.getJobId()).getOwnerProfileId())
                .distinct()
                .toList());

        // 결제는 이미 승인 시각 최신순이라 처음 만나는 순서가 곧 월 내림차순이다
        Map<YearMonth, List<SettlementHistoryItemResult>> itemsByMonth = new LinkedHashMap<>();
        YearMonth thisMonth = YearMonth.now(clock.withZone(HISTORY_ZONE));
        long thisMonthWorkAmount = 0;
        long scheduledAmount = 0;
        long totalSettledAmount = 0;
        for (SettlementHistoryData payment : payments) {
            Job job = jobsById.get(payment.getJobId());
            // 예외: 결제된 의뢰에 선택된 학생이 본인이 아닌 경우
            if (!student.getId().equals(job.getSelectedStudentProfileId())) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            if (payment.getProposalId() != null) {
                // 예외: 제안 결제가 가리키는 제안이 의뢰를 만든 제안이 아닌 경우
                if (!payment.getProposalId().equals(job.getProposalId())) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            } else {
                JobApplication application = applicationsById.get(payment.getJobApplicationId());
                // 예외: 본인 지원서가 아니거나, 결제가 가리키는 지원서가 다른 의뢰의 지원서인 경우
                if (application == null || !application.getStudentProfileId().equals(student.getId())
                        || !application.getJobId().equals(payment.getJobId())) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
            SettlementHistoryStatus status = settlementStatus(payment, job.getStatus());
            Long amount = settlementAmount(payment, status);
            YearMonth approvedMonth = YearMonth.from(payment.getApprovedAt().atZone(HISTORY_ZONE));
            itemsByMonth.computeIfAbsent(approvedMonth, month -> new ArrayList<>())
                    .add(SettlementHistoryItemResult.of(job.getId(), job.getTitle(),
                            amount, settledDate(payment, job, status),
                            storeNamesByOwnerProfileId.get(job.getOwnerProfileId()), status));

            if (approvedMonth.equals(thisMonth)) {
                thisMonthWorkAmount += amount;
            }
            // 착수 보상은 이미 지급이 확정된 금액이라 정산 완료 합계에 포함한다. 환불은 0원이라 어느 합계도 바꾸지 않는다
            if (status == SettlementHistoryStatus.SCHEDULED) {
                scheduledAmount += amount;
            } else {
                totalSettledAmount += amount;
            }
        }

        return SettlementHistoryResult.of(
                SettlementHistorySummaryResult.of(thisMonthWorkAmount, scheduledAmount, totalSettledAmount),
                itemsByMonth.entrySet().stream()
                .map(entry -> SettlementHistoryMonthResult.of(entry.getKey().toString(), entry.getValue()))
                .toList());
    }

    // 결제 상태와 의뢰 상태가 맞지 않는 내역은 임의 상태로 보여주지 않고 데이터 오류(500)로 거부한다
    private SettlementHistoryStatus settlementStatus(SettlementHistoryData payment, JobStatus jobStatus) {
        PaymentStatus paymentStatus = payment.getStatus();
        // 제안 결제 후 학생의 작업 시작을 기다리는 의뢰도 정산 예정이다
        if (paymentStatus == PaymentStatus.PAID
                && (jobStatus == JobStatus.MATCHED || jobStatus == JobStatus.AWAITING_START)) {
            return SettlementHistoryStatus.SCHEDULED;
        }
        if (paymentStatus == PaymentStatus.PAID && jobStatus == JobStatus.CLOSED) {
            return SettlementHistoryStatus.SETTLED;
        }
        // 학생이 의뢰서를 거절한 전액 환불은 보상금이 없어 착수 보상과 구분한다
        if (paymentStatus == PaymentStatus.REFUNDED && jobStatus == JobStatus.CANCELLED) {
            return payment.isFullyRefunded()
                    ? SettlementHistoryStatus.REFUNDED
                    : SettlementHistoryStatus.START_COMPENSATION;
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 학생 수령액. 착수 보상과 환불(0원)은 환불 시점에 저장된 학생 보상금이며 다시 계산하지 않는다
    private Long settlementAmount(SettlementHistoryData payment, SettlementHistoryStatus status) {
        if (status != SettlementHistoryStatus.START_COMPENSATION && status != SettlementHistoryStatus.REFUNDED) {
            return payment.getAmount();
        }
        if (payment.getStudentCompensationAmount() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payment.getStudentCompensationAmount();
    }

    // 정산 예정은 날짜가 없다. 정산 완료는 UTC로 저장된 의뢰 완료 시각의 한국 날짜, 착수 보상과 환불은 한국 시간 기준 환불 처리일이다
    private LocalDate settledDate(SettlementHistoryData payment, Job job, SettlementHistoryStatus status) {
        if (status == SettlementHistoryStatus.SCHEDULED) {
            return null;
        }
        if (status == SettlementHistoryStatus.SETTLED) {
            if (job.getCompletedAt() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return job.getCompletedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(HISTORY_ZONE).toLocalDate();
        }
        if (payment.getRefundedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payment.getRefundedAt().atZone(HISTORY_ZONE).toLocalDate();
    }

    // 결제 상태와 의뢰 상태가 맞지 않는 내역은 임의 상태로 보여주지 않고 데이터 오류(500)로 거부한다
    private PaymentHistoryStatus historyStatus(PaymentHistoryData payment, JobStatus jobStatus) {
        PaymentStatus paymentStatus = payment.getStatus();
        // 제안 결제 후 학생의 작업 시작을 기다리는 의뢰도 보관 중이다
        if (paymentStatus == PaymentStatus.PAID
                && (jobStatus == JobStatus.MATCHED || jobStatus == JobStatus.AWAITING_START)) {
            return PaymentHistoryStatus.HELD;
        }
        if (paymentStatus == PaymentStatus.PAID && jobStatus == JobStatus.CLOSED) {
            return PaymentHistoryStatus.SETTLED;
        }
        // 학생이 의뢰서를 거절한 환불은 보상금 없이 전액을 돌려준다
        if (paymentStatus == PaymentStatus.REFUNDED && jobStatus == JobStatus.CANCELLED) {
            return payment.isFullyRefunded()
                    ? PaymentHistoryStatus.FULLY_REFUNDED
                    : PaymentHistoryStatus.PARTIALLY_REFUNDED;
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 정산 완료만 날짜가 있다. 정산 내역과 같이 UTC로 저장된 의뢰 완료 시각의 한국 날짜다
    private LocalDate paymentSettledDate(Job job, PaymentHistoryStatus status) {
        if (status != PaymentHistoryStatus.SETTLED) {
            return null;
        }
        if (job.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return job.getCompletedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(HISTORY_ZONE).toLocalDate();
    }

    // 부분·전액 환불만 날짜가 있다. 한국 시간 기준 환불 처리일이다
    private LocalDate paymentRefundedDate(PaymentHistoryData payment, PaymentHistoryStatus status) {
        if (status != PaymentHistoryStatus.PARTIALLY_REFUNDED && status != PaymentHistoryStatus.FULLY_REFUNDED) {
            return null;
        }
        if (payment.getRefundedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payment.getRefundedAt().atZone(HISTORY_ZONE).toLocalDate();
    }

    // 결제된 의뢰를 담당하는 학생. 일반 결제는 결제한 지원서의 학생, 제안 결제는 제안한 학생(의뢰의 담당 학생)이다
    private Long paidStudentProfileId(
            PaymentHistoryData payment, Job job, Map<Long, JobApplication> applicationsById) {
        if (payment.getProposalId() != null) {
            // 예외: 제안 결제가 가리키는 제안이 의뢰를 만든 제안이 아니거나 담당 학생이 없는 경우
            if (!payment.getProposalId().equals(job.getProposalId()) || job.getSelectedStudentProfileId() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            return job.getSelectedStudentProfileId();
        }
        JobApplication application = applicationsById.get(payment.getJobApplicationId());
        // 예외: 결제가 가리키는 지원서가 다른 의뢰의 지원서인 경우
        if (!application.getJobId().equals(payment.getJobId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return application.getStudentProfileId();
    }

    private Long refundAmount(PaymentHistoryData payment) {
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            return 0L;
        }
        if (payment.getRefundAmount() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return payment.getRefundAmount();
    }
}
