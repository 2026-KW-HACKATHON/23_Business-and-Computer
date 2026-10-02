package com.gakkum.backend.domain.owner.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "owner_profiles")
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 26)
    private String userId;

    @Column(name = "business_number", nullable = false, unique = true, length = 255)
    private String businessNumber;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(name = "representative_name", length = 255)
    private String representativeName;

    @Column(name = "store_name", nullable = false, length = 255)
    private String storeName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "store_address", length = 255)
    private String storeAddress;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "profile_image_url", length = 255)
    private String profileImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "store_image_url", columnDefinition = "jsonb")
    private List<String> storeImageUrls;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Owner create(
            String userId,
            String businessNumber,
            LocalDate openedAt,
            String representativeName,
            String storeName,
            Long categoryId,
            String storeAddress,
            String description,
            String profileImageUrl,
            List<String> storeImageUrls) {
        return Owner.builder()
                .userId(userId)
                .businessNumber(businessNumber)
                .openedAt(openedAt)
                .representativeName(representativeName)
                .storeName(storeName)
                .categoryId(categoryId)
                .storeAddress(storeAddress)
                .description(description)
                .profileImageUrl(profileImageUrl)
                .storeImageUrls(storeImageUrls)
                .build();
    }
}
