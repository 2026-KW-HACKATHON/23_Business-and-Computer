package com.gakkum.backend.application.proposal.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
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

class ProposalFacadeTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String IMAGE_URL_1 = "https://bucket/images/proposal/" + USER_ID + "/a.png";
    private static final String IMAGE_URL_2 = "https://bucket/images/proposal/" + USER_ID + "/b.png";
    private static final String KEY_1 = "images/proposal/" + USER_ID + "/a.png";
    private static final String KEY_2 = "images/proposal/" + USER_ID + "/b.png";

    private final UserService userService = mock(UserService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ProposalService proposalService = mock(ProposalService.class);
    private final ProposalFacade proposalFacade = new ProposalFacade(
            userService, studentService, ownerService, specialtyService, specialtyCategoryService, mediaService,
            proposalService);

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

    @Test
    @DisplayName("제안 상세를 학생 정보와 대분류별로 묶은 특기와 함께 반환한다")
    void returnsProposalDetail() {
        givenUser(UserRole.OWNER);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 10, 0);
        Proposal proposal = receivedProposal(List.of(IMAGE_URL_1, IMAGE_URL_2), createdAt);
        when(proposalService.getProposalDetail(31L))
                .thenReturn(ProposalDetailData.of(proposal, List.of(12L, 3L, 11L)));
        givenProposingStudent();
        givenStore();
        when(specialtyCategoryService.getSpecialtyDetails(List.of(12L, 3L, 11L))).thenReturn(Map.of(
                12L, SpecialtyDetail.of(12L, "영상 편집", 2L, "영상"),
                3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인"),
                11L, SpecialtyDetail.of(11L, "숏폼 촬영", 2L, "영상")));

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(result.getStoreName()).isEqualTo("가게 이름");
        verify(ownerService).getOwnerProfileById(5L);
        assertThat(result.getLikeCount()).isEqualTo(4);
        assertThat(result.getCustomerProblem()).isEqualTo("메뉴를 알아보기 어렵습니다.");
        assertThat(result.getProposedSolution()).isEqualTo("사진 메뉴판으로 바꿉니다.");
        assertThat(result.getWorkPlan()).isEqualTo("촬영 후 편집합니다.");
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        assertThat(result.getFinalDays()).isEqualTo(7);
        assertThat(result.getReferenceImageUrls()).containsExactly(IMAGE_URL_1, IMAGE_URL_2);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getStudent().getStudentProfileId()).isEqualTo(7L);
        assertThat(result.getStudent().getName()).isEqualTo("김학생");
        assertThat(result.getStudent().getMajor()).isEqualTo("시각디자인학부");
        assertThat(result.getStudent().getStudentNumber()).isEqualTo("20260001");
        assertThat(result.getSpecialtyCategories())
                .extracting(SpecialtyCategoryResult::getId, SpecialtyCategoryResult::getName)
                .containsExactly(tuple(1L, "디자인"), tuple(2L, "영상"));
        assertThat(result.getSpecialtyCategories().get(0).getSpecialties())
                .extracting(SpecialtyResult::getId, SpecialtyResult::getName)
                .containsExactly(tuple(3L, "로고 디자인"));
        assertThat(result.getSpecialtyCategories().get(1).getSpecialties())
                .extracting(SpecialtyResult::getId, SpecialtyResult::getName)
                .containsExactly(tuple(11L, "숏폼 촬영"), tuple(12L, "영상 편집"));
    }

    @Test
    @DisplayName("사진이 없는 제안은 빈 사진 목록으로 반환한다")
    void returnsProposalDetailWithoutImages() {
        givenUser(UserRole.OWNER);
        when(proposalService.getProposalDetail(31L)).thenReturn(ProposalDetailData.of(
                receivedProposal(List.of(), LocalDateTime.of(2026, 9, 30, 10, 0)), List.of(3L)));
        givenProposingStudent();
        givenStore();
        when(specialtyCategoryService.getSpecialtyDetails(List.of(3L)))
                .thenReturn(Map.of(3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인")));

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getReferenceImageUrls()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "STUDENT", "OWNER" })
    @DisplayName("학생과 수신자가 아닌 사장님도 사장님 프로필 확인 없이 제안 상세를 조회한다")
    void returnsProposalDetailToAnyAuthenticatedUser(UserRole role) {
        givenUser(role);
        when(proposalService.getProposalDetail(31L)).thenReturn(ProposalDetailData.of(
                receivedProposal(List.of(), LocalDateTime.of(2026, 9, 30, 10, 0)), List.of(3L)));
        givenProposingStudent();
        givenStore();
        when(specialtyCategoryService.getSpecialtyDetails(List.of(3L)))
                .thenReturn(Map.of(3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인")));

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStudent().getStudentNumber()).isEqualTo("20260001");
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        verify(ownerService, never()).getOwnerProfile(anyString());
    }

    @Test
    @DisplayName("비활성 사용자는 UNAUTHORIZED로 거부하고 제안을 조회하지 않는다")
    void rejectsInactiveUserForProposalDetail() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> proposalFacade.getProposalDetail(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(proposalService, ownerService, studentService, specialtyCategoryService);
    }

    @Test
    @DisplayName("없는 제안은 PROPOSAL_404로 거부하고 학생·특기를 조회하지 않는다")
    void rejectsMissingProposal() {
        givenUser(UserRole.STUDENT);
        when(proposalService.getProposalDetail(31L))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));

        assertError(() -> proposalFacade.getProposalDetail(USERNAME, 31L), ErrorCode.PROPOSAL_NOT_FOUND);
        verifyNoInteractions(ownerService, studentService, specialtyCategoryService);
        verify(userService, never()).getUser(anyString());
    }

    private void givenUser(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(role).build());
    }

    private void givenStore() {
        when(ownerService.getOwnerProfileById(5L)).thenReturn(Owner.builder().id(5L).storeName("가게 이름").build());
    }

    private void givenProposingStudent() {
        when(studentService.getStudentProfile(7L)).thenReturn(Student.builder()
                .id(7L).userId(STUDENT_USER_ID).major("시각디자인학부").studentNumber("20260001").build());
        when(userService.getUser(STUDENT_USER_ID))
                .thenReturn(User.builder().id(STUDENT_USER_ID).name("김학생").role(UserRole.STUDENT).build());
    }

    private Proposal receivedProposal(List<String> imageUrls, LocalDateTime createdAt) {
        return Proposal.builder()
                .id(31L)
                .studentProfileId(7L)
                .ownerProfileId(5L)
                .title("메뉴판 개선 제안")
                .customerProblem("메뉴를 알아보기 어렵습니다.")
                .proposedSolution("사진 메뉴판으로 바꿉니다.")
                .workPlan("촬영 후 편집합니다.")
                .proposedFee(50000L)
                .draftDays(3)
                .finalDays(7)
                .referenceImageUrls(imageUrls)
                .likeCount(4)
                .createdAt(createdAt)
                .build();
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
