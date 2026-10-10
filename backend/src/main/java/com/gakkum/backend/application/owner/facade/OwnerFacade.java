package com.gakkum.backend.application.owner.facade;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.SaveStoreConcernCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.UpdateOwnerMeCommand;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.OwnerMeResult;
import com.gakkum.backend.domain.owner.dto.OwnerQueryDto.StoreConcernResult;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.entity.StoreConcern;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.owner.service.StoreConcernService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.ratelimit.RateLimitedAction;
import com.gakkum.backend.global.ratelimit.UserRateLimit;

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
    private final AuthService authService;
    private final MediaService mediaService;
    private final TransactionTemplate transactionTemplate;
    private final StoreConcernService storeConcernService;
    private final SpecialtyCategoryService specialtyCategoryService;

    /**
     * 사장님 회원가입. 가입 대기 사용자·사업자등록번호 중복·업종과 프로필·매장 사진이 본인이 올린 사진인지 먼저 확인하고, 국세청 사업자등록정보 진위 확인을
     * 통과해야 저장한다. 진위 확인은 사업자가 실제로 있는지만 본다. 사업자등록증의 대표자 이름과 가입하는 사람의
     * 이름(name)은 비교하지 않으므로 직원·대리인·공동 운영자처럼 대표자가 아닌 사람도 사업자 정보가 맞으면 가입할 수 있다.
     * 외부 확인 동안 DB 트랜잭션과 커넥션을 붙잡지 않도록 저장과 토큰 발급만 트랜잭션으로 묶는다.
     * 가입도 국세청을 부르므로 진위 확인 API와 같은 시간당 횟수를 나눠 쓴다.
     */
    @UserRateLimit(RateLimitedAction.OWNER_BUSINESS_VERIFICATION)
    public OwnerRegistrationResponse register(String username, OwnerRegistrationRequest request) {
        User pendingUser = userService.validateOwnerRegistration(username);
        ownerService.validateBusinessNumberAvailable(request.getNormalizedBusinessNumber());
        businessCategoryService.validateCategoryExists(request.getCategoryId());
        CreateOwnerProfileCommand profile = request.toCommand(pendingUser.getId());
        if (profile.getProfileImageUrl() != null) {
            mediaService.validateUploadedImages(
                    pendingUser.getId(), ImagePurpose.PROFILE, List.of(profile.getProfileImageUrl()));
        }
        mediaService.validateUploadedImages(pendingUser.getId(), ImagePurpose.STORE, profile.getStoreImageUrls());

        // 국세청이 "확인되지 않음"으로 답하면 400, 국세청에 닿지 못하면 진위 확인 API와 같은 503을 그대로 전달한다
        if (!authService.verifyOwnerBusiness(request.toBusinessVerificationCommand(username))) {
            throw new BusinessException(ErrorCode.OWNER_BUSINESS_NOT_VERIFIED);
        }

        return transactionTemplate.execute(status -> {
            // 외부 확인 사이에 가입이 끝났을 수 있어 트랜잭션 안에서 가입 대기 사용자를 다시 확인한다.
            // 같은 사업자등록번호의 동시 가입은 DB 유니크 제약이 막는다.
            User user = userService.validateOwnerRegistration(username);
            userService.completeOwnerRegistration(user, request.getOwnerName());
            ownerService.createOwnerProfile(request.toCommand(user.getId()), null);

            String accessToken = jwtService.issueAccessToken(username, UserRole.OWNER);
            String refreshToken = jwtService.replaceRefreshToken(username, UserRole.OWNER);
            return OwnerRegistrationResponse.of(accessToken, refreshToken);
        });
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
     * 새 프로필 사진은 본인이 올린 사진이어야 한다. 저장된 값을 그대로 보내면 예전 계정·데모 데이터의 다른 주소여도 그대로 둔다.
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
        String profileImageUrl = command.getProfileImageUrl();
        if (profileImageUrl != null && !profileImageUrl.equals(owner.getProfileImageUrl())) {
            mediaService.validateUploadedImages(user.getId(), ImagePurpose.PROFILE, List.of(profileImageUrl));
        }

        ownerService.updateOwnerProfile(owner, command);
    }

    /** 사장님 본인 가게의 해결되지 않은 고민. 없으면 빈 값이다. 사장님 프로필이 없으면 500으로 거부한다. */
    @Transactional(readOnly = true)
    public Optional<StoreConcernResult> getConcern(String username) {
        Owner owner = getConcernOwner(username);
        return storeConcernService.findOpenConcern(owner.getId()).map(this::toConcernResult);
    }

    /**
     * 가게 고민을 올리거나 해결되지 않은 고민을 통째로 고친다. 고른 분야(특기 대분류)는 있는 분야여야 한다.
     * 고민은 매장에 붙으므로 데모 사장님의 고민은 같은 데모 세션의 학생에게만 보인다.
     */
    @Transactional
    public StoreConcernResult saveConcern(SaveStoreConcernCommand command) {
        Owner owner = getConcernOwner(command.getUsername());
        if (command.getSpecialtyCategoryId() != null) {
            specialtyCategoryService.validateCategoryExists(command.getSpecialtyCategoryId());
        }
        return toConcernResult(storeConcernService.saveConcern(owner.getId(), command));
    }

    /** 「해결됐어요」. 해결되지 않은 고민이 없으면 404로 거부한다. */
    @Transactional
    public void resolveConcern(String username) {
        Owner owner = getConcernOwner(username);
        storeConcernService.resolveConcern(owner.getId());
    }

    private Owner getConcernOwner(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.OWNER_CONCERN_REQUIRED);
        }
        return ownerService.findOwnerProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private StoreConcernResult toConcernResult(StoreConcern concern) {
        Long categoryId = concern.getSpecialtyCategoryId();
        String categoryName = categoryId == null
                ? null
                : specialtyCategoryService.getCategoryNames(List.of(categoryId)).get(categoryId);
        return StoreConcernResult.of(concern, categoryName);
    }
}
