package com.gakkum.backend.application.proposal.facade;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
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
     * 사장님이 받은 제안 상세. 사장님 프로필이 없으면 403, 없는 제안이나 다른 사장님이 받은 제안은 404다.
     * 수신 사장님으로 제한해 제안을 찾은 뒤에만 학생 정보와 특기를 조회한다.
     */
    @Transactional(readOnly = true)
    public ReceivedProposalResult getReceivedProposal(String username, Long proposalId) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        ReceivedProposalData data = proposalService.getReceivedProposal(proposalId, owner.getId());

        Student student = studentService.getStudentProfile(data.getProposal().getStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
        return ReceivedProposalResult.of(data.getProposal(), student, studentUser,
                groupSpecialties(data.getSpecialtyIds(), specialtiesById));
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
