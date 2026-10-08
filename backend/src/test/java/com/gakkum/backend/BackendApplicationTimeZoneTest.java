package com.gakkum.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BackendApplicationTimeZoneTest {

    @Test
    @DisplayName("애플리케이션 클래스를 불러오면 JVM 기본 시간대가 UTC로 고정되어 기본 시간대로 만든 시각도 UTC가 된다")
    void pinsDefaultTimeZoneToUtc() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        try {
            // 정적 초기화는 클래스당 한 번뿐이라, 이미 불러온 뒤에도 같은 고정 동작을 다시 확인할 수 있게 새 클래스 로더로 불러온다
            ClassLoader loader = new java.net.URLClassLoader(
                    new java.net.URL[] { BackendApplication.class.getProtectionDomain().getCodeSource().getLocation() },
                    null);
            Class.forName(BackendApplication.class.getName(), true, loader);

            assertThat(TimeZone.getDefault().getRawOffset()).isZero();
            assertThat(Duration.between(LocalDateTime.now(), LocalDateTime.now(ZoneOffset.UTC)).abs())
                    .isLessThan(Duration.ofSeconds(5));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
