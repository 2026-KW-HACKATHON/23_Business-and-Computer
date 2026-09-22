package com.gakkum.backend.application.student.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.gakkum.backend.application.student.dto.StudentRegistrationCommand;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.application.student.facade.StudentRegistrationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

class StudentRegistrationControllerTest {

    private final StudentRegistrationFacade studentRegistrationFacade = mock(StudentRegistrationFacade.class);
    private final StudentRegistrationController controller =
            new StudentRegistrationController(studentRegistrationFacade);

    @Test
    void returnsAccessTokenAndSetsRefreshTokenCookie() throws Exception {
        StudentRegistrationRequest request = validRequest("KWANGWOON@KW.AC.KR");
        StudentRegistrationResponse registrationResponse =
                StudentRegistrationResponse.of("access-token", "refresh-token");
        when(studentRegistrationFacade.register(eq("KAKAO_12345"),
                org.mockito.ArgumentMatchers.any(StudentRegistrationCommand.class)))
                .thenReturn(registrationResponse);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<StudentRegistrationResponse> response = controller.register(
                new UsernamePasswordAuthenticationToken("KAKAO_12345", null),
                request,
                servletResponse);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(servletResponse.getHeader("Set-Cookie"))
                .contains("refreshToken=refresh-token", "HttpOnly", "SameSite=Lax", "Path=/");

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"accessToken\":\"access-token\"");
        assertThat(json).doesNotContain("refresh-token", "refreshToken");

        ArgumentCaptor<StudentRegistrationCommand> commandCaptor =
                ArgumentCaptor.forClass(StudentRegistrationCommand.class);
        verify(studentRegistrationFacade).register(eq("KAKAO_12345"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().getEmail()).isEqualTo("kwangwoon@kw.ac.kr");
        assertThat(commandCaptor.getValue().getSpecialtyIds()).isEmpty();
        assertThat(commandCaptor.getValue().getCertificates()).isEmpty();
    }

    @Test
    void rejectsInvalidUniversityEmailAndStudentNumber() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StudentRegistrationRequest request = StudentRegistrationRequest.of(
                "김광운",
                "user@example.com",
                "다른대학교",
                "1234",
                "컴퓨터정보공학부",
                null,
                null,
                null,
                List.of(),
                List.of());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "university", "studentNumber");
    }

    @Test
    void acceptsMissingPortfolioUrl() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        StudentRegistrationRequest request = validRequest("student@kw.ac.kr");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.toCommand().getPortfolioUrl()).isNull();
    }

    private StudentRegistrationRequest validRequest(String email) {
        return StudentRegistrationRequest.of(
                "김광운",
                email,
                "광운대학교",
                "2024402001",
                "컴퓨터정보공학부",
                null,
                null,
                null,
                null,
                null);
    }
}
