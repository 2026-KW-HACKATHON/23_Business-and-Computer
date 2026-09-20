package com.gakkum.backend.domain.user;

public enum SocialProviderType {
    KAKAO("카카오");

    private final String description;

    SocialProviderType(String description) {
        this.description = description;
    }
}
