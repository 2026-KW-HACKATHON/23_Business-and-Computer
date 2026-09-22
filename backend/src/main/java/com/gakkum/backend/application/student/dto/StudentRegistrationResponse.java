package com.gakkum.backend.application.student.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record StudentRegistrationResponse(String accessToken, String refreshToken) {

    @Override
    @JsonIgnore
    public String refreshToken() {
        return refreshToken;
    }
}
