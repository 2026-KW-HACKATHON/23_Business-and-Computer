package com.gakkum.backend.domain.user.service;

import java.util.List;
import java.util.Map;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.dto.CustomOAuth2User;
import com.gakkum.backend.domain.user.dto.UserResponseDTO;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.gakkum.backend.domain.user.entity.SocialProviderType;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        if (!SocialProviderType.KAKAO.name().equalsIgnoreCase(
            userRequest.getClientRegistration().getRegistrationId()
        )) {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다.");
        }

        Map<String, Object> attributes = super.loadUser(userRequest).getAttributes();
        Object idValue = attributes.get("id");

        if (!(idValue instanceof Number kakaoId)) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info"), "카카오 사용자 정보가 올바르지 않습니다."
            );
        }

        long userId = kakaoId.longValue();
        String username = SocialProviderType.KAKAO.name() + "_" + userId;
        UserRole role = userRepository.findByUsername(username)
            .map(User::getRole)
            .orElseGet(() -> {
                userRepository.save(User.builder()
                    .id(UlidGenerator.generate())
                    .username(username)
                    .isLock(false)
                    .socialProviderType(SocialProviderType.KAKAO)
                    .role(UserRole.PENDING)
                    .build());
                return UserRole.PENDING;
            });

        return new CustomOAuth2User(
            attributes,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())),
            username
        );
    }

    @Transactional(readOnly = true)
    public UserResponseDTO readUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User entity = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + username));

        return new UserResponseDTO(username, entity.getEmail());
    }

    @Transactional
    public User completeStudentRegistration(String username, String name, String email) {
        User user = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + username));

        if (user.getRole() != UserRole.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        user.completeStudentRegistration(name, email);

        return user;
    }
}
