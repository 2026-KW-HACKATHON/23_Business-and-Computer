package com.gakkum.backend.application.auth.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerBusinessVerificationResponse {

    private final boolean verified;

    public static OwnerBusinessVerificationResponse of(boolean verified) {
        return OwnerBusinessVerificationResponse.builder()
                .verified(verified)
                .build();
    }
}
