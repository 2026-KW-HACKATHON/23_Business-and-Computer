package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("프론트 주소 허용 목록")
class FrontendOriginsTest {

    private final FrontendOrigins origins = new FrontendOrigins(
            List.of("http://localhost:5173", "http://192.168.*.*:5173", "https://gakkum.hubspacekw.com"),
            "https://gakkum.hubspacekw.com/");

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173", "http://192.168.0.24:5173", "https://gakkum.hubspacekw.com"})
    @DisplayName("배포 사이트 · 로컬 개발 서버 · 같은 와이파이 폰 주소는 허용하고 그대로 돌려보낸다")
    void allowsListedOrigins(String origin) {
        assertThat(origins.isAllowed(origin)).isTrue();
        assertThat(origins.resolve(origin)).isEqualTo(origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example.com", "http://localhost:3000", "https://gakkum.hubspacekw.com.evil.com"})
    @DisplayName("목록 밖의 주소는 허용하지 않고 기본 주소(끝 / 없음)로 바꾼다")
    void replacesUnknownOriginWithDefault(String origin) {
        assertThat(origins.isAllowed(origin)).isFalse();
        assertThat(origins.resolve(origin)).isEqualTo("https://gakkum.hubspacekw.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://192.168.evil.com:5173",
            "http://192.168.1.evil.com:5173",
            "http://192.168.1.1.attacker.io:5173",
            "http://192.168.0.24.evil.com:5173",
            "http://192.168.256.1:5173",
            "http://192.168.0.024:5173",
            "http://192.168..1:5173"})
    @DisplayName("* 는 IP 숫자 한 칸(0~255)에만 맞아 192.168. 로 시작하는 남의 도메인은 허용하지 않는다")
    void rejectsDomainsDisguisedAsLanAddresses(String origin) {
        assertThat(origins.isAllowed(origin)).isFalse();
        assertThat(origins.resolve(origin)).isEqualTo("https://gakkum.hubspacekw.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://192.168.0.0:5173", "http://192.168.255.255:5173", "http://192.168.10.200:5173"})
    @DisplayName("같은 와이파이의 사설 IP 주소는 숫자 범위 안이면 허용한다")
    void allowsPrivateIpAddresses(String origin) {
        assertThat(origins.isAllowed(origin)).isTrue();
    }

    @Test
    @DisplayName("목록의 주소는 대소문자와 끝 / 를 무시하고 정확히 같아야 한다")
    void matchesListedOriginExactly() {
        assertThat(origins.isAllowed("HTTPS://GAKKUM.HUBSPACEKW.COM/")).isTrue();
        assertThat(origins.isAllowed("https://gakkum.hubspacekw.com:443")).isFalse();
        assertThat(origins.isAllowed("https://xgakkum.hubspacekw.com")).isFalse();
    }

    @Test
    @DisplayName("주소가 없으면 기본 주소로 보낸다")
    void resolvesNullToDefault() {
        assertThat(origins.resolve(null)).isEqualTo("https://gakkum.hubspacekw.com");
    }

    @Test
    @DisplayName("Referer 같은 주소에서 scheme · host · port 만 뽑고, 읽을 수 없으면 null")
    void extractsOrigin() {
        assertThat(FrontendOrigins.originOf("https://gakkum.hubspacekw.com/login?x=1"))
                .isEqualTo("https://gakkum.hubspacekw.com");
        assertThat(FrontendOrigins.originOf("http://localhost:5173/")).isEqualTo("http://localhost:5173");
        assertThat(FrontendOrigins.originOf("not a url")).isNull();
        assertThat(FrontendOrigins.originOf("/relative/path")).isNull();
        assertThat(FrontendOrigins.originOf(" ")).isNull();
        assertThat(FrontendOrigins.originOf(null)).isNull();
    }
}
