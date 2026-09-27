package com.gakkum.backend.domain.media.dto;

/** 공개 이미지의 용도. 저장소 키의 경로 구분에 사용한다. */
public enum ImagePurpose {
    PROFILE("profile"),
    STORE("store");

    private final String keyPrefix;

    ImagePurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }
}
