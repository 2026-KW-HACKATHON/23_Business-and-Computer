package com.gakkum.backend.application.job.dto;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobCreateRequest {

    @NotEmpty
    private List<@NotNull @Positive Long> specialtyIds;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String description;

    @NotNull
    @Positive
    private Long budget;

    @NotNull
    @FutureOrPresent
    private LocalDate draftDeadline;

    @NotNull
    @FutureOrPresent
    private LocalDate finalDeadline;

    @NotNull
    @PositiveOrZero
    private Integer revisionCount;

    // 선택 입력. 최대 4장
    @Size(max = 4)
    private List<@NotBlank String> referenceImageUrls;

    public static JobCreateRequest of(
            List<Long> specialtyIds,
            String title,
            String description,
            Long budget,
            LocalDate draftDeadline,
            LocalDate finalDeadline,
            Integer revisionCount) {
        return of(specialtyIds, title, description, budget, draftDeadline, finalDeadline, revisionCount, List.of());
    }

    public static JobCreateRequest of(
            List<Long> specialtyIds,
            String title,
            String description,
            Long budget,
            LocalDate draftDeadline,
            LocalDate finalDeadline,
            Integer revisionCount,
            List<String> referenceImageUrls) {
        return JobCreateRequest.builder()
                .specialtyIds(specialtyIds)
                .title(title)
                .description(description)
                .budget(budget)
                .draftDeadline(draftDeadline)
                .finalDeadline(finalDeadline)
                .revisionCount(revisionCount)
                .referenceImageUrls(referenceImageUrls)
                .build();
    }

    @AssertTrue(message = "선지급 마감일은 최종 마감일보다 늦을 수 없습니다.")
    private boolean isDeadlineOrderValid() {
        if (draftDeadline == null || finalDeadline == null) {
            return true;
        }
        return !draftDeadline.isAfter(finalDeadline);
    }

    @AssertTrue(message = "참고 사진 URL은 중복될 수 없습니다.")
    private boolean isReferenceImageUrlsUnique() {
        return referenceImageUrls == null || new HashSet<>(referenceImageUrls).size() == referenceImageUrls.size();
    }

    public CreateJobCommand toCommand(Long ownerProfileId) {
        return CreateJobCommand.of(
                ownerProfileId,
                List.copyOf(specialtyIds),
                title.trim(),
                description.trim(),
                budget,
                draftDeadline,
                finalDeadline,
                revisionCount,
                referenceImageUrls == null ? List.of() : List.copyOf(referenceImageUrls));
    }
}
