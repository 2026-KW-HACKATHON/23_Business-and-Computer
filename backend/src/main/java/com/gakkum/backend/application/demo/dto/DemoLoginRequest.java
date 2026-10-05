package com.gakkum.backend.application.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoLoginRequest {

    @NotNull
    private DemoRole role;

    // 이전 응답에서 받은 값. 없으면 새 데모 계정 쌍을 만들고, 있으면 그 쌍의 요청한 역할로 전환한다
    @Pattern(regexp = "^[0-9A-Z]{26}$")
    private String demoSessionId;

    public static DemoLoginRequest of(DemoRole role, String demoSessionId) {
        return DemoLoginRequest.builder()
                .role(role)
                .demoSessionId(demoSessionId)
                .build();
    }
}
