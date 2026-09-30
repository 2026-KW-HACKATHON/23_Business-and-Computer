package com.gakkum.backend.application.proposal.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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

class ProposalFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String IMAGE_URL_1 = "https://bucket/images/proposal/" + USER_ID + "/a.png";
    private static final String IMAGE_URL_2 = "https://bucket/images/proposal/" + USER_ID + "/b.png";
    private static final String KEY_1 = "images/proposal/" + USER_ID + "/a.png";
    private static final String KEY_2 = "images/proposal/" + USER_ID + "/b.png";

    private final UserService userService = mock(UserService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final ProposalFacade proposalFacade = new ProposalFacade(
            userService, studentService, ownerService, specialtyService, mediaService, proposalService);

    @Test
    @DisplayName("학생이 존재하는 사장님과 소분류, 업로드된 본인 사진으로 제안을 보내면 저장하고 제안 ID를 반환한다")
    void createsProposal() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        givenIssuedImage(IMAGE_URL_2, KEY_2, true);
        CreateProposalCommand command = command(List.of(IMAGE_URL_1, IMAGE_URL_2));
        when(proposalService.createProposal(command, 7L)).thenReturn(Proposal.builder().id(31L).build());

        ProposalCreateResult result = proposalFacade.createProposal(command);

        assertThat(result.getProposalId()).isEqualTo(31L);
        verify(ownerService).validateOwnerProfileExists(5L);
        verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        verify(mediaService).isImageUploaded(KEY_1);
        verify(mediaService).isImageUploaded(KEY_2);
    }

    @Test
    @DisplayName("사진이 없으면 사진 저장소를 확인하지 않고 제안을 저장한다")
    void createsProposalWithoutImages() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        CreateProposalCommand command = command(List.of());
        when(proposalService.createProposal(command, 7L)).thenReturn(Proposal.builder().id(32L).build());

        assertThat(proposalFacade.createProposal(command).getProposalId()).isEqualTo(32L);
        verifyNoInteractions(mediaService);
    }

    @Test
    @DisplayName("학생이 아닌 사용자는 PROPOSAL_403_STUDENT로 거부하고 이후 검증과 저장을 하지 않는다")
    void rejectsNonStudent() {
        givenUser(UserRole.OWNER);

        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.PROPOSAL_STUDENT_REQUIRED);
        verifyNoInteractions(studentService, ownerService, specialtyService, mediaService, proposalService);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생 역할 사용자는 PROPOSAL_403_STUDENT로 거부한다")
    void rejectsStudentWithoutProfile() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.PROPOSAL_STUDENT_REQUIRED);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("없는 사장님에게 보내면 OWNER_404로 거부하고 저장하지 않는다")
    void rejectsMissingOwner() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        doThrow(new BusinessException(ErrorCode.OWNER_NOT_FOUND)).when(ownerService).validateOwnerProfileExists(5L);

        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.OWNER_NOT_FOUND);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("없거나 중복된 소분류는 소분류 검증 오류를 그대로 전달하고 저장하지 않는다")
    void rejectsInvalidSpecialties() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        doThrow(new BusinessException(ErrorCode.DUPLICATE_SPECIALTY))
                .doThrow(new BusinessException(ErrorCode.SPECIALTY_NOT_FOUND))
                .when(specialtyService).validateSpecialtyIds(List.of(1L, 2L));

        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.DUPLICATE_SPECIALTY);
        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.SPECIALTY_NOT_FOUND);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("본인 제안용으로 발급되지 않은 사진 URL이 하나라도 있으면 업로드 확인 전에 PROPOSAL_400_IMAGE_URL로 거부한다")
    void rejectsForeignImageUrl() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        when(mediaService.findImageKey(USER_ID, ImagePurpose.PROPOSAL, "https://evil.example.com/a.png"))
                .thenReturn(Optional.empty());

        assertError(() -> proposalFacade.createProposal(command(List.of(IMAGE_URL_1, "https://evil.example.com/a.png"))),
                ErrorCode.PROPOSAL_IMAGE_URL_INVALID);
        verify(mediaService, never()).isImageUploaded(anyString());
        verify(proposalService, never()).createProposal(any(), anyLong());
    }

    @Test
    @DisplayName("업로드되지 않은 사진이 있으면 PROPOSAL_409_IMAGE_NOT_UPLOADED로 거부하고 저장하지 않는다")
    void rejectsNotUploadedImage() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        givenIssuedImage(IMAGE_URL_2, KEY_2, false);

        assertError(() -> proposalFacade.createProposal(command(List.of(IMAGE_URL_1, IMAGE_URL_2))),
                ErrorCode.PROPOSAL_IMAGE_NOT_UPLOADED);
        verify(proposalService, never()).createProposal(any(), anyLong());
    }

    @Test
    @DisplayName("역할, 대상 사장님, 소분류, 사진 순서로 검증한 뒤 저장한다")
    void validatesBeforeSaving() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        CreateProposalCommand command = command(List.of(IMAGE_URL_1));
        when(proposalService.createProposal(command, 7L)).thenReturn(Proposal.builder().id(31L).build());

        proposalFacade.createProposal(command);

        InOrder order = inOrder(studentService, ownerService, specialtyService, mediaService, proposalService);
        order.verify(studentService).findStudentProfileByUserId(USER_ID);
        order.verify(ownerService).validateOwnerProfileExists(5L);
        order.verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        order.verify(mediaService).isImageUploaded(KEY_1);
        order.verify(proposalService).createProposal(command, 7L);
    }

    private void givenUser(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(role).build());
    }

    private void givenStudentProfile() {
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(USER_ID).build()));
    }

    private void givenIssuedImage(String imageUrl, String key, boolean uploaded) {
        when(mediaService.findImageKey(USER_ID, ImagePurpose.PROPOSAL, imageUrl)).thenReturn(Optional.of(key));
        when(mediaService.isImageUploaded(key)).thenReturn(uploaded);
    }

    private CreateProposalCommand command(List<String> imageUrls) {
        return CreateProposalCommand.of(USERNAME, 5L, List.of(1L, 2L), "메뉴판 개선 제안", "메뉴를 알아보기 어렵습니다.",
                "사진 메뉴판으로 바꿉니다.", "촬영 후 편집합니다.", 50000L, 3, 7, imageUrls);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
