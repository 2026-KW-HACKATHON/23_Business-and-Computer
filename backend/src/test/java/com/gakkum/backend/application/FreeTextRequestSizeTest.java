package com.gakkum.backend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.application.owner.dto.OwnerMeUpdateRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentMeUpdateRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.ObjectMapper;

/** TEXT 컬럼에 저장되는 자유 글은 다른 긴 글 입력과 같이 5000자까지만 받는다. JSON 요청 본문을 읽은 그대로 검증한다. */
@DisplayName("요청 DTO 자유 글 길이 제한")
class FreeTextRequestSizeTest {

    private static final int MAX = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    static Stream<Arguments> freeTextFields() {
        return Stream.of(
                Arguments.of(JobCreateRequest.class, "description"),
                Arguments.of(OwnerRegistrationRequest.class, "description"),
                Arguments.of(OwnerMeUpdateRequest.class, "description"),
                Arguments.of(StudentRegistrationRequest.class, "introduction"),
                Arguments.of(StudentMeUpdateRequest.class, "introduction"));
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("freeTextFields")
    @DisplayName("5000자까지는 받고 5001자부터는 그 필드의 길이 위반으로 거부한다")
    void limitsFreeTextToFiveThousandCharacters(Class<?> requestType, String field) {
        assertThat(violations(requestType, field, "가".repeat(MAX))).isZero();
        assertThat(violations(requestType, field, "가".repeat(MAX + 1))).isOne();
    }

    private long violations(Class<?> requestType, String field, String value) {
        Object request = objectMapper.readValue(
                "{\"" + field + "\":\"" + value + "\"}", requestType);
        return validator.validateProperty(request, field).stream()
                .filter(violation -> violation.getConstraintDescriptor().getAnnotation() instanceof Size)
                .count();
    }
}
