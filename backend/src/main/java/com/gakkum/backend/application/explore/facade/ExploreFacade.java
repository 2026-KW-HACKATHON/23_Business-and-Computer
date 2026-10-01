package com.gakkum.backend.application.explore.facade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.ExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreCommandDto.StoreExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreCursor;
import com.gakkum.backend.application.explore.dto.ExploreItemType;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.BusinessCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.JobCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ProposalCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.StoreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.dto.StoreExploreCursor;
import com.gakkum.backend.application.explore.dto.StoreExploreSort;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.GetExploreStoresCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExploreFacade {

    // 첫 페이지에서 모든 행이 경계 안에 들도록 쓰는 시각. DB에 저장되는 생성 시각 범위 밖이다
    private static final LocalDateTime LATEST_START = LocalDateTime.of(9999, 1, 1, 0, 0);
    private static final LocalDateTime OLDEST_START = LocalDateTime.of(1900, 1, 1, 0, 0);

    private final UserService userService;
    private final ProposalService proposalService;
    private final JobService jobService;
    private final OwnerService ownerService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final BusinessCategoryService businessCategoryService;

    /**
     * 제안과 의뢰를 한 목록으로 탐색한다. 종류마다 커서 뒤의 카드를 size+1개까지 읽어 병합하고,
     * 한 장이 남으면 다음 페이지가 있다고 보고 이번 페이지 마지막 카드로 커서를 만든다.
     * 매장 이름과 대분류·소분류는 이번 페이지 카드에 대해서만 묶어서 조회한다.
     */
    @Transactional(readOnly = true)
    public ExploreResult explore(ExploreCommand command) {
        userService.getActiveUser(command.getUsername());
        int limit = command.getSize() + 1;

        List<Candidate> candidates = new ArrayList<>();
        if (command.getType() != ExploreType.JOB) {
            proposalService.getExploreProposals(proposalCommand(command, limit))
                    .forEach(data -> candidates.add(Candidate.of(data)));
        }
        if (command.getType() != ExploreType.PROPOSAL) {
            jobService.getExploreJobs(jobCommand(command, limit))
                    .forEach(data -> candidates.add(Candidate.of(data)));
        }
        candidates.sort(comparator(command.getSort()));

        boolean hasNext = candidates.size() > command.getSize();
        List<Candidate> page = hasNext ? candidates.subList(0, command.getSize()) : candidates;
        String nextCursor = hasNext ? toCursor(command, page.get(page.size() - 1)).encode() : null;
        return ExploreResult.of(toItems(page), nextCursor);
    }

    /**
     * 학생이 매장(사장님 프로필) 목록을 탐색한다. 커서 뒤의 매장을 size+1개까지 읽어
     * 한 개가 남으면 다음 페이지가 있다고 보고 이번 페이지 마지막 매장으로 커서를 만든다.
     * 업종 이름은 이번 페이지 매장에 대해서만 묶어서 조회한다.
     */
    @Transactional(readOnly = true)
    public StoreExploreResult exploreStores(StoreExploreCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.STORE_STUDENT_REQUIRED);
        }
        Long businessCategoryId = command.getBusinessCategoryId();
        if (businessCategoryId != null) {
            businessCategoryService.validateCategoryExists(businessCategoryId);
        }

        boolean oldestFirst = command.getSort() == StoreExploreSort.OLDEST;
        StoreExploreCursor cursor = command.getCursor();
        LocalDateTime createdAtBound = cursor == null
                ? (oldestFirst ? OLDEST_START : LATEST_START)
                : cursor.getCreatedAt();
        long idBound = cursor == null ? (oldestFirst ? Long.MIN_VALUE : Long.MAX_VALUE) : cursor.getId();
        List<Owner> stores = ownerService.getExploreStores(GetExploreStoresCommand.of(
                businessCategoryId, oldestFirst, createdAtBound, idBound, command.getSize() + 1));

        boolean hasNext = stores.size() > command.getSize();
        List<Owner> page = hasNext ? stores.subList(0, command.getSize()) : stores;
        String nextCursor = null;
        if (hasNext) {
            Owner last = page.get(page.size() - 1);
            nextCursor = StoreExploreCursor.of(
                    command.getSort(), businessCategoryId, last.getCreatedAt(), last.getId()).encode();
        }

        Map<Long, String> categoryNames = businessCategoryService.getCategoryNames(page.stream()
                .map(Owner::getCategoryId)
                .distinct()
                .toList());
        List<StoreItemResult> items = page.stream()
                .map(store -> StoreItemResult.of(store, BusinessCategoryResult.of(
                        store.getCategoryId(), categoryNames.get(store.getCategoryId()))))
                .toList();
        return StoreExploreResult.of(items, nextCursor);
    }

    private GetExploreProposalsCommand proposalCommand(ExploreCommand command, int limit) {
        ExploreCursor cursor = command.getCursor();
        return switch (command.getSort()) {
            case LATEST -> GetExploreProposalsCommand.of(command.getSpecialtyCategoryId(), ProposalExploreOrder.LATEST,
                    null, createdAtBound(cursor, false), idBound(cursor, ExploreItemType.PROPOSAL, false), limit);
            case OLDEST -> GetExploreProposalsCommand.of(command.getSpecialtyCategoryId(), ProposalExploreOrder.OLDEST,
                    null, createdAtBound(cursor, true), idBound(cursor, ExploreItemType.PROPOSAL, true), limit);
            case LIKES -> GetExploreProposalsCommand.of(command.getSpecialtyCategoryId(), ProposalExploreOrder.LIKES,
                    cursor == null ? Integer.MAX_VALUE : cursor.getLikeCount(),
                    createdAtBound(cursor, false), cursor == null ? Long.MAX_VALUE : cursor.getId(), limit);
        };
    }

    // 좋아요순은 제안 전용이라 의뢰 조회에는 최신순·오래된순만 온다
    private GetExploreJobsCommand jobCommand(ExploreCommand command, int limit) {
        boolean oldestFirst = command.getSort() == ExploreSort.OLDEST;
        ExploreCursor cursor = command.getCursor();
        return GetExploreJobsCommand.of(command.getSpecialtyCategoryId(), oldestFirst,
                createdAtBound(cursor, oldestFirst), idBound(cursor, ExploreItemType.JOB, oldestFirst), limit);
    }

    private static LocalDateTime createdAtBound(ExploreCursor cursor, boolean oldestFirst) {
        if (cursor == null) {
            return oldestFirst ? OLDEST_START : LATEST_START;
        }
        return cursor.getCreatedAt();
    }

    /**
     * 커서와 같은 시각의 행을 어디까지 읽을지 정하는 ID 경계. 저장소는 최신순이면 id < 경계, 오래된순이면 id > 경계로 비교한다.
     * 같은 종류면 커서 ID가 경계다. 같은 시각에서는 제안이 의뢰보다 앞이므로, 커서보다 앞 종류는 같은 시각 행을 모두 빼고
     * 뒤 종류는 모두 포함하도록 Long 최솟값·최댓값을 쓴다.
     */
    private static long idBound(ExploreCursor cursor, ExploreItemType target, boolean oldestFirst) {
        if (cursor == null) {
            return oldestFirst ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        if (cursor.getItemType() == target) {
            return cursor.getId();
        }
        boolean includeSameTime = target.ordinal() > cursor.getItemType().ordinal();
        if (oldestFirst) {
            return includeSameTime ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return includeSameTime ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

    private static Comparator<Candidate> comparator(ExploreSort sort) {
        Comparator<Candidate> byType = Comparator.comparing(Candidate::getType);
        return switch (sort) {
            case LATEST -> Comparator.comparing(Candidate::getCreatedAt).reversed()
                    .thenComparing(byType)
                    .thenComparing(Comparator.comparing(Candidate::getId).reversed());
            case OLDEST -> Comparator.comparing(Candidate::getCreatedAt)
                    .thenComparing(byType)
                    .thenComparing(Candidate::getId);
            case LIKES -> Comparator.comparing(Candidate::getLikeCount).reversed()
                    .thenComparing(Comparator.comparing(Candidate::getCreatedAt).reversed())
                    .thenComparing(Comparator.comparing(Candidate::getId).reversed());
        };
    }

    private static ExploreCursor toCursor(ExploreCommand command, Candidate last) {
        return ExploreCursor.of(command.getSort(), command.getType(), command.getSpecialtyCategoryId(),
                last.getType(), last.getLikeCount(), last.getCreatedAt(), last.getId());
    }

    private List<ExploreItemResult> toItems(List<Candidate> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        Map<Long, String> storeNames = ownerService.getStoreNames(page.stream()
                .map(Candidate::getOwnerProfileId)
                .distinct()
                .toList());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(page.stream()
                .flatMap(candidate -> candidate.getSpecialtyIds().stream())
                .distinct()
                .toList());

        return page.stream()
                .map(candidate -> {
                    String storeName = storeNames.get(candidate.getOwnerProfileId());
                    List<SpecialtyCategoryResult> categories =
                            groupSpecialties(candidate.getSpecialtyIds(), specialtiesById);
                    if (candidate.getProposal() != null) {
                        return (ExploreItemResult) ProposalCardResult.of(
                                candidate.getProposal().getProposal(), storeName, categories);
                    }
                    ExploreJobData job = candidate.getJob();
                    return (ExploreItemResult) JobCardResult.of(
                            job.getJob(), job.getProgressStage(), storeName, categories);
                })
                .toList();
    }

    // 다른 목록 API와 같은 형식으로 카드의 모든 소분류를 대분류 ID 순, 대분류 안에서는 소분류 ID 순으로 묶는다
    private static List<SpecialtyCategoryResult> groupSpecialties(
            List<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        Map<Long, List<SpecialtyDetail>> byCategory = specialtyIds.stream()
                .map(id -> {
                    SpecialtyDetail detail = specialtiesById.get(id);
                    if (detail == null) {
                        throw new IllegalStateException("Specialty not found: " + id);
                    }
                    return detail;
                })
                .sorted(Comparator.comparing(SpecialtyDetail::getId))
                .collect(Collectors.groupingBy(
                        SpecialtyDetail::getCategoryId,
                        TreeMap::new,
                        Collectors.toList()));

        return byCategory.entrySet().stream()
                .map(entry -> SpecialtyCategoryResult.of(
                        entry.getKey(),
                        entry.getValue().get(0).getCategoryName(),
                        entry.getValue().stream()
                                .map(detail -> SpecialtyResult.of(detail.getId(), detail.getName()))
                                .toList()))
                .toList();
    }

    /** 병합 정렬에 쓰는 카드 후보. 제안 또는 의뢰 중 하나만 담는다. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Candidate {

        private final ExploreItemType type;
        private final Long id;
        private final LocalDateTime createdAt;
        private final Integer likeCount;
        private final Long ownerProfileId;
        private final List<Long> specialtyIds;
        private final ExploreProposalData proposal;
        private final ExploreJobData job;

        static Candidate of(ExploreProposalData data) {
            return new Candidate(ExploreItemType.PROPOSAL, data.getProposal().getId(),
                    data.getProposal().getCreatedAt(), data.getProposal().getLikeCount(),
                    data.getProposal().getOwnerProfileId(), data.getSpecialtyIds(), data, null);
        }

        static Candidate of(ExploreJobData data) {
            return new Candidate(ExploreItemType.JOB, data.getJob().getId(), data.getJob().getCreatedAt(), null,
                    data.getJob().getOwnerProfileId(), data.getSpecialtyIds(), null, data);
        }
    }
}
