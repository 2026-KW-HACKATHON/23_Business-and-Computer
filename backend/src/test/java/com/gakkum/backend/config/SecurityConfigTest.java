package com.gakkum.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.application.category.controller.BusinessCategoryController;
import com.gakkum.backend.application.chat.controller.ChatController;
import com.gakkum.backend.application.chat.facade.ChatFacade;
import com.gakkum.backend.application.specialty.controller.SpecialtyController;
import com.gakkum.backend.application.explore.controller.ExploreController;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.media.controller.MediaController;
import com.gakkum.backend.application.media.facade.MediaFacade;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.owner.controller.OwnerController;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.application.payment.controller.PaymentController;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.proposal.controller.ProposalController;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.application.review.controller.ReviewController;
import com.gakkum.backend.application.review.facade.ReviewFacade;
import com.gakkum.backend.application.student.controller.StudentController;
import com.gakkum.backend.application.student.facade.StudentFacade;
import com.gakkum.backend.domain.category.dto.BusinessCategoryResponse;
import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.UpdateOwnerMeCommand;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.OwnerMeResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.UpdateStudentMeCommand;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentMeResult;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.global.response.ApiResponse;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.util.JWTUtil;

@DisplayName("보안 설정 - 기본 거부(default-deny) 인증 정책 검증")
@WebMvcTest(controllers = {SecurityConfigTest.TestController.class, SpecialtyController.class,
        JobController.class, PaymentController.class, MediaController.class, ReviewController.class,
        ProposalController.class, ExploreController.class, BusinessCategoryController.class,
        StudentController.class, OwnerController.class, ChatController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = "demo-login.enabled=false")
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
    private BusinessCategoryService businessCategoryService;

    @MockitoBean
    private JobFacade jobFacade;

    @MockitoBean
    private PaymentFacade paymentFacade;

    @MockitoBean
    private MediaFacade mediaFacade;

    @MockitoBean
    private ReviewFacade reviewFacade;

    @MockitoBean
    private ProposalFacade proposalFacade;

    @MockitoBean
    private ExploreFacade exploreFacade;

    @MockitoBean
    private StudentFacade studentFacade;

    @MockitoBean
    private OwnerFacade ownerFacade;

    @MockitoBean
    private ChatFacade chatFacade;

    @Test
    @DisplayName("인증 없이 탐색 목록을 조회하면 401을 반환한다")
    void exploreRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/explore").param("type", "PROPOSAL").param("sort", "LIKES"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 매장 목록을 조회하면 401을 반환한다")
    void storeExploreRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/explore/stores").param("sort", "LATEST").param("businessCategoryId", "3"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 제안을 보내면 401을 반환한다")
    void proposalCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 제안 상세를 조회하면 401을 반환한다")
    void receivedProposalDetailRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/proposals/31"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 제안에 공감하거나 공감을 취소하면 401을 반환한다")
    void proposalLikeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/proposals/31/likes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        mockMvc.perform(delete("/proposals/31/likes"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 제안을 취소하면 401을 반환한다")
    void rejectsUnauthenticatedProposalCancel() throws Exception {
        mockMvc.perform(post("/proposals/31/cancel"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 의뢰서를 거절하면 401을 반환한다")
    void rejectsUnauthenticatedJobDecline() throws Exception {
        mockMvc.perform(post("/jobs/42/decline"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 받은 제안 목록을 조회하면 401을 반환한다")
    void receivedProposalsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/me/received-proposals"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 사진 업로드 URL을 요청하면 401을 반환한다")
    void imageUploadPreparationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/media/images/uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"PROFILE\",\"fileName\":\"me.png\",\"contentType\":\"image/png\",\"size\":1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 리뷰를 작성하면 401을 반환한다")
    void reviewCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/jobs/42/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"좋았어요\",\"rating\":5}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 받은 리뷰를 조회하면 401을 반환한다")
    void studentReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42/review"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 완료 결과물을 조회하면 401을 반환한다")
    void jobResultRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42/result"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 수정을 요청하거나 최신 제출물을 조회하면 401을 반환한다")
    void revisionRequestAndLatestSubmissionRequireAuthentication() throws Exception {
        mockMvc.perform(post("/jobs/42/submissions/81/revision-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"로고를 조금 더 크게 해주세요.\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        mockMvc.perform(get("/jobs/42/submissions/latest"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

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
    @DisplayName("인증 없이 학생 내 정보를 조회하면 401을 반환한다")
    void studentMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/students/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 학생 프로필을 조회하면 401을 반환하고 컨트롤러에 도달하지 않는다")
    void studentProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/students/7/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(studentFacade);
    }

    @Test
    @DisplayName("학생의 Bearer 토큰으로 학생 내 정보를 조회하면 인증된 사용자 이름으로 컨트롤러까지 도달한다")
    void authenticatedStudentCanReadStudentMe() throws Exception {
        String token = "student-access-token";
        when(jwtUtil.isValid(token, true)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("KAKAO_123");
        when(jwtUtil.getRole(token)).thenReturn("STUDENT");
        when(studentFacade.getMe("KAKAO_123")).thenReturn(StudentMeResult.of(
                Student.builder().id(7L).university("광운대학교").penaltyCount(0).build(),
                User.builder().name("김광운").build(),
                "24", 0L, 0L, new BigDecimal("0.0"), List.of(), List.of(), 0L, List.of(), List.of()));

        mockMvc.perform(get("/students/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.studentProfileId").value(7))
            .andExpect(jsonPath("$.data.name").value("김광운"))
            .andExpect(jsonPath("$.data.studentNumber").value("24"));
    }

    @Test
    @DisplayName("인증 없이 사장님 내 정보를 조회하면 401을 반환하고 컨트롤러에 도달하지 않는다")
    void ownerMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/owners/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerFacade);
    }

    @Test
    @DisplayName("사장님의 Bearer 토큰으로 사장님 내 정보를 조회하면 인증된 사용자 이름으로 컨트롤러까지 도달한다")
    void authenticatedOwnerCanReadOwnerMe() throws Exception {
        String token = "owner-access-token";
        when(jwtUtil.isValid(token, true)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("KAKAO_123");
        when(jwtUtil.getRole(token)).thenReturn("OWNER");
        when(ownerFacade.getMe("KAKAO_123")).thenReturn(OwnerMeResult.of(
                Owner.builder().id(5L).storeName("가꿈 베이커리").build(),
                User.builder().name("김사장").build(),
                3L, 2L, 1L, 1L));

        mockMvc.perform(get("/owners/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ownerProfileId").value(5))
            .andExpect(jsonPath("$.data.name").value("김사장"))
            .andExpect(jsonPath("$.data.sentJobCount").value(3));
    }

    @Test
    @DisplayName("인증 없이 채팅방 목록, 채팅방 단건, 대화 내역을 조회하면 401을 반환하고 컨트롤러에 도달하지 않는다")
    void chatReadsRequireAuthentication() throws Exception {
        for (String path : List.of("/me/chat-rooms", "/chat-rooms/room-1", "/chat-rooms/room-1/messages")) {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        }

        verifyNoInteractions(chatFacade);
    }

    @Test
    @DisplayName("인증 없이 학생 내 정보를 수정하면 401을 반환하고 컨트롤러에 도달하지 않는다")
    void studentMeUpdateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/students/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialtyIds\": [], \"certificates\": []}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(studentFacade);
    }

    @Test
    @DisplayName("학생의 Bearer 토큰으로 학생 내 정보를 수정하면 인증된 사용자 이름으로 컨트롤러까지 도달하고 성공 여부만 반환한다")
    void authenticatedStudentCanUpdateStudentMe() throws Exception {
        String token = "student-access-token";
        when(jwtUtil.isValid(token, true)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("KAKAO_123");
        when(jwtUtil.getRole(token)).thenReturn("STUDENT");

        mockMvc.perform(put("/students/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialtyIds\": [1], \"certificates\": []}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<UpdateStudentMeCommand> command = ArgumentCaptor.forClass(UpdateStudentMeCommand.class);
        verify(studentFacade).updateMe(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getSpecialtyIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("인증 없이 사장님 내 정보를 수정하면 401을 반환하고 컨트롤러에 도달하지 않는다")
    void ownerMeUpdateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/owners/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerFacade);
    }

    @Test
    @DisplayName("사장님의 Bearer 토큰으로 사장님 내 정보를 수정하면 인증된 사용자 이름으로 컨트롤러까지 도달하고 성공 여부만 반환한다")
    void authenticatedOwnerCanUpdateOwnerMe() throws Exception {
        String token = "owner-access-token";
        when(jwtUtil.isValid(token, true)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("KAKAO_123");
        when(jwtUtil.getRole(token)).thenReturn("OWNER");

        mockMvc.perform(put("/owners/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<UpdateOwnerMeCommand> command = ArgumentCaptor.forClass(UpdateOwnerMeCommand.class);
        verify(ownerFacade).updateMe(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo("KAKAO_123");
        assertThat(command.getValue().getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(command.getValue().getCategoryId()).isEqualTo(1L);
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
    @DisplayName("인증 없이 업종 목록을 조회하면 공통 401 응답을 반환한다")
    void businessCategoriesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/business-categories"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("가입 전 PENDING 사용자의 Bearer 토큰으로 업종 목록을 조회할 수 있다")
    void pendingUserCanReadBusinessCategories() throws Exception {
        String token = "pending-access-token";
        when(jwtUtil.isValid(token, true)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("KAKAO_123");
        when(jwtUtil.getRole(token)).thenReturn("PENDING");
        when(businessCategoryService.getBusinessCategories()).thenReturn(List.of(
                BusinessCategoryResponse.from(BusinessCategory.builder().id(5L).name("음식점").build())));

        mockMvc.perform(get("/business-categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(5))
            .andExpect(jsonPath("$.data[0].name").value("음식점"));
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
    @DisplayName("인증 없이 내가 지원한 의뢰 목록을 조회하면 401을 반환한다")
    void studentAppliedJobsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/me/job-applications"))
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
    @DisplayName("인증 없이 의뢰 지원자 목록을 조회하면 401을 반환한다")
    void jobApplicationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42/applications").param("sort", "LATEST"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 의뢰에 지원하면 401을 반환한다")
    void jobApplicationCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/jobs/42/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"요약\",\"workPlan\":\"계획\",\"deliveryMethod\":\"전달\","
                        + "\"deadlineAndPenaltyAgreed\":true}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 의뢰 지원자 프로필을 조회하면 401을 반환한다")
    void jobApplicantProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42/applications/105/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 의뢰 제출물 상세를 조회하면 401을 반환한다")
    void jobSubmissionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/jobs/42/submission"))
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

    @Test
    @DisplayName("인증 없이 결제 내역을 조회하면 401을 반환한다")
    void paymentHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/payments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("인증 없이 정산 내역을 조회하면 401을 반환한다")
    void settlementHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/settlements"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("데모 로그인이 꺼져 있으면 인증 없는 /demo/login 요청은 401을 반환한다")
    void demoLoginIsNotPublicWhenDisabled() throws Exception {
        mockMvc.perform(post("/demo/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("localhost 프론트 Origin의 preflight는 credentials와 함께 허용된다")
    void allowsLocalhostOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("LAN 프론트 Origin의 preflight는 credentials와 함께 허용된다")
    void allowsLanOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/refresh")
                .header(HttpHeaders.ORIGIN, "http://192.168.0.10:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.0.10:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용된 LAN Origin도 보호된 API는 인증이 필요하다")
    void allowsLanOriginWithoutBypassingAuthentication() throws Exception {
        mockMvc.perform(get("/api/protected")
                .header(HttpHeaders.ORIGIN, "http://192.168.0.10:5173"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://192.168.0.10:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("다른 출처, IP 대역, 포트 또는 HTTPS의 preflight는 차단된다")
    void rejectsOriginsOutsideAllowedPatterns() throws Exception {
        for (String origin : List.of("https://other.example.com", "http://10.0.0.10:5173",
                "http://192.168.0.10:5174", "http://localhost:5174", "https://192.168.0.10:5173")) {
            mockMvc.perform(options("/refresh")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
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
