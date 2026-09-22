package com.gakkum.backend.application.owner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OwnerRegistrationResponse {

    private final String accessToken;

    @JsonIgnore
    private final String refreshToken;

    public static OwnerRegistrationResponse of(String accessToken, String refreshToken) {
        return OwnerRegistrationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
