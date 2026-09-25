package com.gakkum.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.specialty.controller.SpecialtyController;
import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.payment.controller.PaymentController;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.global.response.ApiResponse;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("보안 설정 - 기본 거부(default-deny) 인증 정책 검증")
@WebMvcTest(controllers = {SecurityConfigTest.TestController.class, SpecialtyController.class,
        JobController.class, PaymentController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JWTUtil jwtUtil;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserService userService;

    @MockitoBean(name = "SocialSuccessHandler")
    private AuthenticationSuccessHandler socialSuccessHandler;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private JobFacade jobFacade;

    @MockitoBean
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName("인증 없이 임의의 보호된 API를 호출하면 공통 401 응답 형식으로 반환된다")
    void unauthenticatedRequestReturnsCommonUnauthorizedResponse() throws Exception {
        // 인증 정보 없이 임의의 보호 대상 엔드포인트를 호출
        mockMvc.perform(get("/api/protected"))
            // 실제 요청/응답(상태 코드, 헤더, 본문)을 콘솔 로그로 출력
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            // 공통 에러 응답 형식(success=false, data 필드 생략, error.code/message)을 따르는지 확인
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("인증 없이 학생 회원가입을 요청하면 401을 반환한다")
    void studentRegistrationRequiresAuthentication() throws Exception {
        // 인증 헤더 없이 학생 회원가입 API 호출 시 기본 거부 정책에 의해 차단되는지 검증
        mockMvc.perform(post("/auth/student")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 학생 이메일 인증번호를 요청하거나 검증하면 401을 반환한다")
    void studentEmailVerificationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/student-verification/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@kw.ac.kr\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/student-verification/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@kw.ac.kr\",\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증 없이 사장님 회원가입을 요청하면 401을 반환한다")
    void ownerRegistrationRequiresAuthentication() throws Exception {
        // 인증 헤더 없이 사장님 회원가입 API 호출 시 기본 거부 정책에 의해 차단되는지 검증
        mockMvc.perform(post("/auth/owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 사업자등록정보 진위 확인을 요청하면 401을 반환한다")
    void ownerBusinessVerificationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/owner-verification/business")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 대분류/특기 목록을 조회하면 401을 반환한다")
    void specialtiesRequiresAuthentication() throws Exception {
        // GET /specialties 도 다른 API와 동일하게 기본 거부 정책이 적용되는지 검증
        mockMvc.perform(get("/specialties"))
            .andDo(print())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 OPEN, MATCHED 또는 CLOSED 의뢰 목록을 조회하면 401을 반환한다")
    void jobsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/me/jobs").param("status", "OPEN"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        mockMvc.perform(get("/me/jobs").param("status", "MATCHED"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        mockMvc.perform(get("/me/jobs").param("status", "CLOSED"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 의뢰 상세를 조회하면 401을 반환한다")
    void jobDetailRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 결제 준비를 요청하면 401을 반환한다")
    void paymentPreparationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/jobs/42/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobApplicationId\":21,\"refundPolicyAgreed\":true}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 결제 승인을 요청하면 401을 반환한다")
    void paymentApprovalRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/payments/order-123/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pgToken\":\"pg-123\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/protected")
        ApiResponse<String> protectedEndpoint() {
            return ApiResponse.success("protected");
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/student")
        ApiResponse<String> registerStudent() {
            return ApiResponse.success("registered");
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/owner")
        ApiResponse<String> registerOwner() {
            return ApiResponse.success("registered");
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/owner-verification/business")
        ApiResponse<String> verifyOwnerBusiness() {
            return ApiResponse.success("verified");
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/student-verification/email")
        ApiResponse<String> sendStudentEmailVerification() {
            return ApiResponse.success("sent");
        }

        @org.springframework.web.bind.annotation.PostMapping("/auth/student-verification/email/verify")
        ApiResponse<String> verifyStudentEmail() {
            return ApiResponse.success("verified");
        }
    }
}
