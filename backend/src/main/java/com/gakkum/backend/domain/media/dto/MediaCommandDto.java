package com.gakkum.backend.domain.media.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class MediaCommandDto {

    private MediaCommandDto() {
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PrepareImageUploadCommand {
        private final String username;
        private final ImagePurpose purpose;
        private final String fileName;
        private final String contentType;
        private final long size;

        public static PrepareImageUploadCommand of(String username, ImagePurpose purpose, String fileName,
                String contentType, long size) {
            return PrepareImageUploadCommand.builder()
                    .username(username)
                    .purpose(purpose)
                    .fileName(fileName)
                    .contentType(contentType)
                    .size(size)
                    .build();
        }
    }
}
