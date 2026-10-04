package com.gakkum.backend.domain.proposal.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public final class ProposalCommandDto {

    private ProposalCommandDto() {
    }

    /** 제안한 학생이 결제된 제안 의뢰의 작업을 시작하려는 요청. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class StartProposalJobCommand {

        private final String username;
        private final Long jobId;

        public static StartProposalJobCommand of(String username, Long jobId) {
            return new StartProposalJobCommand(username, jobId);
        }
    }

    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CreateProposalCommand {

        private final String username;
        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final String title;
        private final String customerProblem;
        private final String proposedSolution;
        private final String workPlan;
        private final Long proposedFee;
        private final Integer draftDays;
        private final Integer finalDays;
        private final List<String> referenceImageUrls;

        public static CreateProposalCommand of(
                String username,
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
            return CreateProposalCommand.builder()
                    .username(username)
                    .ownerProfileId(ownerProfileId)
                    .specialtyIds(List.copyOf(specialtyIds))
                    .title(title)
                    .customerProblem(customerProblem)
                    .proposedSolution(proposedSolution)
                    .workPlan(workPlan)
                    .proposedFee(proposedFee)
                    .draftDays(draftDays)
                    .finalDays(finalDays)
                    .referenceImageUrls(List.copyOf(referenceImageUrls))
                    .build();
        }
    }

    /** 학생이 보낸 제안 목록 조회 조건 */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetMyProposalsCommand {

        private final Long studentProfileId;

        public static GetMyProposalsCommand of(Long studentProfileId) {
            return GetMyProposalsCommand.builder().studentProfileId(studentProfileId).build();
        }
    }

    /** 사장님이 받은 제안 목록 조회 조건 */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetReceivedProposalsCommand {

        private final Long ownerProfileId;

        public static GetReceivedProposalsCommand of(Long ownerProfileId) {
            return GetReceivedProposalsCommand.builder().ownerProfileId(ownerProfileId).build();
        }
    }

    /**
     * 탐색 목록의 제안 조회 조건. 경계 값은 이전 페이지 마지막 카드 위치이고 그 뒤의 제안만 limit개까지 읽는다.
     * likeCountBound는 좋아요순에서만 사용한다.
     */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GetExploreProposalsCommand {

        private final Long specialtyCategoryId;
        private final ProposalExploreOrder order;
        private final Integer likeCountBound;
        private final LocalDateTime createdAtBound;
        private final Long idBound;
        private final int limit;

        public static GetExploreProposalsCommand of(Long specialtyCategoryId, ProposalExploreOrder order,
                Integer likeCountBound, LocalDateTime createdAtBound, Long idBound, int limit) {
            return GetExploreProposalsCommand.builder()
                    .specialtyCategoryId(specialtyCategoryId)
                    .order(order)
                    .likeCountBound(likeCountBound)
                    .createdAtBound(createdAtBound)
                    .idBound(idBound)
                    .limit(limit)
                    .build();
        }
    }
}
