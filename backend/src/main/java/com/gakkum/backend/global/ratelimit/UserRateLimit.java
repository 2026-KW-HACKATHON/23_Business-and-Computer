package com.gakkum.backend.global.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 붙인 퍼사드 메서드를 로그인한 사용자마다 작업별 횟수 안에서만 실행한다. 넘으면 메서드를 실행하지 않고 429로 거부한다.
 * 횟수는 {@link UserRateLimiter}가 세고, 확인은 트랜잭션이 열리기 전에 {@link UserRateLimitAspect}가 한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRateLimit {

    RateLimitedAction value();
}
