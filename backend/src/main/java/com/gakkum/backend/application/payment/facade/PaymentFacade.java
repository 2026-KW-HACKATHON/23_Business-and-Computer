package com.gakkum.backend.application.payment.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final JobService jobService;
    private final PaymentService paymentService;

    @Transactional
    public PreparePaymentResult preparePayment(String username, Long jobId, PaymentPrepareRequest request) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PAYMENT_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Job job = jobService.getPayableJobForUpdate(jobId, owner.getId());
        JobApplication application = jobService.getPayableApplication(jobId, request.getJobApplicationId());
        Payment payment = paymentService.preparePayment(job, application, user.getId());

        return PreparePaymentResult.of(payment.getOrderId(), payment.getAmount(), job.getTitle());
    }
}
