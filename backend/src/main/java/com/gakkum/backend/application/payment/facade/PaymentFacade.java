package com.gakkum.backend.application.payment.facade;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
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

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    // 결제 내역을 묶는 월의 기준 시간대
    private static final ZoneId HISTORY_ZONE = ZoneId.of("Asia/Seoul");

    private final PaymentPreparationService preparationService;
    private final KakaoPayClient kakaoPayClient;
    private final PaymentService paymentService;
    private final PaymentApprovalService approvalService;
    private final UserService userService;
    private final JobService jobService;
    private final StudentService studentService;

    public ApprovedPaymentData approvePayment(String username, String orderId, String pgToken) {
        return approvalService.approve(username, orderId, pgToken);
    }

    public PreparePaymentResult preparePayment(String username, Long jobId, PaymentPrepareRequest request) {
        PendingPaymentData pending = preparationService.createPending(username, jobId, request);
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
     * 결제가 있으면 의뢰·지원서·학생 프로필·사용자를 중복 없이 모아 한 번씩만 조회한다. 참조 누락은 각 일괄 조회가 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public PaymentHistoryResult getPaymentHistory(String username) {
        User user = userService.getActiveUser(username);

        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_LIST_OWNER_REQUIRED);
        }
        List<PaymentHistoryData> payments = paymentService.getPaymentHistory(user.getId());
        if (payments.isEmpty()) {
            return PaymentHistoryResult.of(List.of());
        }

        Map<Long, Job> jobsById = jobService.getJobsByIds(payments.stream()
                .map(PaymentHistoryData::getJobId)
                .distinct()
                .toList());
        Map<Long, JobApplication> applicationsById = jobService.getJobApplicationsByIds(payments.stream()
                .map(PaymentHistoryData::getJobApplicationId)
                .distinct()
                .toList());
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(applicationsById.values().stream()
                .map(JobApplication::getStudentProfileId)
                .distinct()
                .toList());
        Map<String, User> studentUsersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());

        // 결제는 이미 승인 시각 최신순이라 처음 만나는 순서가 곧 월 내림차순이다
        Map<YearMonth, List<PaymentHistoryItemResult>> itemsByMonth = new LinkedHashMap<>();
        for (PaymentHistoryData payment : payments) {
            Job job = jobsById.get(payment.getJobId());
            JobApplication application = applicationsById.get(payment.getJobApplicationId());
            // 예외: 결제가 가리키는 지원서가 다른 의뢰의 지원서인 경우
            if (!application.getJobId().equals(payment.getJobId())) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
            Student student = studentsById.get(application.getStudentProfileId());
            PaymentHistoryStatus status = historyStatus(payment.getStatus(), job.getStatus());
            itemsByMonth.computeIfAbsent(YearMonth.from(payment.getApprovedAt().atZone(HISTORY_ZONE)),
                    month -> new ArrayList<>())
                    .add(PaymentHistoryItemResult.of(payment, job.getTitle(), refundAmount(payment),
                            studentUsersById.get(student.getUserId()).getName(), status));
        }

        return PaymentHistoryResult.of(itemsByMonth.entrySet().stream()
                .map(entry -> PaymentHistoryMonthResult.of(entry.getKey().toString(), entry.getValue()))
                .toList());
    }

    // 결제 상태와 의뢰 상태가 맞지 않는 내역은 임의 상태로 보여주지 않고 데이터 오류(500)로 거부한다
    private PaymentHistoryStatus historyStatus(PaymentStatus paymentStatus, JobStatus jobStatus) {
        if (paymentStatus == PaymentStatus.PAID && jobStatus == JobStatus.MATCHED) {
            return PaymentHistoryStatus.HELD;
        }
        if (paymentStatus == PaymentStatus.PAID && jobStatus == JobStatus.CLOSED) {
            return PaymentHistoryStatus.SETTLED;
        }
        if (paymentStatus == PaymentStatus.REFUNDED && jobStatus == JobStatus.CANCELLED) {
            return PaymentHistoryStatus.PARTIALLY_REFUNDED;
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
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
