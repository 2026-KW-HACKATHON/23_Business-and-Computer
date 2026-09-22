package com.gakkum.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.gakkum.backend.domain.user.entity.SocialProviderType;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository, mock(JwtService.class));
    private MockRestServiceServer kakaoServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        userService.setRestOperations(restTemplate);
        kakaoServer = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void newKakaoUserIsSavedAsPendingWithoutProfile() {
        kakaoUserInfo("""
            {"id": 12345, "kakao_account": {
              "profile": {"nickname": "새 사용자"}
            }}
            """);
        when(userRepository.findByUsername("KAKAO_12345")).thenReturn(Optional.empty());

        OAuth2User principal = userService.loadUser(userRequest("kakao"));

        assertThat(principal.getName()).isEqualTo("KAKAO_12345");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_PENDING");
        verify(userRepository).findByUsername("KAKAO_12345");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getId()).matches("[0-7][0-9A-HJKMNP-TV-Z]{25}");
        assertThat(saved.getUsername()).isEqualTo("KAKAO_12345");
        assertThat(saved.getRole()).isEqualTo(UserRole.PENDING);
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getName()).isNull();
        assertThat(saved.getIsLock()).isFalse();
        assertThat(saved.getSocialProviderType()).isEqualTo(SocialProviderType.KAKAO);
        verifyNoMoreInteractions(userRepository);
        kakaoServer.verify();
    }

    @Test
    void existingKakaoUserKeepsStoredRoleAndProfile() {
        kakaoUserInfo("""
            {"id": 12345, "kakao_account": {
              "profile": {"nickname": "카카오 닉네임"}
            }}
            """);
        User existing = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username("KAKAO_12345")
            .email("old@example.com")
            .name("이전 이름")
            .role(UserRole.OWNER)
            .socialProviderType(SocialProviderType.KAKAO)
            .isLock(false)
            .build();
        when(userRepository.findByUsername("KAKAO_12345")).thenReturn(Optional.of(existing));

        OAuth2User principal = userService.loadUser(userRequest("kakao"));

        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_OWNER");
        assertThat(existing.getEmail()).isEqualTo("old@example.com");
        assertThat(existing.getName()).isEqualTo("이전 이름");
        verify(userRepository).findByUsername("KAKAO_12345");
        verifyNoMoreInteractions(userRepository);
        kakaoServer.verify();
    }

    @Test
    void idOnlyResponseCanStartPendingLogin() {
        kakaoUserInfo("{\"id\":12345}");
        when(userRepository.findByUsername("KAKAO_12345")).thenReturn(Optional.empty());

        OAuth2User principal = userService.loadUser(userRequest("kakao"));

        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_PENDING");
        verify(userRepository).findByUsername("KAKAO_12345");
        verify(userRepository).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
        kakaoServer.verify();
    }

    @Test
    void unsupportedProviderIsRejectedBeforeFetchingUserInfo() {
        assertThatThrownBy(() -> userService.loadUser(userRequest("other")))
            .isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void pendingUserCompletesStudentRegistration() {
        User user = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username("KAKAO_12345")
            .isLock(false)
            .role(UserRole.PENDING)
            .build();
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false))
            .thenReturn(Optional.of(user));

        User registeredUser = userService.completeStudentRegistration(
            "KAKAO_12345",
            "김광운",
            "kwangwoon@kw.ac.kr"
        );

        assertThat(registeredUser).isSameAs(user);
        assertThat(user.getName()).isEqualTo("김광운");
        assertThat(user.getEmail()).isEqualTo("kwangwoon@kw.ac.kr");
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        verify(userRepository).findByUsernameAndIsLock("KAKAO_12345", false);
        verify(userRepository).existsByEmailIgnoreCase("kwangwoon@kw.ac.kr");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void missingUserCannotCompleteStudentRegistration() {
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.completeStudentRegistration(
            "KAKAO_12345",
            "김광운",
            "kwangwoon@kw.ac.kr"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userRepository).findByUsernameAndIsLock("KAKAO_12345", false);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void registeredUserCannotCompleteStudentRegistrationAgain() {
        User user = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username("KAKAO_12345")
            .email("old@example.com")
            .name("기존 사용자")
            .isLock(false)
            .role(UserRole.STUDENT)
            .build();
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false))
            .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.completeStudentRegistration(
            "KAKAO_12345",
            "김광운",
            "kwangwoon@kw.ac.kr"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REGISTERED));

        assertThat(user.getName()).isEqualTo("기존 사용자");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        verify(userRepository).findByUsernameAndIsLock("KAKAO_12345", false);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void duplicateEmailCannotCompleteStudentRegistration() {
        User user = User.builder()
            .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
            .username("KAKAO_12345")
            .isLock(false)
            .role(UserRole.PENDING)
            .build();
        when(userRepository.findByUsernameAndIsLock("KAKAO_12345", false))
            .thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCase("kwangwoon@kw.ac.kr")).thenReturn(true);

        assertThatThrownBy(() -> userService.completeStudentRegistration(
            "KAKAO_12345",
            "김광운",
            "kwangwoon@kw.ac.kr"
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));

        assertThat(user.getRole()).isEqualTo(UserRole.PENDING);
    }

    private void kakaoUserInfo(String body) {
        kakaoServer.expect(once(), requestTo("https://kapi.kakao.com/v2/user/me"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8080/login/oauth2/code/" + registrationId)
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .tokenUri("https://kauth.kakao.com/oauth/token")
            .userInfoUri("https://kapi.kakao.com/v2/user/me")
            .userNameAttributeName("id")
            .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "test-token",
            Instant.now(),
            Instant.now().plusSeconds(60)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
