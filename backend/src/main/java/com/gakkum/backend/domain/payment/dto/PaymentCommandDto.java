package com.gakkum.backend.domain.payment.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class PaymentCommandDto {

    private PaymentCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PreparePaymentCommand {

        private final Long jobId;
        private final Long jobApplicationId;
        private final String ownerUserId;
        private final Long amount;

        public static PreparePaymentCommand of(Long jobId, Long jobApplicationId, String ownerUserId, Long amount) {
            return PreparePaymentCommand.builder()
                    .jobId(jobId)
                    .jobApplicationId(jobApplicationId)
                    .ownerUserId(ownerUserId)
                    .amount(amount)
                    .build();
        }
    }

    /** 사장님이 받은 제안을 결제하려는 요청. 결제 금액과 학생은 서버가 제안에서 정한다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareProposalPaymentCommand {

        private final String username;
        private final Long proposalId;
        private final Integer revisionCount;
        private final String messageToStudent;

        public static PrepareProposalPaymentCommand of(
                String username, Long proposalId, Integer revisionCount, String messageToStudent) {
            return PrepareProposalPaymentCommand.builder()
                    .username(username)
                    .proposalId(proposalId)
                    .revisionCount(revisionCount)
                    .messageToStudent(messageToStudent)
                    .build();
        }
    }
}
