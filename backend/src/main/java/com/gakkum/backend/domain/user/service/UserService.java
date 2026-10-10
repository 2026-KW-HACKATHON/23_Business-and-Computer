package com.gakkum.backend.domain.user.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.gakkum.backend.util.UlidGenerator;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
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
        User user = validateStudentRegistration(username, email);
        return completeStudentRegistration(user, name, email);
    }

    /** 가입 트랜잭션 안에서 가입 대기 사용자를 학생으로 바꾼다. 동시 가입 처리는 {@link #claimPendingUser}를 따른다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User completeStudentRegistration(User user, String name, String email) {
        claimPendingUser(user, UserRole.STUDENT);
        user.completeStudentRegistration(name, email);
        return user;
    }

    @Transactional(readOnly = true)
    public User validateStudentRegistration(String username, String email) {
        User user = findPendingUser(username);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        return user;
    }

    /** 가입 트랜잭션 안에서 가입 대기 사용자를 사장님으로 바꾼다. 동시 가입 처리는 {@link #claimPendingUser}를 따른다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public User completeOwnerRegistration(User user, String name) {
        claimPendingUser(user, UserRole.OWNER);
        user.completeOwnerRegistration(name);
        return user;
    }

    /**
     * 같은 가입 대기 사용자의 학생·사장님 가입이 동시에 들어와 앞선 확인을 둘 다 통과해도 하나만 가입되도록,
     * 아직 PENDING일 때만 역할을 바꾸는 조건부 UPDATE로 사용자 행을 먼저 차지한다. 바뀐 행이 없으면 다른 요청이
     * 먼저 가입을 끝낸 것이므로 ALREADY_REGISTERED로 거부해 가입 트랜잭션 전체를 롤백한다.
     * 행 잠금은 가입 트랜잭션이 끝날 때까지 유지되어, 나중 요청은 앞선 가입이 커밋되거나 롤백될 때까지 기다린다.
     */
    private void claimPendingUser(User user, UserRole role) {
        if (userRepository.updateRoleIfCurrent(user.getId(), UserRole.PENDING, role) != 1) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }
    }

    @Transactional(readOnly = true)
    public User validateOwnerRegistration(String username) {
        return findPendingUser(username);
    }

    /** 잠기지 않은 가입 대기(PENDING) 사용자를 조회한다. */
    @Transactional(readOnly = true)
    public User getPendingUser(String username) {
        return findPendingUser(username);
    }

    @Transactional(readOnly = true)
    public User getActiveUser(String username) {
        return userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    @Transactional(readOnly = true)
    public User getUser(String userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        if (user.getName() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Map<String, User> getUsersByIds(Collection<String> userIds) {
        if (userIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Map<String, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        for (String userId : userIds) {
            User user = usersById.get(userId);
            if (user == null || user.getName() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        return usersById;
    }

    /**
     * 데모 로그인용 사용자를 만든다. 가입 절차 없이 역할이 정해진 상태로 저장하고, 같은 방문자의 사장님·학생은 demoSessionId가 같다.
     * username은 소셜 로그인 형식(KAKAO_<id>)과 겹치지 않는다.
     */
    @Transactional
    public User createDemoUser(String demoSessionId, UserRole role, String name, String email) {
        return userRepository.save(User.builder()
                .id(UlidGenerator.generate())
                .username("DEMO_" + demoSessionId + "_" + role.name())
                .name(name)
                .email(email)
                .isLock(false)
                .role(role)
                .demoSessionId(demoSessionId)
                .build());
    }

    /**
     * 데모 세션의 예시 데이터에 등장하는 다른 사장님·학생을 만든다. 방문자 계정과 같은 demoSessionId를 쓰지만
     * 잠긴 계정(isLock)이라 로그인할 수 없고, getDemoUser는 방문자 계정 한 쌍만 찾는다.
     * @param number 같은 세션·역할 안에서 겹치지 않는 번호. username은 DEMO_<세션>_<역할>_<번호>다
     */
    @Transactional
    public User createDemoSampleUser(String demoSessionId, UserRole role, int number, String name) {
        return userRepository.save(User.builder()
                .id(UlidGenerator.generate())
                .username("DEMO_" + demoSessionId + "_" + role.name() + "_" + number)
                .name(name)
                .isLock(true)
                .role(role)
                .demoSessionId(demoSessionId)
                .build());
    }

    /** 데모 세션에 속한 해당 역할의 사용자를 조회한다. 모르는 세션이거나 계정이 지워졌으면 인증 오류다. */
    @Transactional(readOnly = true)
    public User getDemoUser(String demoSessionId, UserRole role) {
        return userRepository.findByDemoSessionIdAndRoleAndIsLock(demoSessionId, role, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 기준 시각 이후에 만들어진 데모 세션 수. 세션마다 방문자 사장님이 한 명이라 잠기지 않은 데모 사장님 수로 센다.
     * 예시 데이터의 다른 사장님(잠긴 계정)은 세지 않는다.
     */
    @Transactional(readOnly = true)
    public long countDemoSessionsCreatedAfter(LocalDateTime createdAt) {
        return userRepository.countByDemoSessionIdIsNotNullAndRoleAndIsLockAndCreatedAtAfter(
                UserRole.OWNER, false, createdAt);
    }

    private User findPendingUser(String username) {
        User user = userRepository.findByUsernameAndIsLock(username, false)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (user.getRole() != UserRole.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
        }

        return user;
    }
}
