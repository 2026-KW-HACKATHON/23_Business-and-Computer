package com.gakkum.backend.application.payment.dto;

import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalPaymentPrepareRequest {

    @NotNull
    @PositiveOrZero
    private Integer revisionCount;

    @Size(max = 5000)
    private String messageToStudent;

    @NotNull
    @AssertTrue
    private Boolean refundPolicyAgreed;

    public static ProposalPaymentPrepareRequest of(
            Integer revisionCount, String messageToStudent, Boolean refundPolicyAgreed) {
        return ProposalPaymentPrepareRequest.builder()
                .revisionCount(revisionCount)
                .messageToStudent(messageToStudent)
                .refundPolicyAgreed(refundPolicyAgreed)
                .build();
    }

    /** 빈 한마디는 입력하지 않은 것으로 본다. */
    public PrepareProposalPaymentCommand toCommand(String username, Long proposalId) {
        String message = messageToStudent == null || messageToStudent.isBlank() ? null : messageToStudent.trim();
        return PrepareProposalPaymentCommand.of(username, proposalId, revisionCount, message);
    }
}
