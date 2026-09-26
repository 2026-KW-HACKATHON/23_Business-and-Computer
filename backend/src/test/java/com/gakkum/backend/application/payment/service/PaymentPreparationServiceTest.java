package com.gakkum.backend.application.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentPreparationServiceTest {

    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final JobService jobService = mock(JobService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentPreparationService service =
            new PaymentPreparationService(userService, ownerService, jobService, paymentService);

    @Test
    @DisplayName("사장님의 의뢰와 지원서를 확인하고 서버 금액으로 결제 시도를 만든다")
    void createsPendingPayment() {
        User user = User.builder().id(USER_ID).role(UserRole.OWNER).build();
        Owner owner = Owner.builder().id(7L).build();
        Job job = Job.builder().id(11L).title("포스터 제작").budget(100_000L).build();
        JobApplication application = JobApplication.builder().id(21L).jobId(11L).build();
        Payment payment = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, Instant.EPOCH);
        when(userService.getActiveUser("KAKAO_123")).thenReturn(user);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(owner);
        when(jobService.getPayableJobForUpdate(11L, 7L)).thenReturn(job);
        when(jobService.getPayableApplication(11L, 21L)).thenReturn(application);
        when(paymentService.preparePayment(job, application, USER_ID)).thenReturn(payment);

        PendingPaymentData result = service.createPending("KAKAO_123", 11L, PaymentPrepareRequest.of(21L, true));

        assertThat(result.orderId()).isEqualTo("order-123");
        assertThat(result.amount()).isEqualTo(100_000L);
        assertThat(result.orderName()).isEqualTo("포스터 제작");
        assertThat(result.ownerUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("사장님 계정이 아니면 결제 시도를 만들지 않는다")
    void rejectsNonOwner() {
        when(userService.getActiveUser("KAKAO_123"))
                .thenReturn(User.builder().id(USER_ID).role(UserRole.STUDENT).build());

        assertThatThrownBy(() -> service.createPending("KAKAO_123", 11L, PaymentPrepareRequest.of(21L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_OWNER_REQUIRED));
        verifyNoInteractions(ownerService, jobService, paymentService);
    }
}
