package com.gakkum.backend.domain.proposal.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final ProposalSpecialtyRepository proposalSpecialtyRepository;

    /** 참조 ID와 사진 검증을 마친 제안과 소분류를 한 트랜잭션으로 저장한다. */
    @Transactional
    public Proposal createProposal(CreateProposalCommand command, Long studentProfileId) {
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
                command.getReferenceImageUrls()));

        List<ProposalSpecialty> specialties = command.getSpecialtyIds().stream()
                .map(specialtyId -> ProposalSpecialty.create(proposal.getId(), specialtyId))
                .toList();
        proposalSpecialtyRepository.saveAll(specialties);

        return proposal;
    }

    /** 인증 사용자 누구나 볼 수 있는 제안 상세. 없는 제안은 404로 거부한다. */
    @Transactional(readOnly = true)
    public ProposalDetailData getProposalDetail(Long proposalId) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        List<Long> specialtyIds = proposalSpecialtyRepository.findByProposalId(proposal.getId()).stream()
                .map(ProposalSpecialty::getSpecialtyId)
                .toList();
        return ProposalDetailData.of(proposal, specialtyIds);
    }

    /** 탐색 목록용으로 커서 경계 뒤의 제안을 정렬 순서대로 limit개까지 읽고 제안별 소분류 ID를 한 번에 붙인다. */
    @Transactional(readOnly = true)
    public List<ExploreProposalData> getExploreProposals(GetExploreProposalsCommand command) {
        return withSpecialtyIds(findExploreProposals(command));
    }

    /** 학생이 보낸 모든 제안을 최신순으로 읽고 제안별 소분류 ID를 한 번에 붙인다. */
    @Transactional(readOnly = true)
    public List<ExploreProposalData> getMyProposals(GetMyProposalsCommand command) {
        return withSpecialtyIds(proposalRepository
                .findByStudentProfileIdOrderByCreatedAtDescIdDesc(command.getStudentProfileId()));
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
        Long categoryId = command.getSpecialtyCategoryId();
        Integer likeCount = command.getLikeCountBound();
        LocalDateTime createdAt = command.getCreatedAtBound();
        Long idBound = command.getIdBound();
        if (categoryId != null) {
            Limit limit = Limit.of(command.getLimit());
            return switch (command.getOrder()) {
                case LATEST -> proposalRepository.findExploreLatestInCategory(categoryId, createdAt, idBound, limit);
                case OLDEST -> proposalRepository.findExploreOldestInCategory(categoryId, createdAt, idBound, limit);
                case LIKES -> proposalRepository.findExploreByLikesInCategory(
                        categoryId, likeCount, createdAt, idBound, limit);
            };
        }
        return switch (command.getOrder()) {
            case LATEST -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByCreatedAtAndIdLessThanOrderByIdDesc(createdAt, idBound, limit),
                    limit -> proposalRepository.findByCreatedAtLessThanOrderByCreatedAtDescIdDesc(createdAt, limit));
            case OLDEST -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByCreatedAtAndIdGreaterThanOrderByIdAsc(createdAt, idBound, limit),
                    limit -> proposalRepository.findByCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(createdAt, limit));
            case LIKES -> readInSegments(command.getLimit(),
                    limit -> proposalRepository.findByLikeCountAndCreatedAtAndIdLessThanOrderByIdDesc(
                            likeCount, createdAt, idBound, limit),
                    limit -> proposalRepository.findByLikeCountAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                            likeCount, createdAt, limit),
                    limit -> proposalRepository.findByLikeCountLessThanOrderByLikeCountDescCreatedAtDescIdDesc(
                            likeCount, limit));
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
