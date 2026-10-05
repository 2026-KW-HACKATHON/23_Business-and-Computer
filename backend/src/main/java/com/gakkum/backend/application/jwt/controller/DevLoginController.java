package com.gakkum.backend.application.jwt.controller;

import com.gakkum.backend.application.jwt.dto.DevLoginRequest;
import com.gakkum.backend.application.jwt.facade.DevLoginFacade;
import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-login.enabled", havingValue = "true")
public class DevLoginController {

    private final DevLoginFacade devLoginFacade;

    // 개발용 테스트 계정의 AccessToken 발급. /jwt/exchange 와 같은 모양으로 응답
    @PostMapping(value = "/dev/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JWTResponseDTO devLoginApi(
            @RequestHeader(value = "X-Dev-Login-Key", required = false) String key,
            @Valid @RequestBody DevLoginRequest request
    ) {
        return devLoginFacade.login(key, request.getRole());
    }

}
