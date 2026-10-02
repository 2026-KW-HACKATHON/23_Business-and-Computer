package com.gakkum.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class UserLookupServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserService userService = new UserService(userRepository, mock(JwtService.class));

    @Test
    @DisplayName("학생의 사용자 ID로 작업자 이름을 일괄 조회한다")
    void returnsUsersByIds() {
        when(userRepository.findAllById(List.of("user-1", "user-2"))).thenReturn(List.of(
                User.builder().id("user-2").name("김가꿈").build(),
                User.builder().id("user-1").name("김람가").build()));

        assertThat(userService.getUsersByIds(List.of("user-1", "user-2")))
                .extractingByKey("user-1")
                .extracting(User::getName)
                .isEqualTo("김람가");
    }

    @Test
    @DisplayName("연결된 작업자 사용자가 없으면 조회를 실패시킨다")
    void rejectsMissingUser() {
        when(userRepository.findAllById(List.of("user-1"))).thenReturn(List.of());

        assertThatThrownBy(() -> userService.getUsersByIds(List.of("user-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("연결된 작업자 이름이 없으면 조회를 실패시킨다")
    void rejectsMissingName() {
        when(userRepository.findAllById(List.of("user-1")))
                .thenReturn(List.of(User.builder().id("user-1").build()));

        assertThatThrownBy(() -> userService.getUsersByIds(List.of("user-1")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
