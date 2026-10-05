package com.gakkum.backend.application.jwt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DevLoginRequest {

    @NotNull
    private DevLoginRole role;

    public static DevLoginRequest of(DevLoginRole role) {
        return DevLoginRequest.builder()
                .role(role)
                .build();
    }
}
