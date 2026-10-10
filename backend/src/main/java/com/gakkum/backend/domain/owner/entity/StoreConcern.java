package com.gakkum.backend.domain.owner.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사장님이 학생 제안에 참고하도록 올리는 가게 고민. 제안·의뢰와 연결하지 않는다.
 * 해결하면 지우지 않고 resolvedAt을 남기며, 가게마다 해결되지 않은 고민은 하나만 둔다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "store_concerns")
public class StoreConcern {

    /** 가게마다 해결되지 않은 고민을 하나로 막는 부분 유니크 인덱스 이름 */
    public static final String OPEN_OWNER_UNIQUE_INDEX = "store_concerns_open_owner_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_profile_id", nullable = false, updatable = false)
    private Long ownerProfileId;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 500)
    private String description;

    // 고르지 않으면 null. 특기 대분류(specialty_categories) ID다
    @Column(name = "specialty_category_id")
    private Long specialtyCategoryId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static StoreConcern create(Long ownerProfileId, String title, String description,
            Long specialtyCategoryId) {
        return StoreConcern.builder()
                .ownerProfileId(ownerProfileId)
                .title(title)
                .description(description)
                .specialtyCategoryId(specialtyCategoryId)
                .build();
    }

    /** 한 줄·설명·분야를 통째로 바꾼다. 설명·분야의 null은 기존 값 삭제다. */
    public void update(String title, String description, Long specialtyCategoryId) {
        this.title = title;
        this.description = description;
        this.specialtyCategoryId = specialtyCategoryId;
    }

    public void resolve(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
