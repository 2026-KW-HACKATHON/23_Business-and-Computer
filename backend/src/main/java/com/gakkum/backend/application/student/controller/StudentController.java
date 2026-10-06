package com.gakkum.backend.application.student.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.job.dto.JobApplicantProfileResponse;
import com.gakkum.backend.application.student.dto.StudentMeResponse;
import com.gakkum.backend.application.student.dto.StudentMeUpdateRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.application.student.facade.StudentFacade;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StudentController {

    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final StudentFacade studentFacade;

    @PostMapping("/auth/student")
    public ResponseEntity<ApiResponse<StudentRegistrationResponse>> register(
            Authentication authentication,
            @Valid @RequestBody StudentRegistrationRequest request,
            HttpServletResponse httpServletResponse) {
        StudentRegistrationResponse response = studentFacade.register(
                authentication.getName(),
                request);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .path("/")
                .sameSite("None")
                .httpOnly(true)
                .secure(true)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/students/me")
    public ResponseEntity<ApiResponse<StudentMeResponse>> getMe(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                StudentMeResponse.from(studentFacade.getMe(authentication.getName()))));
    }

    @PutMapping("/students/me")
    public ResponseEntity<ApiResponse<Void>> updateMe(
            Authentication authentication,
            @Valid @RequestBody StudentMeUpdateRequest request) {
        studentFacade.updateMe(request.toCommand(authentication.getName()));
        return ResponseEntity.ok(ApiResponse.success());
    }

    /** 사장님이 의뢰·지원·제안 관계와 무관하게 학생의 정보와 활동 이력을 조회하는 API */
    @GetMapping("/students/{studentProfileId}/profile")
    public ResponseEntity<ApiResponse<JobApplicantProfileResponse>> getStudentProfile(
            Authentication authentication, @PathVariable Long studentProfileId) {
        if (studentProfileId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        JobApplicantProfileResponse response = JobApplicantProfileResponse.from(
                studentFacade.getStudentProfile(authentication.getName(), studentProfileId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
