package com.gakkum.backend;

import java.time.ZoneOffset;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    // 응답은 저장 시각을 UTC로 해석한다. @CreationTimestamp처럼 JVM 기본 시간대로 만드는 시각도 UTC가 되도록 고정한다
    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
