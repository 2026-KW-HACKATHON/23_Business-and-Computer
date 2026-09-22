package com.gakkum.backend.application.owner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record OwnerRegistrationResponse(String accessToken, String refreshToken) {

    @Override
    @JsonIgnore
    public String refreshToken() {
        return refreshToken;
    }
}
