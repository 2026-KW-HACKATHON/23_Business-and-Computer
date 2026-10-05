package com.gakkum.backend.application.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoLoginResponse {

    private final String accessToken;

    @JsonIgnore
    private final String refreshToken;

    private final String demoSessionId;

    public static DemoLoginResponse of(String accessToken, String refreshToken, String demoSessionId) {
        return DemoLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .demoSessionId(demoSessionId)
                .build();
    }
}
