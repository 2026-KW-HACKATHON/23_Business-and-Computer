package com.gakkum.backend.application.explore.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.domain.owner.service.StoreConcernService;

@DisplayName("탐색 파사드")
class ExploreFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final long AUTHOR_PROFILE_ID = 70L;
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 9, 30, 10, 0);

    private final UserService userService = mock(UserService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final JobService jobService = mock(JobService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final ExploreFacade exploreFacade = new ExploreFacade(
            userService, proposalService, jobService, ownerService, specialtyCategoryService,
            mock(BusinessCategoryService.class), studentService, mock(StoreConcernService.class));

    @BeforeEach
    void givenActiveUser() {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id("01K58M6PJV8VAJMXHBHJ2PNB5C").username(USERNAME).isLock(false).build());
    }

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
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));

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
        assertThat(card.getBudget()).isEqualTo(300_000L);
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
        new ExploreFacade(userService, firstProposalService, firstJobService, ownerService, specialtyCategoryService,
                mock(BusinessCategoryService.class), studentService, mock(StoreConcernService.class))
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
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));
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

    @Test
    @DisplayName("학생은 이번 페이지의 의뢰 ID만 한 번에 조회해 지원한 의뢰는 본인 지원서 상태로 채우고 아닌 의뢰는 비워 둔다")
    void fillsAppliedForStudentWithPageJobIdsOnly() {
        givenUser(UserRole.STUDENT);
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(proposal(5L, T2, 0, 50L, List.of(3L))));
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(8L, T3, 60L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED),
                job(9L, T2, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED),
                job(7L, T1, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(50L, "가꿈 분식", 60L, "가꿈 카페", 61L, "가꿈 꽃집"));
        givenSpecialties();
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(77L).userId(USER_ID).build()));
        when(jobService.getApplicationStatuses(77L, List.of(8L, 9L)))
                .thenReturn(Map.of(9L, JobApplicationStatus.REJECTED));

        ExploreResult result = exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 3, null));

        assertThat(result.getItems()).filteredOn(JobCardResult.class::isInstance)
                .extracting(item -> ((JobCardResult) item).getJobId(), item -> ((JobCardResult) item).getApplied())
                .containsExactly(tuple(8L, null), tuple(9L, JobApplicationStatus.REJECTED));
        // 다음 페이지로 밀린 의뢰 7은 지원 상태를 조회하지 않는다
        verify(jobService).getApplicationStatuses(77L, List.of(8L, 9L));
        verify(studentService).findStudentProfileByUserId(USER_ID);
    }

    @Test
    @DisplayName("학생이 아닌 사용자는 지원 상태를 비워 두고 학생 프로필과 지원서를 조회하지 않는다")
    void leavesAppliedEmptyForNonStudent() {
        givenUser(UserRole.OWNER);
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(9L, T2, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(61L, "가꿈 꽃집"));
        givenSpecialties();

        ExploreResult result = exploreFacade.explore(command(ExploreType.JOB, ExploreSort.LATEST, 20, null));

        assertThat(((JobCardResult) result.getItems().get(0)).getApplied()).isNull();
        verifyNoInteractions(studentService);
        verify(jobService, never()).getApplicationStatuses(any(), any());
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생은 지원서를 조회하지 않고 지원 상태를 비워 둔다")
    void treatsStudentWithoutProfileAsNotApplied() {
        givenUser(UserRole.STUDENT);
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(9L, T2, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(61L, "가꿈 꽃집"));
        givenSpecialties();
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        ExploreResult result = exploreFacade.explore(command(ExploreType.JOB, ExploreSort.LATEST, 20, null));

        assertThat(((JobCardResult) result.getItems().get(0)).getApplied()).isNull();
        verify(jobService, never()).getApplicationStatuses(any(), any());
    }

    @Test
    @DisplayName("이번 페이지에 의뢰 카드가 없으면 학생이어도 지원서를 조회하지 않는다")
    void skipsAppliedLookupWithoutJobCards() {
        givenUser(UserRole.STUDENT);
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(proposal(5L, T2, 0, 50L, List.of(3L))));
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(7L, T1, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));

        ExploreResult result = exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 1, null));

        assertThat(result.getItems()).extracting(ExploreItemResult::getType).containsExactly(ExploreItemType.PROPOSAL);
        verify(jobService, never()).getApplicationStatuses(any(), any());
    }

    @Test
    @DisplayName("제안 카드에 상태와 해결 방안 원문을 담고, 이번 페이지 제안의 작성자만 중복 없이 조회해 학생 이름을 채운다")
    @SuppressWarnings("unchecked")
    void fillsProposalAuthorStatusAndSolutionWithPageAuthorsOnly() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(
                proposal(5L, T3, 0, 50L, List.of(3L), 70L, ProposalStatus.AWAITING_START),
                proposal(4L, T2, 0, 50L, List.of(3L), 71L, ProposalStatus.REJECTED),
                proposal(3L, T1, 0, 50L, List.of(3L), 70L, ProposalStatus.ACCEPTED),
                proposal(2L, T1, 0, 50L, List.of(3L), 72L, ProposalStatus.PENDING)));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        givenAuthors(Map.of(70L, "김학생", 71L, "이학생"));

        ExploreResult result = exploreFacade.explore(command(ExploreType.PROPOSAL, ExploreSort.LATEST, 3, null));

        assertThat(result.getItems()).extracting(item -> (ProposalCardResult) item)
                .extracting(ProposalCardResult::getProposalId, ProposalCardResult::getStudentName,
                        ProposalCardResult::getStatus, ProposalCardResult::getProposedSolution)
                .containsExactly(
                        tuple(5L, "김학생", ProposalStatus.AWAITING_START, "해결 방안 5"),
                        tuple(4L, "이학생", ProposalStatus.REJECTED, "해결 방안 4"),
                        tuple(3L, "김학생", ProposalStatus.ACCEPTED, "해결 방안 3"));
        // 다음 페이지로 밀린 제안 2의 작성자 72는 조회하지 않고, 두 번 나온 작성자 70은 한 번만 조회한다
        verify(studentService).getStudentProfilesByIds(List.of(70L, 71L));
        ArgumentCaptor<Collection<String>> userIds = ArgumentCaptor.forClass(Collection.class);
        verify(userService).getUsersByIds(userIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder("user-70", "user-71");
    }

    @Test
    @DisplayName("학생은 이번 페이지의 제안 ID만 한 번에 조회해 본인이 공감한 제안만 true로 채우고 지원 상태와 학생 프로필 조회를 함께 쓴다")
    void fillsLikedByMeForStudentWithPageProposalIdsOnly() {
        givenUser(UserRole.STUDENT);
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(
                proposal(5L, T3, 3, 50L, List.of(3L)),
                proposal(4L, T2, 9, 50L, List.of(3L)),
                proposal(3L, T1, 1, 50L, List.of(3L))));
        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(8L, T2, 60L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(50L, "가꿈 분식", 60L, "가꿈 카페"));
        givenSpecialties();
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(77L).userId(USER_ID).build()));
        when(proposalService.getLikedProposalIds(77L, List.of(5L, 4L))).thenReturn(Set.of(5L));

        ExploreResult result = exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 3, null));

        // 제안 4는 다른 학생들의 공감으로 공감 수만 많고 본인 기록은 없다
        assertThat(result.getItems()).filteredOn(ProposalCardResult.class::isInstance)
                .extracting(item -> ((ProposalCardResult) item).getProposalId(),
                        item -> ((ProposalCardResult) item).isLikedByMe())
                .containsExactly(tuple(5L, true), tuple(4L, false));
        // 다음 페이지로 밀린 제안 3은 공감 여부를 조회하지 않는다
        verify(proposalService).getLikedProposalIds(77L, List.of(5L, 4L));
        verify(jobService).getApplicationStatuses(77L, List.of(8L));
        verify(studentService).findStudentProfileByUserId(USER_ID);
    }

    @Test
    @DisplayName("학생이 아닌 사용자의 제안 카드는 공감 수가 있어도 공감 여부가 false이고 학생 프로필과 공감 기록을 조회하지 않는다")
    void leavesLikedByMeFalseForNonStudent() {
        givenUser(UserRole.OWNER);
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(proposal(5L, T2, 4, 50L, List.of(3L))));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));

        ExploreResult result = exploreFacade.explore(command(ExploreType.PROPOSAL, ExploreSort.LATEST, 20, null));

        ProposalCardResult card = (ProposalCardResult) result.getItems().get(0);
        assertThat(card.isLikedByMe()).isFalse();
        assertThat(card.getStudentName()).isEqualTo("김학생");
        verify(studentService, never()).findStudentProfileByUserId(any());
        verify(proposalService, never()).getLikedProposalIds(any(), any());
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생은 공감 기록을 조회하지 않고 모든 제안을 공감하지 않은 것으로 본다")
    void treatsStudentWithoutProfileAsNotLiked() {
        givenUser(UserRole.STUDENT);
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(proposal(5L, T2, 4, 50L, List.of(3L))));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        givenAuthors(Map.of(AUTHOR_PROFILE_ID, "김학생"));
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        ExploreResult result = exploreFacade.explore(command(ExploreType.PROPOSAL, ExploreSort.LATEST, 20, null));

        assertThat(((ProposalCardResult) result.getItems().get(0)).isLikedByMe()).isFalse();
        verify(proposalService, never()).getLikedProposalIds(any(), any());
    }

    @Test
    @DisplayName("제안 카드가 없는 페이지와 빈 페이지에서는 학생이어도 작성자와 공감 기록을 조회하지 않는다")
    void skipsAuthorAndLikeLookupWithoutProposalCards() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(77L).userId(USER_ID).build()));

        assertThat(exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null)).getItems()).isEmpty();
        // 조회 조건에 쓰는 본인 프로필만 읽는다
        verify(studentService).findStudentProfileByUserId(USER_ID);
        verify(studentService, never()).getStudentProfilesByIds(any());

        when(jobService.getExploreJobs(any())).thenReturn(List.of(
                job(9L, T2, 61L, List.of(3L), JobStatus.OPEN, JobProgressStage.REQUESTED)));
        givenStoreNames(Map.of(61L, "가꿈 꽃집"));
        givenSpecialties();

        ExploreResult result = exploreFacade.explore(command(ExploreType.JOB, ExploreSort.LATEST, 20, null));

        assertThat(result.getItems()).extracting(ExploreItemResult::getType).containsExactly(ExploreItemType.JOB);
        verify(studentService, never()).getStudentProfilesByIds(any());
        verify(userService, never()).getUsersByIds(any());
        verify(proposalService, never()).getLikedProposalIds(any(), any());
    }

    @Test
    @DisplayName("제안 작성자의 학생 프로필이나 사용자를 찾지 못하면 일괄 조회의 INTERNAL_SERVER_ERROR를 그대로 전파한다")
    void propagatesMissingAuthorError() {
        when(proposalService.getExploreProposals(any())).thenReturn(List.of(proposal(5L, T2, 0, 50L, List.of(3L))));
        givenStoreNames(Map.of(50L, "가꿈 분식"));
        givenSpecialties();
        when(studentService.getStudentProfilesByIds(List.of(AUTHOR_PROFILE_ID)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> exploreFacade.explore(command(ExploreType.PROPOSAL, ExploreSort.LATEST, 20, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Test
    @DisplayName("학생의 탐색은 거절된 제안과 본인 제안, 본인 지원이 탈락한 의뢰를 빼도록 학생 프로필 ID를 조회 조건으로 넘긴다")
    void passesStudentProfileAsExploreFilter() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(77L).userId(USER_ID).build()));

        for (ExploreSort sort : ExploreSort.values()) {
            exploreFacade.explore(command(ExploreType.PROPOSAL, sort, 3L, 20, null));
            GetExploreProposalsCommand proposalCommand = captureProposalCommand();
            assertThat(proposalCommand.isRejectedExcluded()).isTrue();
            assertThat(proposalCommand.getExcludedStudentProfileId()).isEqualTo(77L);
        }
        exploreFacade.explore(command(ExploreType.JOB, ExploreSort.OLDEST, 20, null));
        GetExploreJobsCommand jobCommand = captureJobCommand();
        assertThat(jobCommand.getRejectedApplicantProfileId()).isEqualTo(77L);
        assertThat(jobCommand.getExcludedOwnerProfileId()).isNull();
        verify(ownerService, never()).findOwnerProfileByUserId(any());
    }

    @Test
    @DisplayName("사장님의 탐색은 본인 의뢰를 빼도록 사장님 프로필 ID를 넘기고, 거절된 제안은 빼되 작성 학생과 지원 이력으로는 거르지 않는다")
    void passesOwnerProfileAsExploreFilter() {
        givenUser(UserRole.OWNER);
        when(ownerService.findOwnerProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(55L).userId(USER_ID).build()));

        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null));

        GetExploreJobsCommand jobCommand = captureJobCommand();
        assertThat(jobCommand.getExcludedOwnerProfileId()).isEqualTo(55L);
        assertThat(jobCommand.getRejectedApplicantProfileId()).isNull();
        GetExploreProposalsCommand proposalCommand = captureProposalCommand();
        assertThat(proposalCommand.isRejectedExcluded()).isTrue();
        assertThat(proposalCommand.getExcludedStudentProfileId()).isNull();
    }

    @Test
    @DisplayName("프로필이 없는 사용자의 탐색은 본인 글과 지원 이력 필터 없이 거절된 제안만 뺀다")
    void skipsViewerFiltersWithoutProfile() {
        for (UserRole role : List.of(UserRole.STUDENT, UserRole.OWNER, UserRole.PENDING)) {
            givenUser(role);

            exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null));

            GetExploreProposalsCommand proposalCommand = captureProposalCommand();
            assertThat(proposalCommand.isRejectedExcluded()).isTrue();
            assertThat(proposalCommand.getExcludedStudentProfileId()).isNull();
            GetExploreJobsCommand jobCommand = captureJobCommand();
            assertThat(jobCommand.getExcludedOwnerProfileId()).isNull();
            assertThat(jobCommand.getRejectedApplicantProfileId()).isNull();
        }
    }

    private void givenUser(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(role).isLock(false).build());
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

    // 작성 학생 프로필 ID별 이름. 학생 프로필의 사용자 ID는 "user-<프로필 ID>"다
    private void givenAuthors(Map<Long, String> namesByStudentProfileId) {
        when(studentService.getStudentProfilesByIds(any())).thenReturn(namesByStudentProfileId.keySet().stream()
                .collect(Collectors.toMap(id -> id, id -> Student.builder().id(id).userId("user-" + id).build())));
        when(userService.getUsersByIds(any())).thenReturn(namesByStudentProfileId.entrySet().stream()
                .collect(Collectors.toMap(entry -> "user-" + entry.getKey(), entry -> User.builder()
                        .id("user-" + entry.getKey()).name(entry.getValue()).build())));
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
        return proposal(id, createdAt, likeCount, ownerProfileId, specialtyIds, AUTHOR_PROFILE_ID,
                ProposalStatus.PENDING);
    }

    private ExploreProposalData proposal(Long id, LocalDateTime createdAt, int likeCount, Long ownerProfileId,
            List<Long> specialtyIds, Long studentProfileId, ProposalStatus status) {
        return ExploreProposalData.of(Proposal.builder()
                .id(id)
                .studentProfileId(studentProfileId)
                .ownerProfileId(ownerProfileId)
                .title("제안 " + id)
                .proposedSolution("해결 방안 " + id)
                .status(status)
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
                .budget(300_000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .createdAt(createdAt)
                .build(), specialtyIds, progressStage);
    }

    @Test
    @DisplayName("실제 사용자의 탐색은 격리 범위 없이, 데모 사용자의 탐색은 자기 데모 세션 ID로 제안과 의뢰를 조회한다")
    void passesViewerDemoSessionToBothQueries() {
        exploreFacade.explore(command(ExploreType.ALL, ExploreSort.LATEST, 20, null));
        assertThat(captureProposalCommand().getDemoSessionId()).isNull();
        assertThat(captureJobCommand().getDemoSessionId()).isNull();

        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C").username(USERNAME).isLock(false)
                .demoSessionId("01K6DEMO00000000000000000A").build());
        for (ExploreSort sort : ExploreSort.values()) {
            exploreFacade.explore(command(ExploreType.PROPOSAL, sort, 20, null));
            assertThat(captureProposalCommand().getDemoSessionId()).isEqualTo("01K6DEMO00000000000000000A");
        }
        exploreFacade.explore(command(ExploreType.JOB, ExploreSort.OLDEST, 20, null));
        assertThat(captureJobCommand().getDemoSessionId()).isEqualTo("01K6DEMO00000000000000000A");
    }
}
