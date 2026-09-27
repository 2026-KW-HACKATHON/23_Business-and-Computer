package com.gakkum.backend.application.auth.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.auth.dto.AuthCommandDto.VerifyOwnerBusinessCommand;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthFacade {

    private final UserService userService;
    private final AuthService authService;

    /** 가입 대기 사용자와 이메일 중복을 확인한 뒤 학생 이메일 인증번호를 발송한다. */
    @Transactional
    public void sendStudentEmailVerification(String username, String email) {
        User user = userService.validateStudentRegistration(username, email);
        authService.sendStudentEmailVerification(user.getId(), email);
    }

    /** 오입력 횟수가 롤백되지 않도록 AuthService와 같이 BusinessException에 롤백하지 않는다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyStudentEmail(String username, String email, String code) {
        User user = userService.getPendingUser(username);
        authService.verifyStudentEmail(user.getId(), email, code);
    }

    /** 외부 진위 확인 동안 DB 커넥션을 잡지 않도록 트랜잭션 없이 가입 대기 사용자만 먼저 확인한다. */
    public boolean verifyOwnerBusiness(VerifyOwnerBusinessCommand command) {
        userService.validateOwnerRegistration(command.getUsername());
        return authService.verifyOwnerBusiness(command);
    }
}
