package com.gakkum.backend.application.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StudentRegistrationResponse {

    private final String accessToken;

    @JsonIgnore
    private final String refreshToken;

    public static StudentRegistrationResponse of(String accessToken, String refreshToken) {
        return StudentRegistrationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
