package com.gakkum.backend.domain.auth.dto;

import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class AuthCommandDto {

    private AuthCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class VerifyOwnerBusinessCommand {

        private final String username;
        private final String representativeName;
        private final LocalDate openedAt;
        private final String businessNumber;

        public static VerifyOwnerBusinessCommand of(
                String username, String representativeName, LocalDate openedAt, String businessNumber) {
            return VerifyOwnerBusinessCommand.builder()
                    .username(username)
                    .representativeName(representativeName)
                    .openedAt(openedAt)
                    .businessNumber(businessNumber)
                    .build();
        }
    }
}
