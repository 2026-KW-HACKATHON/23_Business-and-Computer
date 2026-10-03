package com.gakkum.backend.application.proposal.facade;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProposalFacade {

    private final UserService userService;
    private final StudentService studentService;
    private final OwnerService ownerService;
    private final SpecialtyService specialtyService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final MediaService mediaService;
    private final ProposalService proposalService;
    private final ReviewService reviewService;
    private final JobService jobService;

    /**
     * 학생의 제안 전송. 역할·대상 사장님·소분류·사진을 검증한 뒤 저장한다.
     * 사진 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않는다.
     */
    public ProposalCreateResult createProposal(CreateProposalCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Student student = getProposingStudent(user);
        ownerService.validateOwnerProfileExists(command.getOwnerProfileId());
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());
        validateUploadedImages(command.getReferenceImageUrls(), user.getId());

        Proposal proposal = proposalService.createProposal(command, student.getId());
        return ProposalCreateResult.from(proposal);
    }

    /**
     * 제안 상세. 활성 사용자라면 역할과 무관하게 모든 제안을 볼 수 있고 없는 제안은 404다.
     * 제안을 찾은 뒤에만 매장(현재 이름)·학생 정보·학생 통계(평균 별점·완료 의뢰 수)·특기를 조회한다.
     */
    @Transactional(readOnly = true)
    public ProposalDetailResult getProposalDetail(String username, Long proposalId) {
        userService.getActiveUser(username);
        ProposalDetailData data = proposalService.getProposalDetail(proposalId);

        String storeName = ownerService.getOwnerProfileById(data.getProposal().getOwnerProfileId()).getStoreName();
        Student student = studentService.getStudentProfile(data.getProposal().getStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
        BigDecimal averageRating = reviewService.getAverageRating(student.getId());
        long completedJobCount = jobService.countClosedJobs(student.getId());
        return ProposalDetailResult.of(data.getProposal(), storeName, student, studentUser,
                averageRating, completedJobCount,
                groupSpecialties(data.getSpecialtyIds(), specialtiesById));
    }

    /**
     * 내가 보낸 제안 목록. 활성 학생만 조회할 수 있다.
     * 제안이 있으면 사장님 프로필과 소분류를 중복 없이 모아 한 번씩만 조회한다.
     */
    @Transactional(readOnly = true)
    public MyProposalListResult getMyProposals(String username) {
        User user = userService.getActiveUser(username);
        Student student = getListingStudent(user);
        List<ExploreProposalData> proposals =
                proposalService.getMyProposals(GetMyProposalsCommand.of(student.getId()));
        if (proposals.isEmpty()) {
            return MyProposalListResult.of(List.of());
        }

        Set<Long> ownerProfileIds = proposals.stream()
                .map(data -> data.getProposal().getOwnerProfileId())
                .collect(Collectors.toSet());
        Set<Long> specialtyIds = proposals.stream()
                .flatMap(data -> data.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, Owner> ownersById = ownerService.getOwnerProfilesByIds(ownerProfileIds);
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return MyProposalListResult.of(proposals.stream()
                .map(data -> MyProposalResult.of(
                        data.getProposal(),
                        ownersById.get(data.getProposal().getOwnerProfileId()),
                        groupSpecialties(data.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    private Student getListingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED));
    }

    /** 학생 프로필이 없는 사용자(사장님 포함)는 제안을 보낼 수 없다. */
    private Student getProposingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_STUDENT_REQUIRED));
    }

    /** 모든 URL이 이 학생의 제안용으로 발급한 경로인지 먼저 확인한 뒤 실제 업로드 여부를 확인한다. */
    private void validateUploadedImages(List<String> imageUrls, String userId) {
        List<String> keys = imageUrls.stream()
                .map(imageUrl -> mediaService.findImageKey(userId, ImagePurpose.PROPOSAL, imageUrl)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_IMAGE_URL_INVALID)))
                .toList();
        for (String key : keys) {
            if (!mediaService.isImageUploaded(key)) {
                throw new BusinessException(ErrorCode.PROPOSAL_IMAGE_NOT_UPLOADED);
            }
        }
    }

    // 의뢰 API와 같은 형식으로 대분류 ID 순, 대분류 안에서는 소분류 ID 순으로 묶는다
    private List<SpecialtyCategoryResult> groupSpecialties(
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
}
