package com.gakkum.backend.application.explore.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.ExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreCursor;
import com.gakkum.backend.application.explore.dto.ExploreItemType;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreItemResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ExploreResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.JobCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ProposalCardResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.SpecialtyResult;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("탐색 파사드")
class ExploreFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final UserService userService = mock(UserService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final JobService jobService = mock(JobService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final ExploreFacade exploreFacade = new ExploreFacade(
            userService, proposalService, jobService, ownerService, specialtyCategoryService);

    @Test
    @DisplayName("최신순 전체 탐색은 두 종류를 size+1개씩 읽어 생성 시각, 같은 시각은 제안 먼저, ID 순으로 병합한다")
    void mergesLatestWithTieBreak() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(
                proposal(5L, T2, 0, 50L, List.of(3L)),
                proposal(4L, T1, 0, 50L, List.of(3L))));
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(8L, T3, 60L, List.of(11L, 3L), JobStatus.OPEN, JobProgressStage.REQUESTED),
                job(9L, T2, 61L, List.of(3L), JobStatus.MATCHED, JobProgressStage.DRAFT)));
        givenStoreNames(Map.of(50L, "가꿈 분식", 60L, "가꿈 카페", 61L, "가꿈 꽃집"));
        givenSpecialties();

        ExploreResult result = exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 3, null));

        assertThat(result.getItems()).extracting(ExploreItemResult::getType, this::itemId).containsExactly(
                tuple(ExploreItemType.JOB, 8L),
                tuple(ExploreItemType.PROPOSAL, 5L),
                tuple(ExploreItemType.JOB, 9L));
        assertThat(result.isHasNext()).isTrue();
        ExploreCursor next = ExploreCursor.decode(result.getNextCursor());
        assertThat(next.getItemType()).isEqualTo(ExploreItemType.JOB);
        assertThat(next.getCreatedAt()).isEqualTo(T2);
        assertThat(next.getId()).isEqualTo(9L);
        assertThat(next.matches(ExploreSort.LATEST, ExploreType.ALL, null)).isTrue();

        GetExploreProposalsCommand proposalCommand = captureProposalCommand();
        assertThat(proposalCommand.getOrder()).isEqualTo(ProposalExploreOrder.LATEST);
        assertThat(proposalCommand.getLimit()).isEqualTo(4);
        assertThat(proposalCommand.getCreatedAtBound()).isAfter(T3);
        GetExploreJobsCommand jobCommand = captureJobCommand();
        assertThat(jobCommand.isOldestFirst()).isFalse();
        assertThat(jobCommand.getLimit()).isEqualTo(4);
        assertThat(jobCommand.getCreatedAtBound()).isAfter(T3);
        // 이번 페이지에 없는 카드(제안 4)는 매장 이름을 조회하지 않는다
        verify(ownerService).getStoreNames(List.of(60L, 50L, 61L));
    }

    @Test
    @DisplayName("카드마다 연결된 모든 대분류와 소분류를 대분류 ID, 소분류 ID 순으로 묶고 매장 이름·진행 단계·상태를 채운다")
    void fillsCardDetails() {
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(9L, T2, 61L, List.of(11L, 4L, 3L), JobStatus.MATCHED, JobProgressStage.REVISION)));
        givenStoreNames(Map.of(61L, "가꿈 꽃집"));
        givenSpecialties();

        ExploreResult result = exploreFacade.explore(command(ExploreType.JOB, ExploreSort.LATEST, 20, null));

        JobCardResult card = (JobCardResult) result.getItems().get(0);
        assertThat(card.getJobId()).isEqualTo(9L);
        assertThat(card.getStoreName()).isEqualTo("가꿈 꽃집");
        assertThat(card.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(card.getProgressStage()).isEqualTo(JobProgressStage.REVISION);
        assertThat(card.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(card.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(card.getSpecialtyCategories())
                .extracting(SpecialtyCategoryResult::getId, SpecialtyCategoryResult::getName)
                .containsExactly(tuple(1L, "디자인"), tuple(2L, "영상"));
        assertThat(card.getSpecialtyCategories().get(0).getSpecialties())
                .extracting(SpecialtyResult::getId)
                .containsExactly(3L, 4L);
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("최신순 의뢰 커서면 같은 시각 제안은 이미 지나간 것으로 보고, 의뢰는 커서 ID보다 작은 것만 읽는다")
    void boundsAfterLatestJobCursor() {
        ExploreCursor cursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, 3L,
                ExploreItemType.JOB, null, T2, 9L);

        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 3L, 20, cursor));

        GetExploreProposalsCommand proposalCommand = captureProposalCommand();
        assertThat(proposalCommand.getSpecialtyCategoryId()).isEqualTo(3L);
        assertThat(proposalCommand.getCreatedAtBound()).isEqualTo(T2);
        assertThat(proposalCommand.getIdBound()).isEqualTo(Long.MIN_VALUE);
        GetExploreJobsCommand jobCommand = captureJobCommand();
        assertThat(jobCommand.getSpecialtyCategoryId()).isEqualTo(3L);
        assertThat(jobCommand.getCreatedAtBound()).isEqualTo(T2);
        assertThat(jobCommand.getIdBound()).isEqualTo(9L);
    }

    @Test
    @DisplayName("최신순 제안 커서면 제안은 커서 ID보다 작은 것만, 같은 시각 의뢰는 모두 읽는다")
    void boundsAfterLatestProposalCursor() {
        ExploreCursor cursor = ExploreCursor.of(ExploreSort.LATEST, ExploreType.ALL, null,
                ExploreItemType.PROPOSAL, null, T2, 5L);

        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, cursor));

        assertThat(captureProposalCommand().getIdBound()).isEqualTo(5L);
        assertThat(captureJobCommand().getIdBound()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("오래된순은 같은 시각 제안 먼저 순서를 유지하도록 경계 방향을 뒤집는다")
    void boundsForOldest() {
        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.OLDEST, 20, ExploreCursor.of(
                ExploreSort.OLDEST, ExploreType.ALL, null, ExploreItemType.PROPOSAL, null, T2, 5L)));
        assertThat(captureProposalCommand().getOrder()).isEqualTo(ProposalExploreOrder.OLDEST);
        assertThat(captureProposalCommand().getIdBound()).isEqualTo(5L);
        assertThat(captureJobCommand().isOldestFirst()).isTrue();
        assertThat(captureJobCommand().getIdBound()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    @DisplayName("오래된순 의뢰 커서면 같은 시각 제안은 모두 제외하고 첫 페이지는 가장 이른 경계에서 시작한다")
    void boundsForOldestJobCursorAndFirstPage() {
        ProposalService firstProposalService = mock(ProposalService.class);
        JobService firstJobService = mock(JobService.class);
        new ExploreFacade(userService, firstProposalService, firstJobService, ownerService, specialtyCategoryService)
                .explore(command(ExploreType.ALL, ExploreSort.OLDEST, 20, null));
        ArgumentCaptor<GetExploreJobsCommand> firstJob = ArgumentCaptor.forClass(GetExploreJobsCommand.class);
        verify(firstJobService).getExploreJobs(firstJob.capture());
        assertThat(firstJob.getValue().getCreatedAtBound()).isBefore(T1);
        assertThat(firstJob.getValue().getIdBound()).isEqualTo(Long.MIN_VALUE);

        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.OLDEST, 20, ExploreCursor.of(
                ExploreSort.OLDEST, ExploreType.ALL, null, ExploreItemType.JOB, null, T2, 9L)));
        assertThat(captureProposalCommand().getIdBound()).isEqualTo(Long.MAX_VALUE);
        assertThat(captureJobCommand().getIdBound()).isEqualTo(9L);
    }

    @Test
    @DisplayName("좋아요순은 의뢰를 읽지 않고 좋아요 수, 생성 시각, ID 내림차순으로 정렬해 좋아요 수를 커서에 담는다")
    void sortsByLikes() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(
                proposal(3L, T3, 7, 50L, List.of(3L)),
                proposal(2L, T2, 7, 50L, List.of(3L)),
                proposal(1L, T2, 7, 50L, List.of(3L)),
                proposal(6L, T3, 2, 50L, List.of(3L))));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        ExploreCursor previous = ExploreCursor.of(ExploreSort.LIKES, ExploreType.PROPOSAL, null,
                ExploreItemType.PROPOSAL, 9, T1, 10L);

        ExploreResult result = exploreFacade.explore(command(ExploreType.PROPOSAL, ExploreSort.LIKES, 3, previous));

        assertThat(result.getItems()).extracting(this::itemId).containsExactly(3L, 2L, 1L);
        ExploreCursor next = ExploreCursor.decode(result.getNextCursor());
        assertThat(next.getLikeCount()).isEqualTo(7);
        assertThat(next.getId()).isEqualTo(1L);
        GetExploreProposalsCommand proposalCommand = captureProposalCommand();
        assertThat(proposalCommand.getOrder()).isEqualTo(ProposalExploreOrder.LIKES);
        assertThat(proposalCommand.getLikeCountBound()).isEqualTo(9);
        assertThat(proposalCommand.getCreatedAtBound()).isEqualTo(T1);
        assertThat(proposalCommand.getIdBound()).isEqualTo(10L);
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록과 마지막 페이지를 반환하고 매장·분류를 조회하지 않는다")
    void returnsEmptyPage() {
        ExploreResult result = exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getNextCursor()).isNull();
        verifyNoInteractions(ownerService, specialtyCategoryService);
    }

    @Test
    @DisplayName("비활성 사용자는 UNAUTHORIZED로 거부하고 목록을 조회하지 않는다")
    void rejectsInactiveUser() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(proposalService, jobService);
    }

    private Long itemId(ExploreItemResult item) {
        return item instanceof ProposalCardResult proposal ? proposal.getProposalId()
                : ((JobCardResult) item).getJobId();
    }

    private GetExploreProposalsCommand captureProposalCommand() {
        ArgumentCaptor<GetExploreProposalsCommand> captor = ArgumentCaptor.forClass(GetExploreProposalsCommand.class);
        verify(proposalService, atLeastOnce()).getExploreProposals(captor.capture());
        return captor.getValue();
    }

    private GetExploreJobsCommand captureJobCommand() {
        ArgumentCaptor<GetExploreJobsCommand> captor = ArgumentCaptor.forClass(GetExploreJobsCommand.class);
        verify(jobService, atLeastOnce()).getExploreJobs(captor.capture());
        return captor.getValue();
    }

    private void givenStoreNames(Map<Long, String> storeNames) {
        when(ownerService.getStoreNames(any())).thenReturn(storeNames);
    }

    private void givenSpecialties() {
        when(specialtyCategoryService.getSpecialtyDetails(any())).thenReturn(Map.of(
                3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인"),
                4L, SpecialtyDetail.of(4L, "포스터 디자인", 1L, "디자인"),
                11L, SpecialtyDetail.of(11L, "숏폼 촬영", 2L, "영상")));
    }

    private ExploreCommand command(ExploreType type, ExploreSort sort, int size, ExploreCursor cursor) {
        return command(type, sort, null, size, cursor);
    }

    private ExploreCommand command(ExploreType type, ExploreSort sort, Long categoryId, int size,
            ExploreCursor cursor) {
        return ExploreCommand.of(USERNAME, categoryId, type, sort, size, cursor);
    }

    private ExploreProposalData proposal(Long id, LocalDateTime createdAt, int likeCount, Long ownerProfileId,
            List<Long> specialtyIds) {
        return ExploreProposalData.of(Proposal.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("제안 " + id)
                .likeCount(likeCount)
                .createdAt(createdAt)
                .build(), specialtyIds);
    }

    private ExploreJobData job(Long id, LocalDateTime createdAt, Long ownerProfileId, List<Long> specialtyIds,
            JobStatus status, JobProgressStage progressStage) {
        return ExploreJobData.of(Job.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("의뢰 " + id)
                .status(status)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .createdAt(createdAt)
                .build(), specialtyIds, progressStage);
    }
}
