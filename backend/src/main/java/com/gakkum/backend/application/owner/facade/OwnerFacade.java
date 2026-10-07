package com.gakkum.backend.application.owner.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.UpdateOwnerMeCommand;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.OwnerMeResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OwnerFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final BusinessCategoryService businessCategoryService;
    private final JwtService jwtService;
    private final JobService jobService;
    private final ProposalService proposalService;

    @Transactional
    public OwnerRegistrationResponse register(String username, OwnerRegistrationRequest request) {
        User user = userService.validateOwnerRegistration(username);
        ownerService.validateBusinessNumberAvailable(request.getNormalizedBusinessNumber());
        businessCategoryService.validateCategoryExists(request.getCategoryId());

        userService.completeOwnerRegistration(user, request.getOwnerName());
        ownerService.createOwnerProfile(request.toCommand(user.getId()), null);

        String accessToken = jwtService.issueAccessToken(username, UserRole.OWNER);
        String refreshToken = jwtService.replaceRefreshToken(username, UserRole.OWNER);
        return OwnerRegistrationResponse.of(accessToken, refreshToken);
    }

    /**
     * 사장님 본인의 기본 정보와 활동 개수. 보낸 의뢰는 취소만 뺀 누적 수라 진행 중·완료 수와 겹치고, 받은 제안은 지원서가 아닌 제안 기준이다.
     * 저장된 대표자 이름이 비어 있으면 가입자 이름으로 대신 응답하고 저장하지는 않는다. 사장님 프로필이 없으면 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public OwnerMeResult getMe(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_ME_REQUIRED);
        }
        Owner owner = ownerService.findOwnerProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        String representativeName = owner.getRepresentativeName();
        if (representativeName == null || representativeName.isBlank()) {
            representativeName = user.getName();
        }

        return OwnerMeResult.of(
                owner,
                user,
                representativeName,
                jobService.countOwnerJobsExcludingCancelled(owner.getId()),
                proposalService.countReceivedProposalsExcludingCancelled(owner.getId()),
                jobService.countOwnerInProgressJobs(owner.getId()),
                jobService.countOwnerClosedJobs(owner.getId()));
    }

    /**
     * 사장님 본인의 상호명·업종·프로필 사진·매장 주소·소개를 전체 저장한다. 선택 항목을 비워 보내면 기존 값을 지운다.
     * 입력을 모두 검증한 뒤에 쓰기 시작하고, 사장님 프로필이 없으면 500으로 거부한다.
     */
    @Transactional
    public void updateMe(UpdateOwnerMeCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_ME_UPDATE_REQUIRED);
        }
        Owner owner = ownerService.findOwnerProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        businessCategoryService.validateCategoryExists(command.getCategoryId());

        ownerService.updateOwnerProfile(owner, command);
    }
}
