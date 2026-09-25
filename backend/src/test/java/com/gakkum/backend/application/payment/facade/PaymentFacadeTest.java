package com.gakkum.backend.application.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class PaymentFacadeTest {

    private static final String USERNAME = "KAKAO_123";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final JobService jobService = mock(JobService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentFacade facade = new PaymentFacade(userService, ownerService, jobService, paymentService);

    @Test
    @DisplayName("결제 준비는 인증된 사장님의 의뢰와 지원서를 확인한 후 주문 정보를 반환한다")
    void preparesPayment() {
        User user = User.builder().id(USER_ID).role(UserRole.OWNER).build();
        Owner owner = Owner.builder().id(7L).build();
        Job job = Job.builder().id(11L).title("포스터 제작").budget(100_000L).build();
        JobApplication application = JobApplication.builder().id(21L).jobId(11L).build();
        Payment payment = Payment.pending(11L, 21L, USER_ID, "order-123", 100_000L, java.time.Instant.EPOCH);
        when(userService.getActiveUser(USERNAME)).thenReturn(user);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(owner);
        when(jobService.getPayableJobForUpdate(11L, 7L)).thenReturn(job);
        when(jobService.getPayableApplication(11L, 21L)).thenReturn(application);
        when(paymentService.preparePayment(job, application, USER_ID)).thenReturn(payment);

        PreparePaymentResult result = facade.preparePayment(USERNAME, 11L, PaymentPrepareRequest.of(21L, true));

        assertThat(result.getOrderId()).isEqualTo("order-123");
        assertThat(result.getAmount()).isEqualTo(100_000L);
        assertThat(result.getOrderName()).isEqualTo("포스터 제작");
    }

    @Test
    @DisplayName("사장님 계정이 아니면 결제 준비를 거절한다")
    void rejectsNonOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).role(UserRole.STUDENT).build());

        assertThatThrownBy(() -> facade.preparePayment(USERNAME, 11L, PaymentPrepareRequest.of(21L, true)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_OWNER_REQUIRED));
        verifyNoInteractions(ownerService, jobService, paymentService);
    }
}
