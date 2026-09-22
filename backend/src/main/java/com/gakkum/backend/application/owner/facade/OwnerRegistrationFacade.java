package com.gakkum.backend.application.owner.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationCommand;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OwnerRegistrationFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final BusinessCategoryService businessCategoryService;
    private final JwtService jwtService;

    @Transactional
    public OwnerRegistrationResponse register(String username, OwnerRegistrationCommand command) {
        User user = userService.validateOwnerRegistration(username);
        ownerService.validateBusinessNumberAvailable(command.getBusinessNumber());
        businessCategoryService.validateCategoryExists(command.getCategoryId());

        userService.completeOwnerRegistration(user, command.getName());
        ownerService.createOwnerProfile(CreateOwnerProfileCommand.of(
                user.getId(),
                command.getBusinessNumber(),
                command.getOpenedAt(),
                command.getRepresentativeName(),
                command.getStoreName(),
                command.getCategoryId(),
                command.getStoreAddress(),
                command.getDescription(),
                command.getProfileImageUrl(),
                command.getStoreImageUrls())
        );

        String accessToken = jwtService.issueAccessToken(username, UserRole.OWNER);
        String refreshToken = jwtService.replaceRefreshToken(username, UserRole.OWNER);
        return OwnerRegistrationResponse.of(accessToken, refreshToken);
    }
}
