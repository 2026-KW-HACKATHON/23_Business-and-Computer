package com.gakkum.backend.domain.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalLike;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class ProposalServiceTest {

    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final ProposalLikeRepository proposalLikeRepository = mock(ProposalLikeRepository.class);
    private final ProposalService proposalService =
            new ProposalService(proposalRepository, proposalSpecialtyRepository, proposalLikeRepository);

    @Test
    @DisplayName("발신 학생과 요청 값으로 좋아요 0개인 제안을 저장하고 저장된 제안 ID로 소분류를 저장한다")
    @SuppressWarnings("unchecked")
    void savesProposalAndSpecialties() {
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal proposal = invocation.getArgument(0);
            return Proposal.builder()
                    .id(31L)
                    .studentProfileId(proposal.getStudentProfileId())
                    .ownerProfileId(proposal.getOwnerProfileId())
                    .build();
        });
        CreateProposalCommand command = CreateProposalCommand.of("KAKAO_12345", 5L, List.of(1L, 2L), "제목",
                "문제", "해결", "계획", 50000L, 0, 7, List.of("https://bucket/a.png"));

        Proposal saved = proposalService.createProposal(command, 7L, null);

        assertThat(saved.getId()).isEqualTo(31L);
        ArgumentCaptor<Proposal> proposalCaptor = ArgumentCaptor.forClass(Proposal.class);
        verify(proposalRepository).save(proposalCaptor.capture());
        Proposal proposal = proposalCaptor.getValue();
        assertThat(proposal.getStudentProfileId()).isEqualTo(7L);
        assertThat(proposal.getOwnerProfileId()).isEqualTo(5L);
        assertThat(proposal.getTitle()).isEqualTo("제목");
        assertThat(proposal.getCustomerProblem()).isEqualTo("문제");
        assertThat(proposal.getProposedSolution()).isEqualTo("해결");
        assertThat(proposal.getWorkPlan()).isEqualTo("계획");
        assertThat(proposal.getProposedFee()).isEqualTo(50000L);
        assertThat(proposal.getDraftDays()).isZero();
        assertThat(proposal.getFinalDays()).isEqualTo(7);
        assertThat(proposal.getReferenceImageUrls()).containsExactly("https://bucket/a.png");
        assertThat(proposal.getLikeCount()).isZero();

        ArgumentCaptor<List<ProposalSpecialty>> specialtiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(proposalSpecialtyRepository).saveAll(specialtiesCaptor.capture());
        assertThat(specialtiesCaptor.getValue())
                .extracting(ProposalSpecialty::getProposalId, ProposalSpecialty::getSpecialtyId)
                .containsExactly(
                        tuple(31L, 1L),
                        tuple(31L, 2L));
    }

    @Test
    @DisplayName("수신 사장님과 무관하게 ID로 찾은 제안과 제안에 선택된 소분류 ID를 반환한다")
    void returnsProposalDetailWithSpecialtyIds() {
        Proposal proposal = Proposal.builder().id(31L).ownerProfileId(5L).build();
        when(proposalRepository.findById(31L)).thenReturn(Optional.of(proposal));
        when(proposalSpecialtyRepository.findByProposalId(31L)).thenReturn(List.of(
                ProposalSpecialty.create(31L, 4L),
                ProposalSpecialty.create(31L, 1L)));

        ProposalDetailData data = proposalService.getProposalDetail(31L);

        assertThat(data.getProposal()).isSameAs(proposal);
        assertThat(data.getSpecialtyIds()).containsExactly(4L, 1L);
    }

    @Test
    @DisplayName("없는 제안은 PROPOSAL_404로 거부하고 소분류를 조회하지 않는다")
    void rejectsMissingProposal() {
        when(proposalRepository.findById(31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proposalService.getProposalDetail(31L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_NOT_FOUND));
        verify(proposalSpecialtyRepository, never()).findByProposalId(anyLong());
    }

    @Test
    @DisplayName("탐색 제안은 정렬별 저장소 조회에 분류·커서 경계·개수를 넘기고 제안별 소분류 ID를 붙인다")
    void returnsExploreProposalsWithSpecialtyIds() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal first = Proposal.builder().id(32L).build();
        Proposal second = Proposal.builder().id(31L).build();
        when(proposalRepository.findExploreByLikesInCategory(null, ProposalStatus.CANCELLED, 3L, 5, bound, 40L, Limit.of(21)))
                .thenReturn(List.of(first, second));
        when(proposalSpecialtyRepository.findByProposalIdIn(List.of(32L, 31L))).thenReturn(List.of(
                ProposalSpecialty.create(31L, 2L),
                ProposalSpecialty.create(32L, 7L),
                ProposalSpecialty.create(32L, 1L)));

        List<ExploreProposalData> data = proposalService.getExploreProposals(GetExploreProposalsCommand.of(
                null, 3L, ProposalExploreOrder.LIKES, 5, bound, 40L, 21));

        assertThat(data).extracting(ExploreProposalData::getProposal).containsExactly(first, second);
        assertThat(data.get(0).getSpecialtyIds()).containsExactly(7L, 1L);
        assertThat(data.get(1).getSpecialtyIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("대분류가 있으면 정렬별 분류 쿼리 하나로 읽고, 결과가 없으면 소분류를 조회하지 않는다")
    void usesCategoryQueryPerOrder() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);

        for (ProposalExploreOrder order : ProposalExploreOrder.values()) {
            assertThat(proposalService.getExploreProposals(
                    GetExploreProposalsCommand.of(null, 4L, order, 5, bound, 9L, 3))).isEmpty();
        }

        verify(proposalRepository).findExploreLatestInCategory(null, ProposalStatus.CANCELLED, 4L, bound, 9L, Limit.of(3));
        verify(proposalRepository).findExploreOldestInCategory(null, ProposalStatus.CANCELLED, 4L, bound, 9L, Limit.of(3));
        verify(proposalRepository).findExploreByLikesInCategory(null, ProposalStatus.CANCELLED, 4L, 5, bound, 9L, Limit.of(3));
        verify(proposalSpecialtyRepository, never()).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("대분류 없는 최신순은 경계 시각과 같은 행부터 읽고 남은 개수만큼 경계 이전 행을 이어 붙인다")
    void readsLatestSegmentsInOrder() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal sameTime = Proposal.builder().id(8L).build();
        Proposal earlier = Proposal.builder().id(20L).build();
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(null, ProposalStatus.CANCELLED, bound, 9L, Limit.of(3)))
                .thenReturn(List.of(sameTime));
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, ProposalStatus.CANCELLED, bound, Limit.of(2)))
                .thenReturn(List.of(earlier));

        List<ExploreProposalData> data = proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, null, ProposalExploreOrder.LATEST, null, bound, 9L, 3));

        assertThat(data).extracting(ExploreProposalData::getProposal).containsExactly(sameTime, earlier);
    }

    @Test
    @DisplayName("대분류 없는 오래된순은 경계 시각과 같은 행으로 개수가 차면 경계 이후 구간을 조회하지 않는다")
    void skipsLaterSegmentWhenFilled() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(null, ProposalStatus.CANCELLED, bound, 9L, Limit.of(2)))
                .thenReturn(List.of(Proposal.builder().id(10L).build(), Proposal.builder().id(11L).build()));

        assertThat(proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, null, ProposalExploreOrder.OLDEST, null, bound, 9L, 2))).hasSize(2);
        verify(proposalRepository, never()).findByDemoSessionIdAndStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(any(), any(), any(), any());
    }

    @Test
    @DisplayName("대분류 없는 좋아요순은 같은 좋아요·같은 시각, 같은 좋아요·이전 시각, 더 적은 좋아요 순으로 이어 읽는다")
    void readsLikesSegmentsInOrder() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal first = Proposal.builder().id(1L).build();
        Proposal second = Proposal.builder().id(2L).build();
        Proposal third = Proposal.builder().id(3L).build();
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(null, ProposalStatus.CANCELLED, 5, bound, 9L, Limit.of(4)))
                .thenReturn(List.of(first));
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(null, ProposalStatus.CANCELLED, 5, bound, Limit.of(3)))
                .thenReturn(List.of(second));
        when(proposalRepository.findByDemoSessionIdAndStatusNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(null, ProposalStatus.CANCELLED, 5, Limit.of(2)))
                .thenReturn(List.of(third));

        List<ExploreProposalData> data = proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, null, ProposalExploreOrder.LIKES, 5, bound, 9L, 4));

        assertThat(data).extracting(ExploreProposalData::getProposal).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("내가 보낸 제안은 본인 학생 ID로 조회하고 소분류 연결을 한 번에 붙인다")
    void readsMyProposalsWithBatchedSpecialties() {
        when(proposalRepository.findByStudentProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(7L, ProposalStatus.CANCELLED)).thenReturn(List.of(
                Proposal.builder().id(32L).build(), Proposal.builder().id(31L).build()));
        when(proposalSpecialtyRepository.findByProposalIdIn(List.of(32L, 31L))).thenReturn(List.of(
                ProposalSpecialty.builder().proposalId(32L).specialtyId(3L).build(),
                ProposalSpecialty.builder().proposalId(32L).specialtyId(4L).build()));

        List<ExploreProposalData> result = proposalService.getMyProposals(GetMyProposalsCommand.of(7L));

        assertThat(result).extracting(data -> data.getProposal().getId()).containsExactly(32L, 31L);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(3L, 4L);
        assertThat(result.get(1).getSpecialtyIds()).isEmpty();
        verify(proposalSpecialtyRepository, org.mockito.Mockito.times(1)).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("내가 보낸 제안이 없으면 소분류를 조회하지 않는다")
    void skipsSpecialtiesForEmptyMyProposals() {
        when(proposalRepository.findByStudentProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(7L, ProposalStatus.CANCELLED)).thenReturn(List.of());

        assertThat(proposalService.getMyProposals(GetMyProposalsCommand.of(7L))).isEmpty();
        verify(proposalSpecialtyRepository, never()).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("받은 제안은 사장님 프로필 ID로 조회하고 소분류 연결을 한 번에 붙인다")
    void readsReceivedProposalsWithBatchedSpecialties() {
        when(proposalRepository.findByOwnerProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(5L, ProposalStatus.CANCELLED)).thenReturn(List.of(
                Proposal.builder().id(32L).build(), Proposal.builder().id(31L).build()));
        when(proposalSpecialtyRepository.findByProposalIdIn(List.of(32L, 31L))).thenReturn(List.of(
                ProposalSpecialty.builder().proposalId(32L).specialtyId(3L).build(),
                ProposalSpecialty.builder().proposalId(32L).specialtyId(4L).build()));

        List<ExploreProposalData> result = proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(5L));

        assertThat(result).extracting(data -> data.getProposal().getId()).containsExactly(32L, 31L);
        assertThat(result.get(0).getSpecialtyIds()).containsExactly(3L, 4L);
        assertThat(result.get(1).getSpecialtyIds()).isEmpty();
        verify(proposalSpecialtyRepository, org.mockito.Mockito.times(1)).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("받은 제안이 없으면 소분류를 조회하지 않는다")
    void skipsSpecialtiesForEmptyReceivedProposals() {
        when(proposalRepository.findByOwnerProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(5L, ProposalStatus.CANCELLED)).thenReturn(List.of());

        assertThat(proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(5L))).isEmpty();
        verify(proposalSpecialtyRepository, never()).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("결제할 제안은 행을 잠가 읽고, 없는 제안은 404, 다른 사장님이 받은 제안은 403, 결제 전이 아닌 제안은 409로 거부한다")
    void returnsPayableProposalForUpdate() {
        Proposal pending = Proposal.builder().id(5L).ownerProfileId(7L).status(ProposalStatus.PENDING).build();
        when(proposalRepository.findLockedById(5L)).thenReturn(Optional.of(pending));
        when(proposalRepository.findLockedById(6L)).thenReturn(Optional.of(
                Proposal.builder().id(6L).ownerProfileId(7L).status(ProposalStatus.AWAITING_START).build()));
        when(proposalRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertThat(proposalService.getPayableProposalForUpdate(5L, 7L)).isSameAs(pending);
        assertProposalError(() -> proposalService.getPayableProposalForUpdate(99L, 7L), ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.getPayableProposalForUpdate(5L, 8L),
                ErrorCode.PROPOSAL_PAYMENT_FORBIDDEN);
        assertProposalError(() -> proposalService.getPayableProposalForUpdate(6L, 7L),
                ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
        verify(proposalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("작업을 시작할 제안은 행을 잠가 읽고, 다른 학생의 제안은 403, 결제되지 않았거나 거절된 제안은 409로 거부한다")
    void returnsStartableProposalForUpdate() {
        Proposal awaiting = Proposal.builder().id(5L).studentProfileId(31L)
                .status(ProposalStatus.AWAITING_START).build();
        Proposal accepted = Proposal.builder().id(6L).studentProfileId(31L).status(ProposalStatus.ACCEPTED).build();
        when(proposalRepository.findLockedById(5L)).thenReturn(Optional.of(awaiting));
        when(proposalRepository.findLockedById(6L)).thenReturn(Optional.of(accepted));
        when(proposalRepository.findLockedById(7L)).thenReturn(Optional.of(
                Proposal.builder().id(7L).studentProfileId(31L).status(ProposalStatus.PENDING).build()));
        when(proposalRepository.findLockedById(8L)).thenReturn(Optional.of(
                Proposal.builder().id(8L).studentProfileId(31L).status(ProposalStatus.REJECTED).build()));

        assertThat(proposalService.getStartableProposalForUpdate(5L, 31L)).isSameAs(awaiting);
        assertThat(proposalService.getStartableProposalForUpdate(6L, 31L)).isSameAs(accepted);
        assertProposalError(() -> proposalService.getStartableProposalForUpdate(5L, 32L),
                ErrorCode.JOB_START_FORBIDDEN);
        assertProposalError(() -> proposalService.getStartableProposalForUpdate(7L, 31L),
                ErrorCode.JOB_START_NOT_AVAILABLE);
        assertProposalError(() -> proposalService.getStartableProposalForUpdate(8L, 31L),
                ErrorCode.JOB_START_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("제안의 소분류 ID를 의뢰에 복사할 수 있도록 그대로 반환한다")
    void returnsSpecialtyIdsOfProposal() {
        when(proposalSpecialtyRepository.findByProposalId(5L)).thenReturn(List.of(
                ProposalSpecialty.create(5L, 3L), ProposalSpecialty.create(5L, 11L)));

        assertThat(proposalService.getSpecialtyIds(5L)).containsExactly(3L, 11L);
    }

    @Test
    @DisplayName("공감한 제안은 학생 프로필 ID와 제안 ID 목록으로 한 번에 조회해 공감 기록이 있는 제안 ID만 반환한다")
    void returnsLikedProposalIdsOfStudent() {
        when(proposalLikeRepository.findByStudentProfileIdAndProposalIdIn(77L, List.of(5L, 6L, 7L)))
                .thenReturn(List.of(ProposalLike.create(5L, 77L), ProposalLike.create(7L, 77L)));

        assertThat(proposalService.getLikedProposalIds(77L, List.of(5L, 6L, 7L))).containsExactlyInAnyOrder(5L, 7L);
    }

    @Test
    @DisplayName("공감 기록이 없으면 빈 집합을 반환하고, 제안 ID 목록이 비어 있으면 공감 기록을 조회하지 않는다")
    void returnsNoLikedProposalIdsWithoutRecordsOrIds() {
        assertThat(proposalService.getLikedProposalIds(77L, List.of(5L))).isEmpty();
        verify(proposalLikeRepository).findByStudentProfileIdAndProposalIdIn(77L, List.of(5L));

        ProposalLikeRepository unused = mock(ProposalLikeRepository.class);
        assertThat(new ProposalService(proposalRepository, proposalSpecialtyRepository, unused)
                .getLikedProposalIds(77L, List.of())).isEmpty();
        verifyNoInteractions(unused);
    }

    @Test
    @DisplayName("처음 공감하면 제안 행을 잠근 뒤 공감 기록을 저장하고 공감 수를 1 올린다")
    void likesProposalOnce() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L)).thenReturn(Optional.empty());

        ProposalLikeData liked = proposalService.likeProposal(31L, 77L, null);

        Proposal result = liked.getProposal();
        assertThat(liked.isAdded()).isTrue();
        assertThat(result).isSameAs(proposal);
        assertThat(result.getLikeCount()).isEqualTo(5);
        ArgumentCaptor<ProposalLike> captor = ArgumentCaptor.forClass(ProposalLike.class);
        verify(proposalLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getProposalId()).isEqualTo(31L);
        assertThat(captor.getValue().getStudentProfileId()).isEqualTo(77L);
        // 공감 기록은 제안 행을 잠근 뒤에 읽는다
        InOrder order = inOrder(proposalRepository, proposalLikeRepository);
        order.verify(proposalRepository).findLockedById(31L);
        order.verify(proposalLikeRepository).findByProposalIdAndStudentProfileId(31L, 77L);
        verify(proposalRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("이미 공감한 제안에 다시 공감하면 기록을 저장하지 않고 공감 수를 그대로 둔다")
    void keepsLikeCountWhenAlreadyLiked() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L))
                .thenReturn(Optional.of(ProposalLike.create(31L, 77L)));

        ProposalLikeData liked = proposalService.likeProposal(31L, 77L, null);

        assertThat(liked.isAdded()).isFalse();
        assertThat(liked.getProposal().getLikeCount()).isEqualTo(4);
        verify(proposalLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("공감을 취소하면 제안 행을 잠근 뒤 본인의 공감 기록을 삭제하고 공감 수를 1 내린다")
    void unlikesProposalOnce() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, null);
        ProposalLike like = ProposalLike.create(31L, 77L);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L)).thenReturn(Optional.of(like));

        Proposal result = proposalService.unlikeProposal(31L, 77L, null);

        assertThat(result).isSameAs(proposal);
        assertThat(result.getLikeCount()).isEqualTo(3);
        verify(proposalLikeRepository).delete(like);
        InOrder order = inOrder(proposalRepository, proposalLikeRepository);
        order.verify(proposalRepository).findLockedById(31L);
        order.verify(proposalLikeRepository).findByProposalIdAndStudentProfileId(31L, 77L);
    }

    @Test
    @DisplayName("공감하지 않은 제안의 공감을 취소하면 기록을 삭제하지 않고 공감 수를 그대로 둔다")
    void keepsLikeCountWhenNotLiked() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L)).thenReturn(Optional.empty());

        assertThat(proposalService.unlikeProposal(31L, 77L, null).getLikeCount()).isEqualTo(4);
        verify(proposalLikeRepository, never()).delete(any());
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "CANCELLED", "REJECTED" }, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("취소·거절되지 않은 모든 상태의 제안과 본인이 작성한 제안에 공감하고 취소할 수 있다")
    void likesAndUnlikesOwnProposalInEveryStatus(ProposalStatus status) {
        // 공감하는 학생 77번이 제안의 작성자다
        Proposal proposal = likeableProposal(status, 0, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ProposalLike.create(31L, 77L)));

        assertThat(proposalService.likeProposal(31L, 77L, null).getProposal().getLikeCount()).isEqualTo(1);
        assertThat(proposalService.unlikeProposal(31L, 77L, null).getLikeCount()).isZero();
        assertThat(proposal.getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("취소된 제안의 공감 추가·취소는 PROPOSAL_404로 거부하고 공감 기록과 공감 수를 바꾸지 않는다")
    void rejectsLikeForCancelledProposal() {
        Proposal proposal = likeableProposal(ProposalStatus.CANCELLED, 0, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));

        assertProposalError(() -> proposalService.likeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.unlikeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
        assertThat(proposal.getLikeCount()).isZero();
        verifyNoInteractions(proposalLikeRepository);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "PENDING", "CANCELLED" })
    @DisplayName("취소할 제안은 행을 잠가 읽고 결제 전이거나 이미 취소된 본인 제안을 그대로 반환한다")
    void returnsCancellableProposalForUpdate(ProposalStatus status) {
        Proposal proposal = likeableProposal(status, 2, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));

        assertThat(proposalService.getCancellableProposalForUpdate(31L, 77L, null)).isSameAs(proposal);
        assertThat(proposal.getStatus()).isEqualTo(status);
        verify(proposalRepository, never()).findById(anyLong());
        verifyNoInteractions(proposalLikeRepository);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "AWAITING_START", "ACCEPTED", "REJECTED" })
    @DisplayName("결제됐거나 거절된 제안의 취소는 PROPOSAL_409_CANCEL로 거부한다")
    void rejectsCancelOfUnavailableStatus(ProposalStatus status) {
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(likeableProposal(status, 2, null)));

        assertProposalError(() -> proposalService.getCancellableProposalForUpdate(31L, 77L, null),
                ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("없는 제안의 취소는 PROPOSAL_404, 다른 학생의 제안은 상태와 무관하게 PROPOSAL_403_CANCEL로 거부한다")
    void rejectsCancelOfMissingOrOthersProposal() {
        when(proposalRepository.findLockedById(99L)).thenReturn(Optional.empty());
        when(proposalRepository.findLockedById(31L))
                .thenReturn(Optional.of(likeableProposal(ProposalStatus.PENDING, 2, null)))
                .thenReturn(Optional.of(likeableProposal(ProposalStatus.ACCEPTED, 2, null)))
                .thenReturn(Optional.of(likeableProposal(ProposalStatus.CANCELLED, 0, null)));

        assertProposalError(() -> proposalService.getCancellableProposalForUpdate(99L, 77L, null),
                ErrorCode.PROPOSAL_NOT_FOUND);
        // 작성자 검증이 상태 검증보다 먼저라 다른 학생은 결제된 제안·취소된 제안에도 403을 받는다
        for (int i = 0; i < 3; i++) {
            assertProposalError(() -> proposalService.getCancellableProposalForUpdate(31L, 78L, null),
                    ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        }
    }

    @ParameterizedTest(name = "학생 {0}, 제안 {1}")
    @CsvSource(value = {
            "null, 01K6DEMO00000000000000000A",
            "01K6DEMO00000000000000000A, null",
            "01K6DEMO00000000000000000A, 01K6DEMO00000000000000000B"}, nullValues = "null")
    @DisplayName("격리 범위가 다른 제안의 취소는 작성자 검증보다 먼저 PROPOSAL_404로 거부한다")
    void rejectsCancelOutsideDemoSession(String studentSession, String proposalSession) {
        when(proposalRepository.findLockedById(31L))
                .thenReturn(Optional.of(likeableProposal(ProposalStatus.PENDING, 2, proposalSession)));

        // 작성자 본인(77번)과 다른 학생(78번) 모두 404다
        assertProposalError(() -> proposalService.getCancellableProposalForUpdate(31L, 77L, studentSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.getCancellableProposalForUpdate(31L, 78L, studentSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
    }

    @Test
    @DisplayName("제안을 취소하면 상태를 바꾸고 공감 수를 0으로 되돌리며 그 제안의 공감 기록을 모두 지운다")
    void cancelsProposalAndDeletesAllLikes() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, null);

        proposalService.cancelProposal(proposal);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(proposal.getLikeCount()).isZero();
        verify(proposalLikeRepository).deleteAllByProposalId(31L);
        verifyNoInteractions(proposalSpecialtyRepository);
    }

    @Test
    @DisplayName("취소할 수 없는 상태의 제안은 공감 기록을 지우지 않고 409로 거부한다")
    void keepsLikesWhenCancelIsRejected() {
        Proposal proposal = likeableProposal(ProposalStatus.AWAITING_START, 4, null);

        assertProposalError(() -> proposalService.cancelProposal(proposal), ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);
        assertThat(proposal.getLikeCount()).isEqualTo(4);
        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("없는 제안의 공감 추가·취소는 PROPOSAL_404로 거부하고 공감 기록을 조회하지 않는다")
    void rejectsLikeForMissingProposal() {
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.empty());

        assertProposalError(() -> proposalService.likeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.unlikeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
        verifyNoInteractions(proposalLikeRepository);
    }

    @ParameterizedTest(name = "학생 {0}, 제안 {1}")
    @CsvSource(value = {
            "null, 01K6DEMO00000000000000000A",
            "01K6DEMO00000000000000000A, null",
            "01K6DEMO00000000000000000A, 01K6DEMO00000000000000000B"}, nullValues = "null")
    @DisplayName("격리 범위가 다른 제안의 공감 추가·취소는 PROPOSAL_404로 거부하고 공감 기록과 공감 수를 바꾸지 않는다")
    void rejectsLikeOutsideDemoSession(String studentSession, String proposalSession) {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 4, proposalSession);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));

        assertProposalError(() -> proposalService.likeProposal(31L, 77L, studentSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.unlikeProposal(31L, 77L, studentSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertThat(proposal.getLikeCount()).isEqualTo(4);
        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("같은 데모 세션의 제안에는 공감할 수 있다")
    void likesProposalWithinDemoSession() {
        Proposal proposal = likeableProposal(ProposalStatus.PENDING, 0, "01K6DEMO00000000000000000A");
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));
        when(proposalLikeRepository.findByProposalIdAndStudentProfileId(31L, 77L)).thenReturn(Optional.empty());

        assertThat(proposalService.likeProposal(31L, 77L, "01K6DEMO00000000000000000A").getProposal()
                .getLikeCount()).isEqualTo(1);
    }

    @ParameterizedTest(name = "거절 주체 {0}")
    @CsvSource(value = { "OWNER", "STUDENT", "NULL" }, nullValues = "NULL")
    @DisplayName("거절된 제안의 공감 추가·취소는 거절 주체와 무관하게 PROPOSAL_409_LIKE로 거부하고 공감 기록과 공감 수를 바꾸지 않는다")
    void rejectsLikeForRejectedProposal(ProposalRejectedBy rejectedBy) {
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(77L).ownerProfileId(5L)
                .status(ProposalStatus.REJECTED).rejectedBy(rejectedBy).likeCount(3).build();
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(proposal));

        assertProposalError(() -> proposalService.likeProposal(31L, 77L, null),
                ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);
        assertProposalError(() -> proposalService.unlikeProposal(31L, 77L, null),
                ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);
        assertThat(proposal.getLikeCount()).isEqualTo(3);
        verifyNoInteractions(proposalLikeRepository);
    }

    @Test
    @DisplayName("격리 범위가 다른 거절 제안의 공감은 409가 아닌 PROPOSAL_404로 거부한다")
    void rejectsLikeForRejectedProposalOutsideDemoSession() {
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(
                likeableProposal(ProposalStatus.REJECTED, 3, "01K6DEMO00000000000000000A")));

        assertProposalError(() -> proposalService.likeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.unlikeProposal(31L, 77L, null), ErrorCode.PROPOSAL_NOT_FOUND);
    }

    @Test
    @DisplayName("거절할 제안은 행을 잠가 읽고 결제 전이거나 이 사장님이 이미 거절한 받은 제안을 그대로 반환한다")
    void returnsRejectableProposalForUpdate() {
        Proposal pending = rejectableProposal(ProposalStatus.PENDING, null, null);
        Proposal rejected = rejectableProposal(ProposalStatus.REJECTED, ProposalRejectedBy.OWNER, null);
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(pending));
        when(proposalRepository.findLockedById(32L)).thenReturn(Optional.of(rejected));

        assertThat(proposalService.getRejectableProposalForUpdate(31L, 5L, null)).isSameAs(pending);
        assertThat(proposalService.getRejectableProposalForUpdate(32L, 5L, null)).isSameAs(rejected);
        verify(proposalRepository).findLockedById(31L);
        assertThat(pending.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }

    @ParameterizedTest(name = "{0} / 거절 주체 {1}")
    @CsvSource(value = { "AWAITING_START,NULL", "ACCEPTED,NULL", "CANCELLED,NULL", "REJECTED,STUDENT",
            "REJECTED,NULL" }, nullValues = "NULL")
    @DisplayName("결제됐거나 취소된 제안, 학생이 거절했거나 거절 주체가 없는 거절 제안의 거절은 PROPOSAL_409_REJECT로 거부한다")
    void rejectsRejectOfUnavailableStatus(ProposalStatus status, ProposalRejectedBy rejectedBy) {
        when(proposalRepository.findLockedById(31L))
                .thenReturn(Optional.of(rejectableProposal(status, rejectedBy, null)));

        assertProposalError(() -> proposalService.getRejectableProposalForUpdate(31L, 5L, null),
                ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("없는 제안의 거절은 PROPOSAL_404, 다른 사장님이 받은 제안은 상태와 무관하게 PROPOSAL_403_REJECT로 거부한다")
    void rejectsRejectOfMissingOrOthersProposal() {
        when(proposalRepository.findLockedById(99L)).thenReturn(Optional.empty());
        assertProposalError(() -> proposalService.getRejectableProposalForUpdate(99L, 5L, null),
                ErrorCode.PROPOSAL_NOT_FOUND);

        for (ProposalStatus status : ProposalStatus.values()) {
            when(proposalRepository.findLockedById(31L))
                    .thenReturn(Optional.of(rejectableProposal(status, null, null)));
            assertProposalError(() -> proposalService.getRejectableProposalForUpdate(31L, 6L, null),
                    ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        }
    }

    @ParameterizedTest(name = "사장님 {0} / 제안 {1}")
    @CsvSource(value = { "NULL,01K6DEMO00000000000000000A", "01K6DEMO00000000000000000A,NULL",
            "01K6DEMO00000000000000000A,01K6DEMO00000000000000000B" }, nullValues = "NULL")
    @DisplayName("격리 범위가 다른 제안의 거절은 받은 사장님 검증보다 먼저 PROPOSAL_404로 거부한다")
    void rejectsRejectOutsideDemoSession(String ownerSession, String proposalSession) {
        // 다른 사장님이 받은 제안이어도 403이 아닌 404다
        when(proposalRepository.findLockedById(31L)).thenReturn(Optional.of(
                rejectableProposal(ProposalStatus.PENDING, null, proposalSession)));

        assertProposalError(() -> proposalService.getRejectableProposalForUpdate(31L, 5L, ownerSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertProposalError(() -> proposalService.getRejectableProposalForUpdate(31L, 6L, ownerSession),
                ErrorCode.PROPOSAL_NOT_FOUND);
    }

    private Proposal rejectableProposal(ProposalStatus status, ProposalRejectedBy rejectedBy, String demoSessionId) {
        return Proposal.builder().id(31L).studentProfileId(77L).ownerProfileId(5L).status(status)
                .rejectedBy(rejectedBy).likeCount(2).demoSessionId(demoSessionId).build();
    }

    private Proposal likeableProposal(ProposalStatus status, int likeCount, String demoSessionId) {
        return Proposal.builder().id(31L).studentProfileId(77L).ownerProfileId(5L).status(status)
                .likeCount(likeCount).demoSessionId(demoSessionId).build();
    }

    private void assertProposalError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("의뢰서를 거절할 제안은 행을 잠가 읽고 다른 학생의 제안은 403, 수락 대기가 아닌 제안은 409, 없는 제안은 데이터 오류로 거부한다")
    void locksDeclinableProposal() {
        Proposal awaiting = Proposal.builder().id(5L).studentProfileId(31L)
                .status(ProposalStatus.AWAITING_START).build();
        when(proposalRepository.findLockedById(5L)).thenReturn(Optional.of(awaiting));
        when(proposalRepository.findLockedById(99L)).thenReturn(Optional.empty());

        assertThat(proposalService.getDeclinableProposalForUpdate(5L, 31L)).isSameAs(awaiting);
        assertProposalError(() -> proposalService.getDeclinableProposalForUpdate(5L, 32L),
                ErrorCode.JOB_DECLINE_FORBIDDEN);
        assertProposalError(() -> proposalService.getDeclinableProposalForUpdate(99L, 31L),
                ErrorCode.INTERNAL_SERVER_ERROR);
        for (ProposalStatus status : List.of(ProposalStatus.PENDING, ProposalStatus.ACCEPTED,
                ProposalStatus.REJECTED, ProposalStatus.CANCELLED)) {
            when(proposalRepository.findLockedById(6L)).thenReturn(Optional.of(
                    Proposal.builder().id(6L).studentProfileId(31L).status(status).build()));
            assertProposalError(() -> proposalService.getDeclinableProposalForUpdate(6L, 31L),
                    ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        }
        assertThat(awaiting.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
    }
}
