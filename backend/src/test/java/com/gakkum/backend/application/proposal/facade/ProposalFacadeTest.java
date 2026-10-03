package com.gakkum.backend.application.proposal.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalListResult;
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
    private static final String OTHER_STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5E";
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
    private final ReviewService reviewService = mock(ReviewService.class);
    private final JobService jobService = mock(JobService.class);
    private final ProposalFacade proposalFacade = new ProposalFacade(
            userService, studentService, ownerService, specialtyService, specialtyCategoryService, mediaService,
            proposalService, reviewService, jobService);

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
        assertThat(result.getStudent().getAverageRating()).isEqualByComparingTo("4.3");
        assertThat(result.getStudent().getCompletedJobCount()).isEqualTo(5L);
        verify(reviewService).getAverageRating(7L);
        verify(jobService).countClosedJobs(7L);
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
        when(reviewService.getAverageRating(7L)).thenReturn(new java.math.BigDecimal("4.3"));
        when(jobService.countClosedJobs(7L)).thenReturn(5L);
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

    @Test
    @DisplayName("내가 보낸 제안을 매장·분류를 일괄 조회해 카드로 구성하고 같은 매장·소분류는 한 번만 조회한다")
    void returnsMyProposals() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        when(proposalService.getMyProposals(any(GetMyProposalsCommand.class))).thenReturn(List.of(
                ExploreProposalData.of(myProposal(32L, 5L, ProposalStatus.ACCEPTED), List.of(12L, 3L)),
                ExploreProposalData.of(myProposal(31L, 5L, ProposalStatus.PENDING), List.of(3L))));
        when(ownerService.getOwnerProfilesByIds(Set.of(5L))).thenReturn(Map.of(5L, Owner.builder()
                .id(5L).storeName("가꿈 카페").storeAddress(null).profileImageUrl("https://example.com/s.png").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(3L, 12L))).thenReturn(Map.of(
                3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인"),
                12L, SpecialtyDetail.of(12L, "영상 편집", 2L, "영상")));

        MyProposalListResult result = proposalFacade.getMyProposals(USERNAME);

        assertThat(result.getProposals()).extracting(p -> p.getProposalId(), p -> p.getStatus())
                .containsExactly(tuple(32L, ProposalStatus.ACCEPTED), tuple(31L, ProposalStatus.PENDING));
        assertThat(result.getProposals().get(0).getProposedSolution()).isEqualTo("사진 메뉴판으로 바꿉니다.");
        assertThat(result.getProposals().get(0).getSpecialtyCategories())
                .extracting(SpecialtyCategoryResult::getId).containsExactly(1L, 2L);
        assertThat(result.getProposals().get(0).getStore().getStoreName()).isEqualTo("가꿈 카페");
        assertThat(result.getProposals().get(0).getStore().getStoreAddress()).isNull();
        assertThat(result.getProposals().get(0).getStore().getProfileImageUrl()).isEqualTo("https://example.com/s.png");
        ArgumentCaptor<GetMyProposalsCommand> captor = ArgumentCaptor.forClass(GetMyProposalsCommand.class);
        verify(proposalService).getMyProposals(captor.capture());
        assertThat(captor.getValue().getStudentProfileId()).isEqualTo(7L);
        verify(ownerService, times(1)).getOwnerProfilesByIds(any());
        verify(specialtyCategoryService, times(1)).getSpecialtyDetails(any());
    }

    @Test
    @DisplayName("보낸 제안이 없으면 빈 목록을 반환하고 매장·분류를 조회하지 않는다")
    void returnsEmptyMyProposals() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        when(proposalService.getMyProposals(any())).thenReturn(List.of());

        assertThat(proposalFacade.getMyProposals(USERNAME).getProposals()).isEmpty();
        verifyNoInteractions(ownerService, specialtyCategoryService);
    }

    @Test
    @DisplayName("학생이 아니면 PROPOSAL_LIST_STUDENT_REQUIRED로 거부한다")
    void rejectsNonStudentForMyProposals() {
        givenUser(UserRole.OWNER);

        assertError(() -> proposalFacade.getMyProposals(USERNAME), ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("학생 프로필이 없으면 PROPOSAL_LIST_STUDENT_REQUIRED로 거부한다")
    void rejectsMissingStudentProfileForMyProposals() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> proposalFacade.getMyProposals(USERNAME), ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED);
        verifyNoInteractions(proposalService);
    }

    private Proposal myProposal(Long id, Long ownerProfileId, ProposalStatus status) {
        return Proposal.builder().id(id).studentProfileId(7L).ownerProfileId(ownerProfileId).title("제안 " + id)
                .customerProblem("메뉴를 알아보기 어렵습니다.").proposedSolution("사진 메뉴판으로 바꿉니다.")
                .likeCount(5).status(status).build();
    }

    @Test
    @DisplayName("받은 제안을 본인 사장님 프로필 ID로 조회하고 학생·사용자·분류를 중복 없이 일괄 조회해 카드로 구성한다")
    void returnsReceivedProposals() {
        givenUser(UserRole.OWNER);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(5L).build());
        when(proposalService.getReceivedProposals(any(GetReceivedProposalsCommand.class))).thenReturn(List.of(
                ExploreProposalData.of(receivedProposal(33L, 7L, ProposalStatus.REJECTED), List.of(12L, 3L)),
                ExploreProposalData.of(receivedProposal(32L, 8L, ProposalStatus.PENDING), List.of(3L)),
                ExploreProposalData.of(receivedProposal(31L, 7L, ProposalStatus.ACCEPTED), List.of())));
        when(studentService.getStudentProfilesByIds(List.of(7L, 8L))).thenReturn(Map.of(
                7L, Student.builder().id(7L).userId(STUDENT_USER_ID).major("소프트웨어학부").studentNumber("2024123456").build(),
                8L, Student.builder().id(8L).userId(OTHER_STUDENT_USER_ID).major("시각디자인학부").studentNumber("2023111111").build()));
        // 학생 맵의 순회 순서에 의존하지 않도록 중복 없는 사용자 ID 집합으로만 맞춘다
        when(userService.getUsersByIds(argThat(ids -> ids.size() == 2
                && Set.copyOf(ids).equals(Set.of(STUDENT_USER_ID, OTHER_STUDENT_USER_ID))))).thenReturn(Map.of(
                STUDENT_USER_ID, User.builder().id(STUDENT_USER_ID).name("홍길동").build(),
                OTHER_STUDENT_USER_ID, User.builder().id(OTHER_STUDENT_USER_ID).name("김철수").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(3L, 12L))).thenReturn(Map.of(
                3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인"),
                12L, SpecialtyDetail.of(12L, "영상 편집", 2L, "영상")));

        ReceivedProposalListResult result = proposalFacade.getReceivedProposals(USERNAME);

        assertThat(result.getProposals()).extracting(p -> p.getProposalId(), p -> p.getStatus())
                .containsExactly(tuple(33L, ProposalStatus.REJECTED), tuple(32L, ProposalStatus.PENDING),
                        tuple(31L, ProposalStatus.ACCEPTED));
        assertThat(result.getProposals().get(0).getLikeCount()).isEqualTo(5);
        assertThat(result.getProposals().get(0).getSpecialtyCategories())
                .extracting(SpecialtyCategoryResult::getId).containsExactly(1L, 2L);
        assertThat(result.getProposals().get(2).getSpecialtyCategories()).isEmpty();
        assertThat(result.getProposals()).extracting(p -> p.getStudent().getStudentProfileId(),
                        p -> p.getStudent().getName(), p -> p.getStudent().getStudentNumber(),
                        p -> p.getStudent().getMajor())
                .containsExactly(
                        tuple(7L, "홍길동", "2024123456", "소프트웨어학부"),
                        tuple(8L, "김철수", "2023111111", "시각디자인학부"),
                        tuple(7L, "홍길동", "2024123456", "소프트웨어학부"));
        ArgumentCaptor<GetReceivedProposalsCommand> captor = ArgumentCaptor.forClass(GetReceivedProposalsCommand.class);
        verify(proposalService).getReceivedProposals(captor.capture());
        assertThat(captor.getValue().getOwnerProfileId()).isEqualTo(5L);
        verify(studentService, times(1)).getStudentProfilesByIds(any());
        verify(userService, times(1)).getUsersByIds(any());
        verify(specialtyCategoryService, times(1)).getSpecialtyDetails(any());
    }

    @Test
    @DisplayName("받은 제안이 없으면 빈 목록을 반환하고 학생·사용자·분류를 조회하지 않는다")
    void returnsEmptyReceivedProposals() {
        givenUser(UserRole.OWNER);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(5L).build());
        when(proposalService.getReceivedProposals(any())).thenReturn(List.of());

        assertThat(proposalFacade.getReceivedProposals(USERNAME).getProposals()).isEmpty();
        verifyNoInteractions(studentService, specialtyCategoryService);
        verify(userService, never()).getUsersByIds(any());
    }

    @Test
    @DisplayName("사장님이 아니면 PROPOSAL_LIST_OWNER_REQUIRED로 거부한다")
    void rejectsNonOwnerForReceivedProposals() {
        givenUser(UserRole.STUDENT);

        assertError(() -> proposalFacade.getReceivedProposals(USERNAME), ErrorCode.PROPOSAL_LIST_OWNER_REQUIRED);
        verifyNoInteractions(proposalService, ownerService);
    }

    @Test
    @DisplayName("사장님 프로필이 없으면 OWNER_PROFILE_NOT_FOUND를 그대로 전달하고 제안을 조회하지 않는다")
    void rejectsMissingOwnerProfileForReceivedProposals() {
        givenUser(UserRole.OWNER);
        when(ownerService.getOwnerProfile(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.OWNER_PROFILE_NOT_FOUND));

        assertError(() -> proposalFacade.getReceivedProposals(USERNAME), ErrorCode.OWNER_PROFILE_NOT_FOUND);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("학생 프로필 일괄 조회의 참조 누락 오류는 그대로 전달하고 사용자·분류를 조회하지 않는다")
    void propagatesMissingStudentFromBatchLookup() {
        givenReceivedProposal();
        when(studentService.getStudentProfilesByIds(List.of(7L)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(() -> proposalFacade.getReceivedProposals(USERNAME), ErrorCode.INTERNAL_SERVER_ERROR);
        verify(userService, never()).getUsersByIds(any());
        verifyNoInteractions(specialtyCategoryService);
    }

    @Test
    @DisplayName("사용자 일괄 조회의 참조·이름 누락 오류는 그대로 전달하고 분류를 조회하지 않는다")
    void propagatesMissingStudentUserFromBatchLookup() {
        givenReceivedProposal();
        when(studentService.getStudentProfilesByIds(List.of(7L))).thenReturn(Map.of(
                7L, Student.builder().id(7L).userId(STUDENT_USER_ID).major("소프트웨어학부").studentNumber("2024123456").build()));
        when(userService.getUsersByIds(List.of(STUDENT_USER_ID)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(() -> proposalFacade.getReceivedProposals(USERNAME), ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(specialtyCategoryService);
    }

    @Test
    @DisplayName("제안의 소분류를 조회할 수 없으면 분류를 누락하지 않고 예외로 중단한다")
    void failsWhenReceivedProposalSpecialtyIsMissing() {
        givenReceivedProposal();
        when(studentService.getStudentProfilesByIds(List.of(7L))).thenReturn(Map.of(
                7L, Student.builder().id(7L).userId(STUDENT_USER_ID).major("소프트웨어학부").studentNumber("2024123456").build()));
        when(userService.getUsersByIds(List.of(STUDENT_USER_ID))).thenReturn(Map.of(
                STUDENT_USER_ID, User.builder().id(STUDENT_USER_ID).name("홍길동").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(3L))).thenReturn(Map.of());

        assertThatThrownBy(() -> proposalFacade.getReceivedProposals(USERNAME))
                .isInstanceOf(IllegalStateException.class);
    }

    private void givenReceivedProposal() {
        givenUser(UserRole.OWNER);
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(5L).build());
        when(proposalService.getReceivedProposals(any())).thenReturn(List.of(
                ExploreProposalData.of(receivedProposal(31L, 7L, ProposalStatus.PENDING), List.of(3L))));
    }

    private Proposal receivedProposal(Long id, Long studentProfileId, ProposalStatus status) {
        return Proposal.builder().id(id).studentProfileId(studentProfileId).ownerProfileId(5L).title("제안 " + id)
                .customerProblem("메뉴를 알아보기 어렵습니다.").proposedSolution("사진 메뉴판으로 바꿉니다.")
                .likeCount(5).status(status).build();
    }
}
