package com.gakkum.backend.global.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** API 응답 시각을 한국 시간과 +09:00 오프셋으로 맞춘다. 응답 DTO의 from·of 매핑에서만 쓴다. */
public final class KoreaTime {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    /** UTC로 저장된 시각을 같은 순간의 한국 시각으로 바꾼다. null은 그대로 둔다. */
    public static OffsetDateTime from(LocalDateTime utc) {
        return utc == null ? null : from(utc.toInstant(ZoneOffset.UTC));
    }

    public static OffsetDateTime from(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
    }
}
