package com.gakkum.backend.domain.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class ProposalServiceTest {

    private final ProposalRepository proposalRepository = mock(ProposalRepository.class);
    private final ProposalSpecialtyRepository proposalSpecialtyRepository = mock(ProposalSpecialtyRepository.class);
    private final ProposalService proposalService = new ProposalService(proposalRepository, proposalSpecialtyRepository);

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

        Proposal saved = proposalService.createProposal(command, 7L);

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
        when(proposalRepository.findExploreByLikesInCategory(3L, 5, bound, 40L, Limit.of(21)))
                .thenReturn(List.of(first, second));
        when(proposalSpecialtyRepository.findByProposalIdIn(List.of(32L, 31L))).thenReturn(List.of(
                ProposalSpecialty.create(31L, 2L),
                ProposalSpecialty.create(32L, 7L),
                ProposalSpecialty.create(32L, 1L)));

        List<ExploreProposalData> data = proposalService.getExploreProposals(GetExploreProposalsCommand.of(
                3L, ProposalExploreOrder.LIKES, 5, bound, 40L, 21));

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
                    GetExploreProposalsCommand.of(4L, order, 5, bound, 9L, 3))).isEmpty();
        }

        verify(proposalRepository).findExploreLatestInCategory(4L, bound, 9L, Limit.of(3));
        verify(proposalRepository).findExploreOldestInCategory(4L, bound, 9L, Limit.of(3));
        verify(proposalRepository).findExploreByLikesInCategory(4L, 5, bound, 9L, Limit.of(3));
        verify(proposalSpecialtyRepository, never()).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("대분류 없는 최신순은 경계 시각과 같은 행부터 읽고 남은 개수만큼 경계 이전 행을 이어 붙인다")
    void readsLatestSegmentsInOrder() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal sameTime = Proposal.builder().id(8L).build();
        Proposal earlier = Proposal.builder().id(20L).build();
        when(proposalRepository.findByCreatedAtAndIdLessThanOrderByIdDesc(bound, 9L, Limit.of(3)))
                .thenReturn(List.of(sameTime));
        when(proposalRepository.findByCreatedAtLessThanOrderByCreatedAtDescIdDesc(bound, Limit.of(2)))
                .thenReturn(List.of(earlier));

        List<ExploreProposalData> data = proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, ProposalExploreOrder.LATEST, null, bound, 9L, 3));

        assertThat(data).extracting(ExploreProposalData::getProposal).containsExactly(sameTime, earlier);
    }

    @Test
    @DisplayName("대분류 없는 오래된순은 경계 시각과 같은 행으로 개수가 차면 경계 이후 구간을 조회하지 않는다")
    void skipsLaterSegmentWhenFilled() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        when(proposalRepository.findByCreatedAtAndIdGreaterThanOrderByIdAsc(bound, 9L, Limit.of(2)))
                .thenReturn(List.of(Proposal.builder().id(10L).build(), Proposal.builder().id(11L).build()));

        assertThat(proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, ProposalExploreOrder.OLDEST, null, bound, 9L, 2))).hasSize(2);
        verify(proposalRepository, never()).findByCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(any(), any());
    }

    @Test
    @DisplayName("대분류 없는 좋아요순은 같은 좋아요·같은 시각, 같은 좋아요·이전 시각, 더 적은 좋아요 순으로 이어 읽는다")
    void readsLikesSegmentsInOrder() {
        LocalDateTime bound = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal first = Proposal.builder().id(1L).build();
        Proposal second = Proposal.builder().id(2L).build();
        Proposal third = Proposal.builder().id(3L).build();
        when(proposalRepository.findByLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(5, bound, 9L, Limit.of(4)))
                .thenReturn(List.of(first));
        when(proposalRepository.findByLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(5, bound, Limit.of(3)))
                .thenReturn(List.of(second));
        when(proposalRepository.findByLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(5, Limit.of(2)))
                .thenReturn(List.of(third));

        List<ExploreProposalData> data = proposalService.getExploreProposals(
                GetExploreProposalsCommand.of(null, ProposalExploreOrder.LIKES, 5, bound, 9L, 4));

        assertThat(data).extracting(ExploreProposalData::getProposal).containsExactly(first, second, third);
    }

    @Test
    @DisplayName("내가 보낸 제안은 본인 학생 ID로 조회하고 소분류 연결을 한 번에 붙인다")
    void readsMyProposalsWithBatchedSpecialties() {
        when(proposalRepository.findByStudentProfileIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(
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
        when(proposalRepository.findByStudentProfileIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());

        assertThat(proposalService.getMyProposals(GetMyProposalsCommand.of(7L))).isEmpty();
        verify(proposalSpecialtyRepository, never()).findByProposalIdIn(any());
    }

    @Test
    @DisplayName("받은 제안은 사장님 프로필 ID로 조회하고 소분류 연결을 한 번에 붙인다")
    void readsReceivedProposalsWithBatchedSpecialties() {
        when(proposalRepository.findByOwnerProfileIdOrderByCreatedAtDescIdDesc(5L)).thenReturn(List.of(
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
        when(proposalRepository.findByOwnerProfileIdOrderByCreatedAtDescIdDesc(5L)).thenReturn(List.of());

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

    private void assertProposalError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
