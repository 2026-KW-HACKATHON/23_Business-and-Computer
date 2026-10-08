package com.gakkum.backend.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KoreaTimeTest {

    private static final ZoneOffset KOREA_OFFSET = ZoneOffset.ofHours(9);

    @ParameterizedTest
    @CsvSource({
            // UTC 오후 3시 이후는 한국에서 다음 날이 된다
            "2026-10-08T03:00:00, 2026-10-08T12:00:00+09:00",
            "2026-10-08T15:30:00, 2026-10-09T00:30:00+09:00",
            "2026-12-31T15:00:00, 2027-01-01T00:00:00+09:00" })
    @DisplayName("UTC로 저장된 시각을 +09:00 오프셋의 한국 시각으로 바꾼다")
    void convertsUtcLocalDateTimeToKoreaOffset(LocalDateTime utc, String expected) {
        OffsetDateTime converted = KoreaTime.from(utc);

        assertThat(converted.getOffset()).isEqualTo(KOREA_OFFSET);
        assertThat(converted.toLocalDateTime()).isEqualTo(OffsetDateTime.parse(expected).toLocalDateTime());
    }

    @Test
    @DisplayName("변환 전후의 순간은 같고 소수 초 정밀도를 유지한다")
    void keepsInstantAndFractionalSeconds() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 7, 10, 30, 0, 123_456_789);

        OffsetDateTime converted = KoreaTime.from(utc);

        assertThat(converted.toInstant()).isEqualTo(utc.toInstant(ZoneOffset.UTC));
        assertThat(converted.getNano()).isEqualTo(123_456_789);
        assertThat(converted).hasToString("2026-10-07T19:30:00.123456789+09:00");
    }

    @Test
    @DisplayName("Instant는 같은 순간의 +09:00 한국 시각으로 바꾸고 소수 초 정밀도를 유지한다")
    void convertsInstantToKoreaOffset() {
        Instant instant = Instant.parse("2026-10-02T15:30:00.123456Z");

        OffsetDateTime converted = KoreaTime.from(instant);

        assertThat(converted.getOffset()).isEqualTo(KOREA_OFFSET);
        assertThat(converted.toInstant()).isEqualTo(instant);
        assertThat(converted).hasToString("2026-10-03T00:30:00.123456+09:00");
    }

    @Test
    @DisplayName("null 시각은 변환하지 않고 null로 둔다")
    void keepsNull() {
        assertThat(KoreaTime.from((LocalDateTime) null)).isNull();
        assertThat(KoreaTime.from((Instant) null)).isNull();
    }

    @Test
    @DisplayName("이미 변환한 한국 시각의 지역 시각을 다시 변환하면 9시간이 더 밀리므로 한 번만 변환해야 한다")
    void convertingTwiceShiftsAgain() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 5, 15, 30);

        OffsetDateTime once = KoreaTime.from(utc);
        OffsetDateTime twice = KoreaTime.from(once.toLocalDateTime());

        assertThat(once).hasToString("2026-10-06T00:30+09:00");
        assertThat(twice.toInstant()).isEqualTo(once.toInstant().plusSeconds(9 * 3600));
    }

    @ParameterizedTest
    @ValueSource(strings = { "UTC", "Asia/Seoul", "America/New_York" })
    @DisplayName("JVM 기본 시간대가 달라도 변환 결과는 같다")
    void ignoresDefaultTimeZone(String defaultZone) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
        try {
            assertThat(KoreaTime.from(LocalDateTime.of(2026, 10, 8, 3, 0))).hasToString("2026-10-08T12:00+09:00");
            assertThat(KoreaTime.from(Instant.parse("2026-10-08T03:00:00Z"))).hasToString("2026-10-08T12:00+09:00");
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
