package com.gakkum.backend.application.jwt.facade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gakkum.backend.application.jwt.dto.DevLoginRole;
import com.gakkum.backend.domain.jwt.dto.JWTResponseDTO;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

/**
 * 개발용 테스트 로그인. dev-login.enabled가 켜진 서버에서만 등록된다.
 * 테스트 계정은 V39__seed_dev_login_accounts.sql이 만든다.
 */
@Component
@ConditionalOnProperty(name = "dev-login.enabled", havingValue = "true")
public class DevLoginFacade {

    private static final int MIN_KEY_LENGTH = 32;
    private static final String OWNER_USERNAME = "DEV_OWNER";
    private static final String STUDENT_USERNAME = "DEV_STUDENT";

    private final UserService userService;
    private final JwtService jwtService;
    private final byte[] key;

    public DevLoginFacade(UserService userService,
                          JwtService jwtService,
                          @Value("${dev-login.key:}") String key) {
        if (key.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException("dev-login.key must be at least 32 characters");
        }
        this.userService = userService;
        this.jwtService = jwtService;
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }

    public JWTResponseDTO login(String key, DevLoginRole role) {
        if (key == null || !MessageDigest.isEqual(this.key, key.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean owner = role == DevLoginRole.OWNER;
        String username = owner ? OWNER_USERNAME : STUDENT_USERNAME;
        UserRole userRole = owner ? UserRole.OWNER : UserRole.STUDENT;

        User user = userService.getActiveUser(username);
        if (user.getRole() != userRole) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return new JWTResponseDTO(jwtService.issueAccessToken(username, userRole));
    }
}
