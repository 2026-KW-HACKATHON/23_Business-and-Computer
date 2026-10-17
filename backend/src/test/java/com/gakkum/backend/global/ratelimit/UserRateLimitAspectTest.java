package com.gakkum.backend.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.auth.facade.AuthFacade;
import com.gakkum.backend.application.chat.facade.ChatFacade;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.media.controller.MediaController;
import com.gakkum.backend.application.media.facade.MediaFacade;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("사용자별 요청 횟수 제한 적용 (@UserRateLimit)")
class UserRateLimitAspectTest {

    private static final String BODY =
            "{\"purpose\":\"PROFILE\",\"fileName\":\"me.png\",\"contentType\":\"image/png\",\"size\":100}";

    private final UserService userService = mock(UserService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Map<RateLimitedAction, Integer> limits = new EnumMap<>(RateLimitedAction.class);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            limits.put(action, 2);
        }
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new MediaFacade(userService, mediaService));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new UserRateLimitAspect(new UserRateLimiter(Clock.systemUTC(), limits)));
        MediaFacade facade = proxyFactory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(userService.getActiveUser(anyString())).thenAnswer(invocation -> User.builder()
                .id("USER_" + invocation.getArgument(0))
                .role(UserRole.PENDING)
                .build());
        when(mediaService.prepareImageUpload(anyString(), any(), anyString(), anyString(), anyLong()))
                .thenReturn(PrepareImageUploadResult.of("https://upload.example.com", Map.of(),
                        LocalDateTime.of(2026, 10, 10, 0, 10), "https://images.example.com/a.png"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("사진 업로드 URL 발급이 사용자 한도를 넘으면 발급하지 않고 429 COMMON_429로 거부한다")
    void rejectsImageUploadOverLimitWith429() throws Exception {
        UsernamePasswordAuthenticationToken authentication = login("KAKAO_1");

        prepareImageUpload(authentication).andExpect(status().isCreated());
        prepareImageUpload(authentication).andExpect(status().isCreated());
        prepareImageUpload(authentication)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_429"))
                .andExpect(jsonPath("$.error.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));

        verify(userService, times(2)).getActiveUser("KAKAO_1");
        verify(mediaService, times(2)).prepareImageUpload(anyString(), any(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("한 사용자가 한도에 닿아도 다른 사용자는 같은 와이파이에서도 그대로 발급받는다")
    void limitsPerUserNotShared() throws Exception {
        UsernamePasswordAuthenticationToken first = login("KAKAO_1");
        prepareImageUpload(first).andExpect(status().isCreated());
        prepareImageUpload(first).andExpect(status().isCreated());
        prepareImageUpload(first).andExpect(status().isTooManyRequests());

        UsernamePasswordAuthenticationToken second = login("KAKAO_2");
        prepareImageUpload(second).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("국세청·메일·업로드 URL 발급 퍼사드 메서드에만 해당 작업의 횟수 제한이 붙어 있다")
    void annotatesCostlyFacadeMethods() {
        assertThat(actionOf(AuthFacade.class, "sendStudentEmailVerification"))
                .contains(RateLimitedAction.STUDENT_EMAIL_SEND);
        assertThat(actionOf(AuthFacade.class, "verifyOwnerBusiness"))
                .contains(RateLimitedAction.OWNER_BUSINESS_VERIFICATION);
        assertThat(actionOf(OwnerFacade.class, "register"))
                .contains(RateLimitedAction.OWNER_BUSINESS_VERIFICATION);
        assertThat(actionOf(MediaFacade.class, "prepareImageUpload")).contains(RateLimitedAction.IMAGE_UPLOAD);
        assertThat(actionOf(ChatFacade.class, "prepareAttachmentUpload"))
                .contains(RateLimitedAction.CHAT_ATTACHMENT_UPLOAD);
        assertThat(actionOf(JobFacade.class, "prepareSubmissionFileUpload"))
                .contains(RateLimitedAction.JOB_SUBMISSION_FILE_UPLOAD);
        assertThat(actionOf(AuthFacade.class, "verifyStudentEmail")).isEmpty();
    }

    @Test
    @DisplayName("의뢰 등록 · 제안 · 지원 · 채팅 보내기 · 결제 준비 퍼사드 메서드에 해당 작업의 횟수 제한이 붙어 있다")
    void annotatesWriteFacadeMethods() {
        assertThat(actionOf(JobFacade.class, "createJob")).contains(RateLimitedAction.JOB_CREATE);
        assertThat(actionOf(ProposalFacade.class, "createProposal")).contains(RateLimitedAction.PROPOSAL_CREATE);
        assertThat(actionOf(JobFacade.class, "createJobApplication"))
                .contains(RateLimitedAction.JOB_APPLICATION_CREATE);
        assertThat(actionOf(ChatFacade.class, "sendTextMessage")).contains(RateLimitedAction.CHAT_MESSAGE_SEND);
        assertThat(actionOf(ChatFacade.class, "sendAttachmentMessage"))
                .contains(RateLimitedAction.CHAT_MESSAGE_SEND);
        assertThat(actionOf(PaymentFacade.class, "preparePayment")).contains(RateLimitedAction.PAYMENT_PREPARE);
        assertThat(actionOf(PaymentFacade.class, "prepareProposalPayment"))
                .contains(RateLimitedAction.PAYMENT_PREPARE);
        // 승인은 카카오페이에서 돌아온 뒤 같은 주문을 다시 확인하는 요청이라 막지 않는다
        assertThat(actionOf(PaymentFacade.class, "approvePayment")).isEmpty();
    }

    @Test
    @DisplayName("데모 계정은 데모 한도로 세어, 같은 한도의 일반 계정보다 먼저 429를 받는다")
    void appliesDemoLimitThroughAspect() throws Exception {
        Map<RateLimitedAction, Integer> limits = new EnumMap<>(RateLimitedAction.class);
        Map<RateLimitedAction, Integer> demoLimits = new EnumMap<>(RateLimitedAction.class);
        for (RateLimitedAction action : RateLimitedAction.values()) {
            limits.put(action, 2);
            demoLimits.put(action, 2);
        }
        demoLimits.put(RateLimitedAction.IMAGE_UPLOAD, 1);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new MediaFacade(userService, mediaService));
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAspect(new UserRateLimitAspect(new UserRateLimiter(Clock.systemUTC(), limits, demoLimits)));
        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(proxyFactory.<MediaFacade>getProxy()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UsernamePasswordAuthenticationToken demo = login("DEMO_01K6DEMO00000000000000000A_OWNER");
        prepareImageUpload(demo).andExpect(status().isCreated());
        prepareImageUpload(demo).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("COMMON_429"));

        UsernamePasswordAuthenticationToken regular = login("KAKAO_1");
        prepareImageUpload(regular).andExpect(status().isCreated());
        prepareImageUpload(regular).andExpect(status().isCreated());
        prepareImageUpload(regular).andExpect(status().isTooManyRequests());
    }

    private ResultActions prepareImageUpload(UsernamePasswordAuthenticationToken authentication) throws Exception {
        return mockMvc.perform(post("/media/images/uploads")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }

    // JWTFilter와 같이 인증된 토큰을 보안 컨텍스트에 둔다
    private static UsernamePasswordAuthenticationToken login(String username) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_PENDING")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private static Optional<RateLimitedAction> actionOf(Class<?> type, String methodName) {
        Method method = Arrays.stream(type.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return Optional.ofNullable(method.getAnnotation(UserRateLimit.class)).map(UserRateLimit::value);
    }
}
