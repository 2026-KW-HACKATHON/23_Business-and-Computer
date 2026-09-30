package com.gakkum.backend.application.proposal.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
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
}
