package com.gakkum.backend.application.proposal.dto;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProposalCreateRequest {

    @NotNull
    @Positive
    private Long ownerProfileId;

    @NotEmpty
    private List<@NotNull @Positive Long> specialtyIds;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String customerProblem;

    @NotBlank
    @Size(max = 500)
    private String proposedSolution;

    @NotBlank
    @Size(max = 500)
    private String workPlan;

    @NotNull
    @Positive
    private Long proposedFee;

    // 수락일 기준 일수. 0이면 수락 당일
    @NotNull
    @PositiveOrZero
    private Integer draftDays;

    @NotNull
    @PositiveOrZero
    private Integer finalDays;

    // 선택 입력. 개수 상한 없음
    private List<@NotBlank String> referenceImageUrls;

    public static ProposalCreateRequest of(
            Long ownerProfileId,
            List<Long> specialtyIds,
            String title,
            String customerProblem,
            String proposedSolution,
            String workPlan,
            Long proposedFee,
            Integer draftDays,
            Integer finalDays,
            List<String> referenceImageUrls) {
        return ProposalCreateRequest.builder()
                .ownerProfileId(ownerProfileId)
                .specialtyIds(specialtyIds)
                .title(title)
                .customerProblem(customerProblem)
                .proposedSolution(proposedSolution)
                .workPlan(workPlan)
                .proposedFee(proposedFee)
                .draftDays(draftDays)
                .finalDays(finalDays)
                .referenceImageUrls(referenceImageUrls)
                .build();
    }

    @AssertTrue(message = "최종 완료 기간은 초안 기간보다 짧을 수 없습니다.")
    private boolean isDayOrderValid() {
        if (draftDays == null || finalDays == null) {
            return true;
        }
        return draftDays <= finalDays;
    }

    public CreateProposalCommand toCommand(String username) {
        return CreateProposalCommand.of(
                username,
                ownerProfileId,
                specialtyIds,
                title.trim(),
                customerProblem.trim(),
                proposedSolution.trim(),
                workPlan.trim(),
                proposedFee,
                draftDays,
                finalDays,
                referenceImageUrls == null ? List.of() : referenceImageUrls);
    }
}
