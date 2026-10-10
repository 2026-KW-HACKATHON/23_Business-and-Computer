package com.gakkum.backend.global.ratelimit;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * {@link UserRateLimit}이 붙은 메서드를 실행하기 전에 로그인한 사용자(JWT의 username)로 요청 수를 센다.
 * 트랜잭션보다 먼저 실행되도록 순서를 앞에 두어, 한도를 넘은 요청은 DB 커넥션을 잡지 않고 거부한다.
 * 이 메서드들은 모두 로그인이 필요한 API라 인증 정보가 없으면 보안 설정이 먼저 막으므로, 그 경우는 세지 않고 넘긴다.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class UserRateLimitAspect {

    private final UserRateLimiter userRateLimiter;

    @Before(value = "@annotation(userRateLimit)", argNames = "userRateLimit")
    public void acquire(UserRateLimit userRateLimit) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return;
        }
        userRateLimiter.acquire(userRateLimit.value(), authentication.getName());
    }
}
