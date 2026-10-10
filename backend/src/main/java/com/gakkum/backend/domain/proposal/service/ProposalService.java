package com.gakkum.backend.domain.proposal.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProposalService {

    // 탐색(GET /explore)에서 빠지는 제안 상태. 학생 프로필 열람 권한도 같은 기준으로 본다
    private static final List<ProposalStatus> EXPLORE_HIDDEN_STATUSES =
            List.of(ProposalStatus.CANCELLED, ProposalStatus.REJECTED);

    private final ProposalRepository proposalRepository;
    private final ProposalSpecialtyRepository proposalSpecialtyRepository;
    private final ProposalLikeRepository proposalLikeRepository;

    /** 참조 ID와 사진 검증을 마친 제안과 소분류를 한 트랜잭션으로 저장한다. demoSessionId는 제안한 학생의 격리 범위이고 실제 학생은 null이다. */
    @Transactional
    public Proposal createProposal(CreateProposalCommand command, Long studentProfileId, String demoSessionId) {
        Proposal proposal = proposalRepository.save(Proposal.create(
                studentProfileId,
                command.getOwnerProfileId(),
                command.getTitle(),
                command.getCustomerProblem(),
                command.getProposedSolution(),
                command.getWorkPlan(),
                command.getProposedFee(),
                command.getDraftDays(),
                command.getFinalDays(),
                command.getReferenceImageUrls(),
                demoSessionId));

        List<ProposalSpecialty> specialties = command.getSpecialtyIds().stream()
                .map(specialtyId -> ProposalSpecialty.create(proposal.getId(), specialtyId))
                .toList();
        proposalSpecialtyRepository.saveAll(specialties);

        return proposal;
    }

    /** 제안 상세. 없는 제안은 404로 거부한다. 격리 범위와 취소된 제안의 조회 권한은 호출하는 쪽이 확인한다. */
    @Transactional(readOnly = true)
    public ProposalDetailData getProposalDetail(Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        List<Long> specialtyIds = proposalSpecialtyRepository.findByProposalId(proposal.getId()).stream()
                .map(ProposalSpecialty::getSpecialtyId)
                .toList();
        return ProposalDetailData.of(proposal, specialtyIds);
    }

    /**
     * 사장님이 결제할 수 있는 본인 제안을 잠가 반환한다. 같은 제안의 결제 준비·승인을 순서대로 처리한다.
     * 없는 제안은 404, 다른 사장님이 받은 제안은 403, 결제 전(PENDING)이 아닌 제안은 409로 거부한다.
     * @param proposalId
     * @param ownerProfileId
     * @return 결제 전(PENDING) 제안
     */
    @Transactional
    public Proposal getPayableProposalForUpdate(Long proposalId, Long ownerProfileId) {
        Proposal proposal = getProposalForUpdate(proposalId);
        if (!proposal.getOwnerProfileId().equals(ownerProfileId)) {
            throw new BusinessException(ErrorCode.PROPOSAL_PAYMENT_FORBIDDEN);
        }
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new BusinessException(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
        }
        return proposal;
    }

    /**
     * 학생이 작업을 시작할 수 있는 본인 제안을 잠가 반환한다. 같은 제안의 결제 승인·작업 시작을 순서대로 처리한다.
     * 없는 제안은 404, 다른 학생의 제안은 403, 결제되지 않았거나 거절된 제안은 409로 거부한다.
     * @param proposalId
     * @param studentProfileId
     * @return 수락 대기(AWAITING_START) 또는 이미 수락된(ACCEPTED) 제안
     */
    @Transactional
    public Proposal getStartableProposalForUpdate(Long proposalId, Long studentProfileId) {
        Proposal proposal = getProposalForUpdate(proposalId);
        if (!proposal.getStudentProfileId().equals(studentProfileId)) {
            throw new BusinessException(ErrorCode.JOB_START_FORBIDDEN);
        }
        if (proposal.getStatus() != ProposalStatus.AWAITING_START
                && proposal.getStatus() != ProposalStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE);
        }
        return proposal;
    }

    /**
     * 학생이 거절하려는 의뢰서의 본인 제안을 잠가 반환한다. 같은 제안의 작업 시작·거절을 순서대로 처리한다.
     * 다른 학생의 제안은 403, 수락 대기가 아닌(이미 시작·거절된) 제안은 409로 거부한다.
     * 의뢰가 가리키는 제안이 없으면 데이터 오류(500)다.
     * @param proposalId 의뢰를 만든 제안 ID
     * @param studentProfileId
     * @return 수락 대기(AWAITING_START) 제안
     */
    @Transactional
    public Proposal getDeclinableProposalForUpdate(Long proposalId, Long studentProfileId) {
        Proposal proposal = proposalRepository.findLockedById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        if (!proposal.getStudentProfileId().equals(studentProfileId)) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_FORBIDDEN);
        }
        if (proposal.getStatus() != ProposalStatus.AWAITING_START) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        }
        return proposal;
    }

    /**
     * 학생이 취소하려는 본인 제안을 잠가 반환한다. 같은 제안의 취소·결제 준비·승인·공감 변경을 순서대로 처리한다.
     * 없거나 격리 범위가 다른 제안은 404, 다른 학생의 제안은 403, 결제됐거나 거절된 제안은 409로 거부한다.
     * @param proposalId
     * @param studentProfileId
     * @param demoSessionId 취소하는 학생의 격리 범위. 실제 학생은 null
     * @return 결제 전(PENDING) 또는 이미 취소된(CANCELLED) 제안
     */
    @Transactional
    public Proposal getCancellableProposalForUpdate(Long proposalId, Long studentProfileId, String demoSessionId) {
        Proposal proposal = getProposalForUpdate(proposalId);
        if (!Objects.equals(proposal.getDemoSessionId(), demoSessionId)) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        if (!proposal.getStudentProfileId().equals(studentProfileId)) {
            throw new BusinessException(ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        }
        if (proposal.getStatus() != ProposalStatus.PENDING && proposal.getStatus() != ProposalStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);
        }
        return proposal;
    }

    /**
     * 잠근 제안을 취소하고 그 제안의 모든 공감 기록을 지운다. 소분류와 사진은 그대로 둔다.
     * 취소할 수 있는 제안인지와 결제 대기 주문 여부는 호출하는 쪽이 먼저 확인한다.
     * @param proposal 잠근 결제 전(PENDING) 제안
     */
    @Transactional
    public void cancelProposal(Proposal proposal) {
        proposal.cancel();
        proposalLikeRepository.deleteAllByProposalId(proposal.getId());
    }

    /**
     * 사장님이 거절하려는 받은 제안을 잠가 반환한다. 같은 제안의 거절·취소·결제 준비·승인·공감 변경을 순서대로 처리한다.
     * 없거나 격리 범위가 다른 제안은 404, 다른 사장님이 받은 제안은 403으로 거부한다.
     * 결제됐거나 학생이 거절·취소한 제안, 거절 주체가 기록되지 않은 거절 제안은 409로 거부한다.
     * @param proposalId
     * @param ownerProfileId
     * @param demoSessionId 거절하는 사장님의 격리 범위. 실제 사장님은 null
     * @return 결제 전(PENDING) 또는 이 사장님이 이미 거절한(REJECTED) 제안
     */
    @Transactional
    public Proposal getRejectableProposalForUpdate(Long proposalId, Long ownerProfileId, String demoSessionId) {
        Proposal proposal = getProposalForUpdate(proposalId);
        if (!Objects.equals(proposal.getDemoSessionId(), demoSessionId)) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        if (!proposal.getOwnerProfileId().equals(ownerProfileId)) {
            throw new BusinessException(ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        }
        boolean rejectedByOwner = proposal.getStatus() == ProposalStatus.REJECTED
                && proposal.getRejectedBy() == ProposalRejectedBy.OWNER;
        if (proposal.getStatus() != ProposalStatus.PENDING && !rejectedByOwner) {
            throw new BusinessException(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);
        }
        return proposal;
    }

    /** 제안 행을 잠가 반환한다. 없는 제안은 404로 거부한다. */
    @Transactional
    public Proposal getProposalForUpdate(Long proposalId) {
        return proposalRepository.findLockedById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
    }

    /** 제안에 선택된 소분류 ID. 제안으로 의뢰를 만들 때 그대로 복사한다. */
    @Transactional(readOnly = true)
    public List<Long> getSpecialtyIds(Long proposalId) {
        return proposalSpecialtyRepository.findByProposalId(proposalId).stream()
                .map(ProposalSpecialty::getSpecialtyId)
                .toList();
    }

    /**
     * 탐색 목록용으로 커서 경계 뒤의 취소되지 않은 제안을 정렬 순서대로 limit개까지 읽고 제안별 소분류 ID를 한 번에 붙인다.
     * 탐색 화면용 조건이면 거절된 제안과 조회 학생이 작성한 제안도 limit을 세기 전에 뺀다.
     */
    @Transactional(readOnly = true)
    public List<ExploreProposalData> getExploreProposals(GetExploreProposalsCommand command) {
        return withSpecialtyIds(findExploreProposals(command));
    }

    /** 학생이 보낸 제안 중 취소하지 않은 제안을 최신순으로 읽고 제안별 소분류 ID를 한 번에 붙인다. */
    @Transactional(readOnly = true)
    public List<ExploreProposalData> getMyProposals(GetMyProposalsCommand command) {
        return withSpecialtyIds(proposalRepository.findByStudentProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(
                command.getStudentProfileId(), ProposalStatus.CANCELLED));
    }

    /** 학생이 모든 사장님에게 보낸 제안 수. 수락·거절·취소 여부와 무관하게 센다. */
    @Transactional(readOnly = true)
    public long countProposals(Long studentProfileId) {
        return proposalRepository.countByStudentProfileId(studentProfileId);
    }

    /**
     * 사장님이 학생의 제안을 볼 수 있는지. 학생이 이 사장님에게 보낸 제안이 있거나(상태 무관),
     * 학생의 제안 중 사장님이 탐색(GET /explore)에서 보는 제안이 있으면 참이다. 탐색과 같이 조회자의 격리 범위와
     * 같은 제안만 보고 취소·거절된 제안은 뺀다.
     */
    @Transactional(readOnly = true)
    public boolean isStudentProposalVisibleToOwner(Long ownerProfileId, Long studentProfileId, String demoSessionId) {
        return proposalRepository.existsByOwnerProfileIdAndStudentProfileId(ownerProfileId, studentProfileId)
                || proposalRepository.existsByStudentProfileIdAndDemoSessionIdAndStatusNotIn(
                        studentProfileId, demoSessionId, EXPLORE_HIDDEN_STATUSES);
    }

    /** 학생이 모든 사장님에게 보낸 제안 중 취소하지 않은 제안 수. 수락·거절 여부와 무관하게 센다. */
    @Transactional(readOnly = true)
    public long countProposalsExcludingCancelled(Long studentProfileId) {
        return proposalRepository.countByStudentProfileIdAndStatusNot(studentProfileId, ProposalStatus.CANCELLED);
    }

    /** 사장님이 받은 제안 중 취소하지 않은 제안 수. 결제·수락·거절 여부와 무관하게 센다. */
    @Transactional(readOnly = true)
    public long countReceivedProposalsExcludingCancelled(Long ownerProfileId) {
        return proposalRepository.countByOwnerProfileIdAndStatusNot(ownerProfileId, ProposalStatus.CANCELLED);
    }

    /** 사장님이 받은 제안 중 취소되지 않은 제안을 최신순으로 읽고 제안별 소분류 ID를 한 번에 붙인다. */
    @Transactional(readOnly = true)
    public List<ExploreProposalData> getReceivedProposals(GetReceivedProposalsCommand command) {
        return withSpecialtyIds(proposalRepository.findByOwnerProfileIdAndStatusNotOrderByCreatedAtDescIdDesc(
                command.getOwnerProfileId(), ProposalStatus.CANCELLED));
    }

    /**
     * 주어진 제안 중 학생 본인이 공감한 제안 조회. 전체 공감 수가 아닌 본인의 공감 기록으로만 판단한다
     * @param studentProfileId
     * @param proposalIds
     * @return 공감한 제안 ID, 없으면 빈 집합
     */
    @Transactional(readOnly = true)
    public Set<Long> getLikedProposalIds(Long studentProfileId, Collection<Long> proposalIds) {
        if (proposalIds.isEmpty()) {
            return Set.of();
        }
        return proposalLikeRepository.findByStudentProfileIdAndProposalIdIn(studentProfileId, proposalIds).stream()
                .map(ProposalLike::getProposalId)
                .collect(Collectors.toSet());
    }

    /**
     * 학생의 공감을 켠다. 제안 행을 잠가 같은 제안의 공감 변경을 순서대로 처리하고,
     * 공감 기록이 없을 때만 저장하고 공감 수를 1 올린다. 이미 공감한 제안은 그대로 둔다.
     * 본인 제안과 취소·거절되지 않은 모든 상태의 제안에 허용한다.
     * 없거나 격리 범위가 다르거나 취소된 제안은 404, 거절된 제안은 409로 거부한다.
     * @param proposalId
     * @param studentProfileId
     * @param demoSessionId 공감하는 학생의 격리 범위. 실제 학생은 null
     * @return 공감 수가 반영된 제안과 이 요청으로 공감이 새로 추가됐는지
     */
    @Transactional
    public ProposalLikeData likeProposal(Long proposalId, Long studentProfileId, String demoSessionId) {
        Proposal proposal = getLikeableProposalForUpdate(proposalId, demoSessionId);
        boolean added = proposalLikeRepository.findByProposalIdAndStudentProfileId(proposalId, studentProfileId)
                .isEmpty();
        if (added) {
            proposalLikeRepository.save(ProposalLike.create(proposalId, studentProfileId));
            proposal.increaseLikeCount();
        }
        return ProposalLikeData.of(proposal, added);
    }

    /**
     * 학생의 공감을 끈다. 제안 행을 잠가 같은 제안의 공감 변경을 순서대로 처리하고,
     * 공감 기록이 있을 때만 삭제하고 공감 수를 1 내린다. 공감하지 않은 제안은 그대로 둔다.
     * 없거나 격리 범위가 다르거나 취소된 제안은 공감 기록과 무관하게 404, 거절된 제안은 409로 거부한다.
     * @param proposalId
     * @param studentProfileId
     * @param demoSessionId 공감을 취소하는 학생의 격리 범위. 실제 학생은 null
     * @return 공감 수가 반영된 제안
     */
    @Transactional
    public Proposal unlikeProposal(Long proposalId, Long studentProfileId, String demoSessionId) {
        Proposal proposal = getLikeableProposalForUpdate(proposalId, demoSessionId);
        proposalLikeRepository.findByProposalIdAndStudentProfileId(proposalId, studentProfileId)
                .ifPresent(like -> {
                    proposalLikeRepository.delete(like);
                    proposal.decreaseLikeCount();
                });
        return proposal;
    }

    private Proposal getLikeableProposalForUpdate(Long proposalId, String demoSessionId) {
        Proposal proposal = getProposalForUpdate(proposalId);
        if (!Objects.equals(proposal.getDemoSessionId(), demoSessionId)
                || proposal.getStatus() == ProposalStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        // 거절된 제안은 거절 주체와 무관하게 기존 공감 기록과 공감 수를 그대로 남긴다
        if (proposal.getStatus() == ProposalStatus.REJECTED) {
            throw new BusinessException(ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);
        }
        return proposal;
    }

    private List<ExploreProposalData> withSpecialtyIds(List<Proposal> proposals) {
        if (proposals.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> specialtyIdsByProposalId = proposalSpecialtyRepository
                .findByProposalIdIn(proposals.stream().map(Proposal::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        ProposalSpecialty::getProposalId,
                        Collectors.mapping(ProposalSpecialty::getSpecialtyId, Collectors.toList())));
        return proposals.stream()
                .map(proposal -> ExploreProposalData.of(
                        proposal, specialtyIdsByProposalId.getOrDefault(proposal.getId(), List.of())))
                .toList();
    }

    private List<Proposal> findExploreProposals(GetExploreProposalsCommand command) {
        String demoSessionId = command.getDemoSessionId();
        Long categoryId = command.getSpecialtyCategoryId();
        Integer likeCount = command.getLikeCountBound();
        LocalDateTime createdAt = command.getCreatedAtBound();
        Long idBound = command.getIdBound();
        List<ProposalStatus> excluded = command.isRejectedExcluded()
                ? EXPLORE_HIDDEN_STATUSES
                : List.of(ProposalStatus.CANCELLED);
        Long excludedStudent = command.getExcludedStudentProfileId();
        if (categoryId != null) {
            Limit limit = Limit.of(command.getLimit());
            return switch (command.getOrder()) {
                case LATEST -> proposalRepository.findExploreLatestInCategory(
                        demoSessionId, excluded, excludedStudent, categoryId, createdAt, idBound, limit);
                case OLDEST -> proposalRepository.findExploreOldestInCategory(
                        demoSessionId, excluded, excludedStudent, categoryId, createdAt, idBound, limit);
                case LIKES -> proposalRepository.findExploreByLikesInCategory(
                        demoSessionId, excluded, excludedStudent, categoryId, likeCount, createdAt, idBound, limit);
            };
        }
        return switch (command.getOrder()) {
            case LATEST -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                            demoSessionId, excluded, excludedStudent, createdAt, idBound, limit),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                            demoSessionId, excluded, excludedStudent, createdAt, limit));
            case OLDEST -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                            demoSessionId, excluded, excludedStudent, createdAt, idBound, limit),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                            demoSessionId, excluded, excludedStudent, createdAt, limit));
            case LIKES -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                            demoSessionId, excluded, excludedStudent, likeCount, createdAt, idBound, limit),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                            demoSessionId, excluded, excludedStudent, likeCount, createdAt, limit),
                    limit -> proposalRepository.findByDemoSessionIdAndStatusNotInAndStudentProfileIdNotAndLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                            demoSessionId, excluded, excludedStudent, likeCount, limit));
        };
    }

    /** 커서 경계 뒤를 정렬 순서상 앞 구간부터 읽어 limit개를 채운다. 채워지면 남은 구간은 조회하지 않는다. */
    @SafeVarargs
    private static List<Proposal> readInSegments(int limit, Function<Limit, List<Proposal>>... segments) {
        List<Proposal> proposals = new ArrayList<>();
        for (Function<Limit, List<Proposal>> segment : segments) {
            if (proposals.size() >= limit) {
                break;
            }
            proposals.addAll(segment.apply(Limit.of(limit - proposals.size())));
        }
        return proposals;
    }
}
