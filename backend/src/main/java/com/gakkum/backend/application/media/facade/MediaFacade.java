package com.gakkum.backend.application.media.facade;

import org.springframework.stereotype.Component;

import com.gakkum.backend.domain.media.dto.MediaCommandDto.PrepareImageUploadCommand;
import com.gakkum.backend.domain.media.dto.MediaQueryDto.PrepareImageUploadResult;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MediaFacade {

    private final UserService userService;
    private final MediaService mediaService;

    /** 가입 대기 사용자도 가입 요청에 넣을 사진을 올려야 하므로 역할은 확인하지 않는다. */
    public PrepareImageUploadResult prepareImageUpload(PrepareImageUploadCommand command) {
        User uploader = userService.getActiveUser(command.getUsername());
        return mediaService.prepareImageUpload(uploader.getId(), command.getPurpose(), command.getFileName(),
                command.getContentType(), command.getSize());
    }
}
