package com.gakkum.backend.application.proposal.facade;

import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobDeclineResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCancelResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalRejectResult;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobStartResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;
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

    private static final String DEMO_SESSION_A = "01K6DEMO00000000000000000A";

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5D";
    private static final String OTHER_STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5E";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5F";
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
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
    // 한국 시간 2026-10-05 08:00. UTC 날짜(10-04)와 달라 예상 마감일이 한국 날짜 기준인지 드러난다
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T23:00:00Z"), ZoneOffset.UTC);
    private final ProposalFacade proposalFacade = new ProposalFacade(
            userService, studentService, ownerService, specialtyService, specialtyCategoryService, mediaService,
            proposalService, reviewService, jobService, paymentService, chatRoomService, clock);

    @Test
    @DisplayName("학생이 존재하는 사장님과 소분류, 업로드된 본인 사진으로 제안을 보내면 저장하고 제안 ID를 반환한다")
    void createsProposal() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        givenIssuedImage(IMAGE_URL_2, KEY_2, true);
        CreateProposalCommand command = command(List.of(IMAGE_URL_1, IMAGE_URL_2));
        when(proposalService.createProposal(command, 7L, null)).thenReturn(Proposal.builder().id(31L).build());

        ProposalCreateResult result = proposalFacade.createProposal(command);

        assertThat(result.getProposalId()).isEqualTo(31L);
        verify(ownerService).validateOwnerProfileExists(5L, null);
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
        when(proposalService.createProposal(command, 7L, null)).thenReturn(Proposal.builder().id(32L).build());

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
        doThrow(new BusinessException(ErrorCode.OWNER_NOT_FOUND)).when(ownerService).validateOwnerProfileExists(5L, null);

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
        verify(proposalService, never()).createProposal(any(), anyLong(), any());
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
        verify(proposalService, never()).createProposal(any(), anyLong(), any());
    }

    @Test
    @DisplayName("역할, 대상 사장님, 소분류, 사진 순서로 검증한 뒤 저장한다")
    void validatesBeforeSaving() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        givenIssuedImage(IMAGE_URL_1, KEY_1, true);
        CreateProposalCommand command = command(List.of(IMAGE_URL_1));
        when(proposalService.createProposal(command, 7L, null)).thenReturn(Proposal.builder().id(31L).build());

        proposalFacade.createProposal(command);

        InOrder order = inOrder(studentService, ownerService, specialtyService, mediaService, proposalService);
        order.verify(studentService).findStudentProfileByUserId(USER_ID);
        order.verify(ownerService).validateOwnerProfileExists(5L, null);
        order.verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        order.verify(mediaService).isImageUploaded(KEY_1);
        order.verify(proposalService).createProposal(command, 7L, null);
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
        assertThat(result.getStoreAddress()).isEqualTo("서울시 마포구 1");
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
        when(ownerService.getOwnerProfileById(5L)).thenReturn(Owner.builder().id(5L).storeName("가게 이름")
                .storeAddress("서울시 마포구 1").build());
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

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = { "AWAITING_START", "MATCHED", "CLOSED", "CANCELLED" })
    @DisplayName("내가 보낸 제안을 매장·분류·의뢰를 일괄 조회해 구성하고 연결 의뢰의 상태를 전달한다")
    void returnsMyProposals(JobStatus jobStatus) {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 15, 30);
        when(proposalService.getMyProposals(any(GetMyProposalsCommand.class))).thenReturn(List.of(
                ExploreProposalData.of(myProposal(32L, 5L, ProposalStatus.ACCEPTED, createdAt), List.of(12L, 3L)),
                ExploreProposalData.of(myProposal(31L, 5L, ProposalStatus.PENDING, null), List.of(3L))));
        when(ownerService.getOwnerProfilesByIds(Set.of(5L))).thenReturn(Map.of(5L, Owner.builder()
                .id(5L).storeName("가꿈 카페").storeAddress(null).profileImageUrl("https://example.com/s.png").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(3L, 12L))).thenReturn(Map.of(
                3L, SpecialtyDetail.of(3L, "로고 디자인", 1L, "디자인"),
                12L, SpecialtyDetail.of(12L, "영상 편집", 2L, "영상")));

        when(jobService.getJobsByProposalIds(List.of(32L, 31L))).thenReturn(Map.of(
                32L, Job.builder().id(420L).proposalId(32L).status(jobStatus).build()));

        MyProposalListResult result = proposalFacade.getMyProposals(USERNAME);

        assertThat(result.getProposals()).extracting(p -> p.getProposalId(), p -> p.getStatus())
                .containsExactly(tuple(32L, ProposalStatus.ACCEPTED), tuple(31L, ProposalStatus.PENDING));
        // 연결 의뢰는 제안 수와 무관하게 한 번에 조회하고 결제 전 제안은 null이다
        assertThat(result.getProposals()).extracting(p -> p.getJobId()).containsExactly(420L, null);
        assertThat(result.getProposals()).extracting(p -> p.getJobStatus()).containsExactly(jobStatus, null);
        // 생성 시각은 변환 없이 원본 그대로 전달한다
        assertThat(result.getProposals()).extracting(p -> p.getCreatedAt()).containsExactly(createdAt, null);
        verify(jobService, times(1)).getJobsByProposalIds(any());
        verify(jobService, never()).findJobByProposalId(anyLong());
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
        verifyNoInteractions(ownerService, specialtyCategoryService, jobService);
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

    private Proposal myProposal(Long id, Long ownerProfileId, ProposalStatus status, LocalDateTime createdAt) {
        return Proposal.builder().id(id).studentProfileId(7L).ownerProfileId(ownerProfileId).title("제안 " + id)
                .customerProblem("메뉴를 알아보기 어렵습니다.").proposedSolution("사진 메뉴판으로 바꿉니다.")
                .likeCount(5).status(status).createdAt(createdAt).build();
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

        when(jobService.getJobsByProposalIds(List.of(33L, 32L, 31L))).thenReturn(Map.of(
                31L, Job.builder().id(410L).proposalId(31L).status(JobStatus.MATCHED).build()));

        ReceivedProposalListResult result = proposalFacade.getReceivedProposals(USERNAME);

        assertThat(result.getProposals()).extracting(p -> p.getProposalId(), p -> p.getStatus())
                .containsExactly(tuple(33L, ProposalStatus.REJECTED), tuple(32L, ProposalStatus.PENDING),
                        tuple(31L, ProposalStatus.ACCEPTED));
        // 연결 의뢰는 제안 수와 무관하게 한 번에 조회하고 결제 전 제안은 null이다
        assertThat(result.getProposals()).extracting(p -> p.getJobId()).containsExactly(null, null, 410L);
        assertThat(result.getProposals()).extracting(p -> p.getJobStatus())
                .containsExactly(null, null, JobStatus.MATCHED);
        // 생성 시각은 저장된 원본을 그대로 넘기고 한국 시각 변환은 응답이 맡는다
        assertThat(result.getProposals()).extracting(p -> p.getCreatedAt())
                .containsOnly(LocalDateTime.of(2026, 10, 5, 15, 30));
        verify(jobService, times(1)).getJobsByProposalIds(any());
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
        verifyNoInteractions(studentService, specialtyCategoryService, jobService);
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
                .likeCount(5).status(status).createdAt(LocalDateTime.of(2026, 10, 5, 15, 30)).build();
    }

    private Proposal detailProposal(ProposalStatus status) {
        return Proposal.builder().id(31L).studentProfileId(7L).ownerProfileId(5L).title("메뉴판 개선 제안")
                .customerProblem("문제").proposedSolution("해결").workPlan("계획").proposedFee(50000L)
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).likeCount(0).status(status).build();
    }

    // 제안 31번의 상세를 조회할 준비. 매장 사장님은 OWNER_USER_ID, 제안한 학생은 STUDENT_USER_ID다
    private void givenProposalDetail(ProposalStatus status, String viewerId, UserRole viewerRole) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(viewerId).username(USERNAME).role(viewerRole).build());
        when(proposalService.getProposalDetail(31L))
                .thenReturn(ProposalDetailData.of(detailProposal(status), List.of()));
        when(ownerService.getOwnerProfileById(5L))
                .thenReturn(Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가게 이름").build());
        givenProposingStudent();
        when(specialtyCategoryService.getSpecialtyDetails(List.of())).thenReturn(Map.of());
    }

    // 학생 희망 금액은 50,000원이고 사장님이 결제한 확정 작업비는 120,000원이다
    private Job givenPaidJob(JobStatus status, LocalDateTime startedAt) {
        Job job = Job.builder().id(42L).proposalId(31L).status(status).budget(120000L)
                .draftDeadline(LocalDate.of(2026, 10, 8)).finalDeadline(LocalDate.of(2026, 10, 12))
                .revisionCount(2).acceptanceMessage("매장 분위기에 맞춰 주세요.").startedAt(startedAt).build();
        when(jobService.findJobByProposalId(31L)).thenReturn(Optional.of(job));
        when(paymentService.getProposalPaidAt(31L)).thenReturn(Instant.parse("2026-10-05T03:00:00Z"));
        return job;
    }

    @Test
    @DisplayName("결제 전 제안 상세는 초안 기간과 상태, 한국 날짜 기준 오늘에 기간을 더한 예상 마감일을 반환하고 의뢰와 확정 조건은 없다")
    void returnsEstimatedDeadlinesBeforePayment() {
        givenProposalDetail(ProposalStatus.PENDING, OWNER_USER_ID, UserRole.OWNER);

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getDraftDays()).isEqualTo(3);
        assertThat(result.getFinalDays()).isEqualTo(7);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        // 주소를 등록하지 않은 매장과 생성 시각이 없는 제안은 null 그대로 전달한다
        assertThat(result.getStoreAddress()).isNull();
        assertThat(result.getCreatedAt()).isNull();
        // 시계는 UTC 10월 4일 23시 = 한국 10월 5일 8시
        assertThat(result.getEstimatedDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(result.getEstimatedFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(result.getJobId()).isNull();
        assertThat(result.getAgreement()).isNull();
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제된 제안 상세는 제안을 받은 사장님에게 연결 의뢰와 확정 작업 조건을 반환하고 예상 마감일은 내리지 않는다")
    void returnsAgreementToReceivingOwner() {
        givenProposalDetail(ProposalStatus.AWAITING_START, OWNER_USER_ID, UserRole.OWNER);
        givenPaidJob(JobStatus.AWAITING_START, null);

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getEstimatedDraftDeadline()).isNull();
        assertThat(result.getEstimatedFinalDeadline()).isNull();
        assertThat(result.getAgreement().getJobStatus()).isEqualTo(JobStatus.AWAITING_START);
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        assertThat(result.getAgreement().getBudget()).isEqualTo(120000L);
        assertThat(result.getAgreement().getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(result.getAgreement().getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(result.getAgreement().getRevisionCount()).isEqualTo(2);
        assertThat(result.getAgreement().getMessageToStudent()).isEqualTo("매장 분위기에 맞춰 주세요.");
        assertThat(result.getAgreement().getPaidAt()).isEqualTo(Instant.parse("2026-10-05T03:00:00Z"));
        assertThat(result.getAgreement().getStartedAt()).isNull();
    }

    @Test
    @DisplayName("결제된 제안 상세는 제안한 학생에게도 확정 작업 조건과 작업 시작 시각을 반환하고 희망 금액과 실제 결제 금액을 따로 내린다")
    void returnsAgreementToProposingStudent() {
        givenProposalDetail(ProposalStatus.ACCEPTED, STUDENT_USER_ID, UserRole.STUDENT);
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 6, 9, 30);
        givenPaidJob(JobStatus.MATCHED, startedAt);

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        assertThat(result.getAgreement().getBudget()).isEqualTo(120000L);
        assertThat(result.getAgreement().getJobStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(result.getAgreement().getMessageToStudent()).isEqualTo("매장 분위기에 맞춰 주세요.");
        assertThat(result.getAgreement().getStartedAt()).isEqualTo(startedAt);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = { "OWNER", "STUDENT" })
    @DisplayName("제안의 당사자가 아닌 인증 사용자는 결제된 제안의 내용과 의뢰 ID는 볼 수 있지만 한마디와 확정 조건은 받지 못한다")
    void hidesAgreementFromOtherUsers(UserRole role) {
        givenProposalDetail(ProposalStatus.AWAITING_START, USER_ID, role);
        givenPaidJob(JobStatus.AWAITING_START, null);

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getProposedFee()).isEqualTo(50000L);
        assertThat(result.getAgreement()).isNull();
        verifyNoInteractions(paymentService);
    }

    private void givenStartingStudent() {
        givenUser(UserRole.STUDENT);
        givenStudentProfile();
    }

    @Test
    @DisplayName("학생이 공감하면 학생 프로필 ID와 격리 범위로 공감을 켜고 반영된 공감 수와 likedByMe true를 반환한다")
    void likesProposal() {
        givenStartingStudent();
        when(proposalService.likeProposal(31L, 7L, null))
                .thenReturn(Proposal.builder().id(31L).likeCount(5).build());

        ProposalLikeResult result = proposalFacade.likeProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getLikeCount()).isEqualTo(5);
        assertThat(result.isLikedByMe()).isTrue();
        verify(proposalService, never()).unlikeProposal(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("학생이 공감을 취소하면 학생 프로필 ID와 격리 범위로 공감을 끄고 반영된 공감 수와 likedByMe false를 반환한다")
    void unlikesProposal() {
        givenStartingStudent();
        when(proposalService.unlikeProposal(31L, 7L, null))
                .thenReturn(Proposal.builder().id(31L).likeCount(4).build());

        ProposalLikeResult result = proposalFacade.unlikeProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getLikeCount()).isEqualTo(4);
        assertThat(result.isLikedByMe()).isFalse();
        verify(proposalService, never()).likeProposal(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("데모 학생의 공감 추가·취소는 자기 데모 세션 ID를 격리 범위로 전달한다")
    void passesDemoSessionForLike() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).demoSessionId(DEMO_SESSION_A).build());
        givenStudentProfile();
        Proposal proposal = Proposal.builder().id(31L).likeCount(1).demoSessionId(DEMO_SESSION_A).build();
        when(proposalService.likeProposal(31L, 7L, DEMO_SESSION_A)).thenReturn(proposal);
        when(proposalService.unlikeProposal(31L, 7L, DEMO_SESSION_A)).thenReturn(proposal);

        assertThat(proposalFacade.likeProposal(USERNAME, 31L).isLikedByMe()).isTrue();
        assertThat(proposalFacade.unlikeProposal(USERNAME, 31L).isLikedByMe()).isFalse();
        verify(proposalService).likeProposal(31L, 7L, DEMO_SESSION_A);
        verify(proposalService).unlikeProposal(31L, 7L, DEMO_SESSION_A);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "OWNER", "PENDING" })
    @DisplayName("사장님과 가입 대기 사용자의 공감 추가·취소는 PROPOSAL_403_LIKE_STUDENT로 거부하고 학생 프로필과 제안을 조회하지 않는다")
    void rejectsNonStudentForLike(UserRole role) {
        givenUser(role);

        assertError(() -> proposalFacade.likeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED);
        assertError(() -> proposalFacade.unlikeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED);
        verifyNoInteractions(studentService, proposalService);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생 역할 사용자의 공감 추가·취소는 PROPOSAL_403_LIKE_STUDENT로 거부하고 제안을 잠그지 않는다")
    void rejectsStudentWithoutProfileForLike() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> proposalFacade.likeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED);
        assertError(() -> proposalFacade.unlikeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED);
        verifyNoInteractions(proposalService);
    }

    @Test
    @DisplayName("잠긴 사용자의 공감 추가·취소는 UNAUTHORIZED로 거부하고 학생 프로필과 제안을 조회하지 않는다")
    void rejectsInactiveUserForLike() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> proposalFacade.likeProposal(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        assertError(() -> proposalFacade.unlikeProposal(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(studentService, proposalService);
    }

    @Test
    @DisplayName("없거나 격리 범위가 다른 제안의 PROPOSAL_404는 공감 추가·취소에서 그대로 전달한다")
    void propagatesProposalNotFoundForLike() {
        givenStartingStudent();
        when(proposalService.likeProposal(31L, 7L, null))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));
        when(proposalService.unlikeProposal(31L, 7L, null))
                .thenThrow(new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND));

        assertError(() -> proposalFacade.likeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.unlikeProposal(USERNAME, 31L), ErrorCode.PROPOSAL_NOT_FOUND);
    }

    @ParameterizedTest(name = "공감 기록 {0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = { true, false })
    @DisplayName("학생의 제안 상세는 본인 학생 프로필의 공감 기록으로 likedByMe를 채운다")
    void returnsLikedByMeToStudent(boolean liked) {
        givenProposalDetail(ProposalStatus.PENDING, USER_ID, UserRole.STUDENT);
        givenStudentProfile();
        when(proposalService.getLikedProposalIds(7L, List.of(31L))).thenReturn(liked ? Set.of(31L) : Set.of());

        assertThat(proposalFacade.getProposalDetail(USERNAME, 31L).isLikedByMe()).isEqualTo(liked);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "OWNER", "PENDING" })
    @DisplayName("학생이 아닌 조회자의 제안 상세는 학생 프로필과 공감 기록을 조회하지 않고 likedByMe를 false로 반환한다")
    void returnsNotLikedToNonStudent(UserRole role) {
        givenProposalDetail(ProposalStatus.PENDING, USER_ID, role);

        assertThat(proposalFacade.getProposalDetail(USERNAME, 31L).isLikedByMe()).isFalse();
        verify(studentService, never()).findStudentProfileByUserId(anyString());
        verify(proposalService, never()).getLikedProposalIds(anyLong(), any());
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생의 제안 상세는 공감 기록을 조회하지 않고 likedByMe를 false로 반환한다")
    void returnsNotLikedToStudentWithoutProfile() {
        givenProposalDetail(ProposalStatus.PENDING, USER_ID, UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(proposalFacade.getProposalDetail(USERNAME, 31L).isLikedByMe()).isFalse();
        verify(proposalService, never()).getLikedProposalIds(anyLong(), any());
    }

    @Test
    @DisplayName("제안한 학생이 작업을 시작하면 제안을 수락으로, 의뢰를 진행 중으로 넘기고 채팅방을 만들어 확정 마감일과 함께 반환한다")
    void startsProposalJob() {
        givenStartingStudent();
        Proposal proposal = detailProposal(ProposalStatus.AWAITING_START);
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 6, 9, 30);
        Job started = Job.builder().id(42L).proposalId(31L).status(JobStatus.MATCHED).startedAt(startedAt)
                .draftDeadline(LocalDate.of(2026, 10, 8)).finalDeadline(LocalDate.of(2026, 10, 12)).build();
        when(jobService.getStartableProposalId(42L, 7L)).thenReturn(31L);
        when(proposalService.getStartableProposalForUpdate(31L, 7L)).thenReturn(proposal);
        when(jobService.startJob(42L, 31L, 7L)).thenReturn(started);
        when(chatRoomService.getOrCreate(42L)).thenReturn(ChatRoom.create(42L));

        ProposalJobStartResult result = proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L));

        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getJobStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(result.getProposalStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(result.getStartedAt()).isEqualTo(startedAt);
        assertThat(result.getChatRoomId()).hasSize(26);
        assertThat(result.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(result.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 12));
        // 잠금 순서: 제안 ID를 잠금 없이 읽은 뒤 제안 → 의뢰, 채팅방은 마지막
        InOrder order = inOrder(jobService, proposalService, chatRoomService);
        order.verify(jobService).getStartableProposalId(42L, 7L);
        order.verify(proposalService).getStartableProposalForUpdate(31L, 7L);
        order.verify(jobService).startJob(42L, 31L, 7L);
        order.verify(chatRoomService).getOrCreate(42L);
    }

    @Test
    @DisplayName("이미 시작한 의뢰의 재요청은 제안 상태를 그대로 두고 기존 시작 시각과 채팅방을 반환한다")
    void restartReturnsExistingStartAndChatRoom() {
        givenStartingStudent();
        Proposal proposal = detailProposal(ProposalStatus.ACCEPTED);
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 6, 9, 30);
        ChatRoom existingRoom = ChatRoom.create(42L);
        when(jobService.getStartableProposalId(42L, 7L)).thenReturn(31L);
        when(proposalService.getStartableProposalForUpdate(31L, 7L)).thenReturn(proposal);
        when(jobService.startJob(42L, 31L, 7L)).thenReturn(
                Job.builder().id(42L).proposalId(31L).status(JobStatus.MATCHED).startedAt(startedAt).build());
        when(chatRoomService.getOrCreate(42L)).thenReturn(existingRoom);

        ProposalJobStartResult first = proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L));
        ProposalJobStartResult second = proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L));

        assertThat(second.getStartedAt()).isEqualTo(first.getStartedAt()).isEqualTo(startedAt);
        assertThat(second.getChatRoomId()).isEqualTo(first.getChatRoomId()).isEqualTo(existingRoom.getId());
        assertThat(second.getProposalStatus()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    @DisplayName("학생이 아니거나 학생 프로필이 없으면 JOB_START_403으로 거부하고 의뢰와 제안을 조회하지 않는다")
    void rejectsNonStudentForStart() {
        givenUser(UserRole.OWNER);
        assertError(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L)),
                ErrorCode.JOB_START_FORBIDDEN);

        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());
        assertError(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L)),
                ErrorCode.JOB_START_FORBIDDEN);

        verifyNoInteractions(jobService, proposalService, chatRoomService);
    }

    @Test
    @DisplayName("담당 학생이 아니거나 시작할 수 없는 의뢰이면 제안을 잠그지 않고 채팅방도 만들지 않는다")
    void doesNotLockOrCreateRoomWhenJobIsNotStartable() {
        givenStartingStudent();
        when(jobService.getStartableProposalId(42L, 7L))
                .thenThrow(new BusinessException(ErrorCode.JOB_START_FORBIDDEN));

        assertError(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L)),
                ErrorCode.JOB_START_FORBIDDEN);
        verifyNoInteractions(proposalService, chatRoomService);
        verify(jobService, never()).startJob(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("잠근 뒤 의뢰를 시작할 수 없으면 제안을 수락으로 바꾸지 않고 채팅방을 만들지 않는다")
    void keepsProposalWhenStartFailsUnderLock() {
        givenStartingStudent();
        Proposal proposal = detailProposal(ProposalStatus.AWAITING_START);
        when(jobService.getStartableProposalId(42L, 7L)).thenReturn(31L);
        when(proposalService.getStartableProposalForUpdate(31L, 7L)).thenReturn(proposal);
        when(jobService.startJob(42L, 31L, 7L)).thenThrow(new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE));

        assertError(() -> proposalFacade.startProposalJob(StartProposalJobCommand.of(USERNAME, 42L)),
                ErrorCode.JOB_START_NOT_AVAILABLE);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("데모 학생의 제안은 자기 데모 세션 ID로 대상 사장님을 확인하고 같은 값으로 저장한다")
    void createsProposalInStudentDemoSession() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).demoSessionId(DEMO_SESSION_A).build());
        givenStudentProfile();
        CreateProposalCommand command = command(List.of());
        when(proposalService.createProposal(command, 7L, DEMO_SESSION_A))
                .thenReturn(Proposal.builder().id(33L).build());

        assertThat(proposalFacade.createProposal(command).getProposalId()).isEqualTo(33L);
        verify(ownerService).validateOwnerProfileExists(5L, DEMO_SESSION_A);
    }

    @Test
    @DisplayName("대상 사장님이 학생과 다른 격리 범위면 OWNER_404로 거부하고 제안을 저장하지 않는다")
    void rejectsProposalToOwnerOutsideDemoSession() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).demoSessionId(DEMO_SESSION_A).build());
        givenStudentProfile();
        doThrow(new BusinessException(ErrorCode.OWNER_NOT_FOUND))
                .when(ownerService).validateOwnerProfileExists(5L, DEMO_SESSION_A);

        assertError(() -> proposalFacade.createProposal(command(List.of())), ErrorCode.OWNER_NOT_FOUND);
        verify(proposalService, never()).createProposal(any(), anyLong(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "조회자 {0}, 제안 {1}")
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "null, 01K6DEMO00000000000000000A",
            "01K6DEMO00000000000000000A, null",
            "01K6DEMO00000000000000000A, 01K6DEMO00000000000000000B"}, nullValues = "null")
    @DisplayName("조회자와 격리 범위가 다른 제안 상세는 PROPOSAL_404로 거부하고 매장·학생 정보를 조회하지 않는다")
    void rejectsProposalDetailOutsideDemoSession(String viewerSession, String proposalSession) {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).demoSessionId(viewerSession).build());
        when(proposalService.getProposalDetail(31L)).thenReturn(ProposalDetailData.of(
                Proposal.builder().id(31L).studentProfileId(7L).ownerProfileId(5L)
                        .demoSessionId(proposalSession).build(), List.of()));

        assertError(() -> proposalFacade.getProposalDetail(USERNAME, 31L), ErrorCode.PROPOSAL_NOT_FOUND);
        verifyNoInteractions(ownerService, studentService, reviewService, jobService, paymentService);
        // 격리 범위 확인 전에는 공감 기록도 조회하지 않는다
        verify(proposalService, never()).getLikedProposalIds(anyLong(), any());
    }

    @Test
    @DisplayName("같은 데모 세션의 제안 상세는 조회된다")
    void returnsProposalDetailWithinDemoSession() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.OWNER).demoSessionId(DEMO_SESSION_A).build());
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(7L).ownerProfileId(5L)
                .title("메뉴판 개선 제안").customerProblem("문제").proposedSolution("해결").workPlan("계획")
                .proposedFee(50000L).draftDays(3).finalDays(7).referenceImageUrls(List.of()).likeCount(0)
                .status(ProposalStatus.PENDING).createdAt(LocalDateTime.of(2026, 9, 30, 10, 0))
                .demoSessionId(DEMO_SESSION_A).build();
        when(proposalService.getProposalDetail(31L)).thenReturn(ProposalDetailData.of(proposal, List.of()));
        givenProposingStudent();
        givenStore();

        assertThat(proposalFacade.getProposalDetail(USERNAME, 31L).getProposalId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("제안한 학생이 결제 대기 주문이 없는 결제 전 제안을 취소하면 제안을 잠근 뒤 취소하고 취소 상태를 반환한다")
    void cancelsPendingProposal() {
        givenStartingStudent();
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(7L).status(ProposalStatus.PENDING)
                .likeCount(2).build();
        when(proposalService.getCancellableProposalForUpdate(31L, 7L, null)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.empty());
        org.mockito.Mockito.doAnswer(invocation -> {
            proposal.cancel();
            return null;
        }).when(proposalService).cancelProposal(proposal);

        ProposalCancelResult result = proposalFacade.cancelProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        // 결제 대기 주문은 제안 행을 잠근 뒤에 확인하고, 그 뒤에 취소한다
        InOrder order = inOrder(proposalService, paymentService);
        order.verify(proposalService).getCancellableProposalForUpdate(31L, 7L, null);
        order.verify(paymentService).findPendingProposalPayment(31L);
        order.verify(proposalService).cancelProposal(proposal);
        verifyNoInteractions(jobService, chatRoomService, ownerService);
    }

    @Test
    @DisplayName("이미 취소한 본인 제안의 취소 재요청은 결제 주문을 확인하지 않고 아무것도 바꾸지 않은 채 취소 상태를 반환한다")
    void cancelIsIdempotentForAuthor() {
        givenStartingStudent();
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(7L).status(ProposalStatus.CANCELLED)
                .likeCount(0).build();
        when(proposalService.getCancellableProposalForUpdate(31L, 7L, null)).thenReturn(proposal);

        ProposalCancelResult result = proposalFacade.cancelProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        verify(proposalService, never()).cancelProposal(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제 대기 주문이 있는 결제 전 제안의 취소는 PROPOSAL_409_CANCEL_PAYMENT_PENDING으로 거부하고 제안을 바꾸지 않는다")
    void rejectsCancelWithPendingPayment() {
        givenStartingStudent();
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(7L).status(ProposalStatus.PENDING)
                .likeCount(2).build();
        when(proposalService.getCancellableProposalForUpdate(31L, 7L, null)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.of(mock(Payment.class)));

        assertError(() -> proposalFacade.cancelProposal(USERNAME, 31L), ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposal.getLikeCount()).isEqualTo(2);
        verify(proposalService, never()).cancelProposal(any());
    }

    @Test
    @DisplayName("데모 학생의 제안 취소는 자기 데모 세션 ID를 격리 범위로 전달한다")
    void passesDemoSessionForCancel() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.STUDENT).demoSessionId(DEMO_SESSION_A).build());
        givenStudentProfile();
        Proposal proposal = Proposal.builder().id(31L).studentProfileId(7L).status(ProposalStatus.PENDING)
                .likeCount(0).demoSessionId(DEMO_SESSION_A).build();
        when(proposalService.getCancellableProposalForUpdate(31L, 7L, DEMO_SESSION_A)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.empty());

        proposalFacade.cancelProposal(USERNAME, 31L);

        verify(proposalService).getCancellableProposalForUpdate(31L, 7L, DEMO_SESSION_A);
        verify(proposalService).cancelProposal(proposal);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "OWNER", "PENDING" })
    @DisplayName("사장님과 가입 대기 사용자의 제안 취소는 PROPOSAL_403_CANCEL로 거부하고 학생 프로필과 제안을 조회하지 않는다")
    void rejectsNonStudentForCancel(UserRole role) {
        givenUser(role);

        assertError(() -> proposalFacade.cancelProposal(USERNAME, 31L), ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        verifyNoInteractions(studentService, proposalService, paymentService);
    }

    @Test
    @DisplayName("학생 프로필이 없는 학생 역할 사용자의 제안 취소는 PROPOSAL_403_CANCEL로 거부하고 제안을 잠그지 않는다")
    void rejectsStudentWithoutProfileForCancel() {
        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> proposalFacade.cancelProposal(USERNAME, 31L), ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        verifyNoInteractions(proposalService, paymentService);
    }

    @Test
    @DisplayName("잠긴 사용자의 제안 취소는 UNAUTHORIZED로 거부하고 학생 프로필과 제안을 조회하지 않는다")
    void rejectsInactiveUserForCancel() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> proposalFacade.cancelProposal(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(studentService, proposalService, paymentService);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ErrorCode.class,
            names = { "PROPOSAL_NOT_FOUND", "PROPOSAL_CANCEL_FORBIDDEN", "PROPOSAL_CANCEL_NOT_AVAILABLE" })
    @DisplayName("제안을 잠가 확인한 404·403·409는 그대로 전달하고 결제 주문을 확인하거나 취소하지 않는다")
    void propagatesCancelValidationErrors(ErrorCode errorCode) {
        givenStartingStudent();
        when(proposalService.getCancellableProposalForUpdate(31L, 7L, null))
                .thenThrow(new BusinessException(errorCode));

        assertError(() -> proposalFacade.cancelProposal(USERNAME, 31L), errorCode);
        verify(proposalService, never()).cancelProposal(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("받은 사장님이 결제 대기 주문이 없는 결제 전 제안을 거절하면 제안을 잠근 뒤 거절 주체와 UTC 시각을 남기고 거절 상태를 반환한다")
    void rejectsPendingProposal() {
        givenRejectingOwner(null);
        Proposal proposal = Proposal.builder().id(31L).ownerProfileId(5L).status(ProposalStatus.PENDING)
                .likeCount(2).build();
        when(proposalService.getRejectableProposalForUpdate(31L, 5L, null)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.empty());

        ProposalRejectResult result = proposalFacade.rejectProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(proposal.getRejectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 23, 0));
        assertThat(proposal.getLikeCount()).isEqualTo(2);
        // 결제 대기 주문은 제안 행을 잠근 뒤에 확인한다
        InOrder order = inOrder(proposalService, paymentService);
        order.verify(proposalService).getRejectableProposalForUpdate(31L, 5L, null);
        order.verify(paymentService).findPendingProposalPayment(31L);
        // 환불 같은 결제 변경과 공감 기록 삭제, 의뢰·채팅 변경은 하지 않는다
        verify(paymentService, never()).refundOnDecline(anyLong(), anyLong(), anyString());
        verify(proposalService, never()).cancelProposal(any());
        verifyNoInteractions(jobService, chatRoomService, studentService);
    }

    @Test
    @DisplayName("본인이 이미 거절한 제안의 거절 재요청은 결제 주문을 확인하지 않고 최초 거절 시각을 그대로 둔 채 거절 상태를 반환한다")
    void rejectIsIdempotentForOwner() {
        givenRejectingOwner(null);
        LocalDateTime firstRejectedAt = LocalDateTime.of(2026, 10, 1, 1, 0);
        Proposal proposal = Proposal.builder().id(31L).ownerProfileId(5L).status(ProposalStatus.REJECTED)
                .rejectedBy(ProposalRejectedBy.OWNER).rejectedAt(firstRejectedAt).build();
        when(proposalService.getRejectableProposalForUpdate(31L, 5L, null)).thenReturn(proposal);

        ProposalRejectResult result = proposalFacade.rejectProposal(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getRejectedAt()).isEqualTo(firstRejectedAt);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제 대기 주문이 있는 결제 전 제안의 거절은 PROPOSAL_409_REJECT_PAYMENT_PENDING으로 거부하고 제안을 바꾸지 않는다")
    void rejectsRejectWithPendingPayment() {
        givenRejectingOwner(null);
        Proposal proposal = Proposal.builder().id(31L).ownerProfileId(5L).status(ProposalStatus.PENDING)
                .likeCount(2).build();
        when(proposalService.getRejectableProposalForUpdate(31L, 5L, null)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.of(mock(Payment.class)));

        assertError(() -> proposalFacade.rejectProposal(USERNAME, 31L), ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposal.getRejectedBy()).isNull();
        assertThat(proposal.getRejectedAt()).isNull();
    }

    @Test
    @DisplayName("데모 사장님의 제안 거절은 자기 데모 세션 ID를 격리 범위로 전달한다")
    void passesDemoSessionForReject() {
        givenRejectingOwner(DEMO_SESSION_A);
        Proposal proposal = Proposal.builder().id(31L).ownerProfileId(5L).status(ProposalStatus.PENDING)
                .likeCount(0).demoSessionId(DEMO_SESSION_A).build();
        when(proposalService.getRejectableProposalForUpdate(31L, 5L, DEMO_SESSION_A)).thenReturn(proposal);
        when(paymentService.findPendingProposalPayment(31L)).thenReturn(Optional.empty());

        assertThat(proposalFacade.rejectProposal(USERNAME, 31L).getStatus()).isEqualTo(ProposalStatus.REJECTED);

        verify(proposalService).getRejectableProposalForUpdate(31L, 5L, DEMO_SESSION_A);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = UserRole.class, names = { "STUDENT", "PENDING" })
    @DisplayName("학생과 가입 대기 사용자의 제안 거절은 PROPOSAL_403_REJECT로 거부하고 사장님 프로필과 제안을 조회하지 않는다")
    void rejectsNonOwnerForReject(UserRole role) {
        givenUser(role);

        assertError(() -> proposalFacade.rejectProposal(USERNAME, 31L), ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        verifyNoInteractions(ownerService, proposalService, paymentService);
    }

    @Test
    @DisplayName("사장님 프로필이 없는 사장님 역할 사용자의 제안 거절은 PROPOSAL_403_REJECT로 거부하고 제안을 잠그지 않는다")
    void rejectsOwnerWithoutProfileForReject() {
        givenUser(UserRole.OWNER);
        when(ownerService.findOwnerProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> proposalFacade.rejectProposal(USERNAME, 31L), ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        verifyNoInteractions(proposalService, paymentService);
    }

    @Test
    @DisplayName("잠긴 사용자의 제안 거절은 UNAUTHORIZED로 거부하고 사장님 프로필과 제안을 조회하지 않는다")
    void rejectsInactiveUserForReject() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> proposalFacade.rejectProposal(USERNAME, 31L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(ownerService, proposalService, paymentService);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ErrorCode.class,
            names = { "PROPOSAL_NOT_FOUND", "PROPOSAL_REJECT_FORBIDDEN", "PROPOSAL_REJECT_NOT_AVAILABLE" })
    @DisplayName("제안을 잠가 확인한 거절의 404·403·409는 그대로 전달하고 결제 주문을 확인하지 않는다")
    void propagatesRejectValidationErrors(ErrorCode errorCode) {
        givenRejectingOwner(null);
        when(proposalService.getRejectableProposalForUpdate(31L, 5L, null))
                .thenThrow(new BusinessException(errorCode));

        assertError(() -> proposalFacade.rejectProposal(USERNAME, 31L), errorCode);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("거절된 제안의 상세와 보낸 제안 목록은 저장된 거절 주체와 원본 거절 시각을 그대로 넘기고 예상 마감일은 내리지 않는다")
    void returnsRejectionDetailsInDetailAndList() {
        LocalDateTime rejectedAt = LocalDateTime.of(2026, 10, 5, 15, 30);
        Proposal rejected = Proposal.builder().id(31L).studentProfileId(7L).ownerProfileId(5L).title("제안")
                .draftDays(3).finalDays(7).referenceImageUrls(List.of()).likeCount(4)
                .status(ProposalStatus.REJECTED).rejectedBy(ProposalRejectedBy.OWNER).rejectedAt(rejectedAt).build();
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(STUDENT_USER_ID).username(USERNAME).role(UserRole.STUDENT).build());
        when(proposalService.getProposalDetail(31L)).thenReturn(ProposalDetailData.of(rejected, List.of()));
        when(ownerService.getOwnerProfileById(5L))
                .thenReturn(Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가게 이름").build());
        givenProposingStudent();
        when(specialtyCategoryService.getSpecialtyDetails(any())).thenReturn(Map.of());
        when(studentService.findStudentProfileByUserId(STUDENT_USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
        when(proposalService.getMyProposals(any(GetMyProposalsCommand.class)))
                .thenReturn(List.of(ExploreProposalData.of(rejected, List.of())));
        when(ownerService.getOwnerProfilesByIds(Set.of(5L)))
                .thenReturn(Map.of(5L, Owner.builder().id(5L).storeName("가게 이름").build()));

        ProposalDetailResult detail = proposalFacade.getProposalDetail(USERNAME, 31L);
        assertThat(detail.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(detail.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(detail.getRejectedAt()).isEqualTo(rejectedAt);
        assertThat(detail.getLikeCount()).isEqualTo(4);
        assertThat(detail.getEstimatedDraftDeadline()).isNull();
        assertThat(detail.getEstimatedFinalDeadline()).isNull();

        assertThat(proposalFacade.getMyProposals(USERNAME).getProposals())
                .extracting(p -> p.getStatus(), p -> p.getRejectedBy(), p -> p.getRejectedAt())
                .containsExactly(tuple(ProposalStatus.REJECTED, ProposalRejectedBy.OWNER, rejectedAt));
    }

    private void givenRejectingOwner(String demoSessionId) {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder()
                .id(USER_ID).username(USERNAME).role(UserRole.OWNER).demoSessionId(demoSessionId).build());
        when(ownerService.findOwnerProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Owner.builder().id(5L).userId(USER_ID).build()));
    }

    @Test
    @DisplayName("취소된 제안 상세는 제안한 학생 본인에게 취소 상태로 반환하고 예상 마감일과 의뢰·확정 조건은 내리지 않는다")
    void returnsCancelledProposalDetailToAuthor() {
        givenProposalDetail(ProposalStatus.CANCELLED, STUDENT_USER_ID, UserRole.STUDENT);

        ProposalDetailResult result = proposalFacade.getProposalDetail(USERNAME, 31L);

        assertThat(result.getProposalId()).isEqualTo(31L);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(result.getEstimatedDraftDeadline()).isNull();
        assertThat(result.getEstimatedFinalDeadline()).isNull();
        assertThat(result.getJobId()).isNull();
        assertThat(result.getAgreement()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.CsvSource({
            "01K58M6PJV8VAJMXHBHJ2PNB5F, OWNER",
            "01K58M6PJV8VAJMXHBHJ2PNB5E, STUDENT",
            "01K58M6PJV8VAJMXHBHJ2PNB5C, PENDING" })
    @DisplayName("취소된 제안 상세는 받은 사장님을 포함한 작성자 아닌 사용자에게 PROPOSAL_404로 거부하고 매장·통계·공감 기록을 조회하지 않는다")
    void hidesCancelledProposalDetailFromOthers(String viewerId, UserRole role) {
        givenProposalDetail(ProposalStatus.CANCELLED, viewerId, role);

        assertError(() -> proposalFacade.getProposalDetail(USERNAME, 31L), ErrorCode.PROPOSAL_NOT_FOUND);
        verifyNoInteractions(ownerService, reviewService, jobService, paymentService, specialtyCategoryService);
        verify(userService, never()).getUser(anyString());
        verify(proposalService, never()).getLikedProposalIds(anyLong(), any());
    }

    @Test
    @DisplayName("제안한 학생이 의뢰서를 거절하면 제안을 거절로, 의뢰를 취소로 넘기고 전액 환불 기록을 반환하며 채팅방은 만들지 않는다")
    void declinesProposalJob() {
        givenStartingStudent();
        Proposal proposal = detailProposal(ProposalStatus.AWAITING_START);
        LocalDateTime declinedAt = LocalDateTime.of(2026, 10, 6, 12, 0);
        Job declined = Job.builder().id(42L).ownerProfileId(5L).proposalId(31L).status(JobStatus.CANCELLED)
                .completedAt(declinedAt).build();
        givenJobOwner();
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenReturn(31L);
        when(proposalService.getDeclinableProposalForUpdate(31L, 7L)).thenReturn(proposal);
        when(jobService.declineJob(42L, 31L, 7L)).thenReturn(declined);
        when(paymentService.refundOnDecline(42L, 31L, OWNER_USER_ID)).thenReturn(
                new RefundedPaymentData(100_000L, 0L, 100_000L, Instant.parse("2026-10-06T03:00:00Z")));

        ProposalJobDeclineResult result = proposalFacade.declineProposalJob(USERNAME, 42L);

        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getJobStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(result.getProposalStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        // 거절 주체와 시계의 UTC 시각을 같은 트랜잭션에서 함께 남긴다
        assertThat(proposal.getRejectedBy()).isEqualTo(ProposalRejectedBy.STUDENT);
        assertThat(proposal.getRejectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 4, 23, 0));
        assertThat(result.getPaidAmount()).isEqualTo(100_000L);
        assertThat(result.getStudentCompensationAmount()).isZero();
        assertThat(result.getRefundAmount()).isEqualTo(100_000L);
        assertThat(result.getDeclinedAt()).isEqualTo(declinedAt);
        // 잠금 순서: 제안 ID를 잠금 없이 읽은 뒤 제안 → 의뢰 → 결제. 결제는 의뢰한 사장님의 사용자 ID로 확인한다
        InOrder order = inOrder(jobService, proposalService, paymentService);
        order.verify(jobService).getDeclinableProposalId(42L, 7L, null);
        order.verify(proposalService).getDeclinableProposalForUpdate(31L, 7L);
        order.verify(jobService).declineJob(42L, 31L, 7L);
        order.verify(paymentService).refundOnDecline(42L, 31L, OWNER_USER_ID);
        verifyNoInteractions(chatRoomService);
    }

    @Test
    @DisplayName("데모 학생의 의뢰서 거절은 자기 데모 세션 ID를 격리 범위로 전달한다")
    void passesDemoSessionForDecline() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).username(USERNAME)
                .role(UserRole.STUDENT).demoSessionId(DEMO_SESSION_A).build());
        givenStudentProfile();
        when(jobService.getDeclinableProposalId(42L, 7L, DEMO_SESSION_A))
                .thenThrow(new BusinessException(ErrorCode.JOB_NOT_FOUND));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.JOB_NOT_FOUND);
        verify(jobService).getDeclinableProposalId(42L, 7L, DEMO_SESSION_A);
        verifyNoInteractions(proposalService, paymentService);
    }

    @Test
    @DisplayName("학생이 아니거나 학생 프로필이 없으면 JOB_DECLINE_403으로 거부하고 의뢰·제안·결제를 조회하지 않는다")
    void rejectsNonStudentForDecline() {
        for (UserRole role : List.of(UserRole.OWNER, UserRole.PENDING)) {
            givenUser(role);
            assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.JOB_DECLINE_FORBIDDEN);
        }

        givenUser(UserRole.STUDENT);
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());
        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.JOB_DECLINE_FORBIDDEN);

        verifyNoInteractions(jobService, proposalService, paymentService, chatRoomService);
    }

    @Test
    @DisplayName("잠긴 사용자의 의뢰서 거절은 UNAUTHORIZED로 거부하고 학생 프로필과 의뢰를 조회하지 않는다")
    void rejectsInactiveUserForDecline() {
        when(userService.getActiveUser(USERNAME)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(studentService, jobService, proposalService, paymentService);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class,
            names = { "JOB_NOT_FOUND", "JOB_DECLINE_FORBIDDEN", "JOB_DECLINE_NOT_AVAILABLE" })
    @DisplayName("없는 의뢰·담당 학생 아님·일반 의뢰이면 제안을 잠그지 않고 의뢰와 결제를 바꾸지 않는다")
    void doesNotLockWhenJobIsNotDeclinable(ErrorCode errorCode) {
        givenStartingStudent();
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenThrow(new BusinessException(errorCode));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), errorCode);
        verifyNoInteractions(proposalService, paymentService);
        verify(jobService, never()).declineJob(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 시작했거나 거절한 제안이면 JOB_DECLINE_409로 거부하고 의뢰를 바꾸거나 환불을 다시 처리하지 않는다")
    void doesNotRefundAgainWhenProposalIsNotAwaiting() {
        givenStartingStudent();
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenReturn(31L);
        when(proposalService.getDeclinableProposalForUpdate(31L, 7L))
                .thenThrow(new BusinessException(ErrorCode.JOB_DECLINE_NOT_AVAILABLE));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        verify(jobService, never()).declineJob(anyLong(), anyLong(), anyLong());
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("잠근 뒤 의뢰를 거절할 수 없으면 제안을 거절로 바꾸지 않고 환불하지 않는다")
    void keepsProposalWhenDeclineFailsUnderLock() {
        givenStartingStudent();
        Proposal proposal = detailProposal(ProposalStatus.AWAITING_START);
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenReturn(31L);
        when(proposalService.getDeclinableProposalForUpdate(31L, 7L)).thenReturn(proposal);
        when(jobService.declineJob(42L, 31L, 7L))
                .thenThrow(new BusinessException(ErrorCode.JOB_DECLINE_NOT_AVAILABLE));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("결제 완료 주문이 없거나 참조가 맞지 않는 환불 오류는 500으로 그대로 전달해 트랜잭션을 되돌린다")
    void propagatesRefundDataErrorForDecline() {
        givenStartingStudent();
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenReturn(31L);
        when(proposalService.getDeclinableProposalForUpdate(31L, 7L))
                .thenReturn(detailProposal(ProposalStatus.AWAITING_START));
        when(jobService.declineJob(42L, 31L, 7L)).thenReturn(
                Job.builder().id(42L).ownerProfileId(5L).proposalId(31L).status(JobStatus.CANCELLED).build());
        givenJobOwner();
        when(paymentService.refundOnDecline(42L, 31L, OWNER_USER_ID))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("의뢰한 사장님 프로필을 찾을 수 없으면 500으로 그대로 전달하고 환불하지 않는다")
    void propagatesMissingJobOwnerForDecline() {
        givenStartingStudent();
        when(jobService.getDeclinableProposalId(42L, 7L, null)).thenReturn(31L);
        when(proposalService.getDeclinableProposalForUpdate(31L, 7L))
                .thenReturn(detailProposal(ProposalStatus.AWAITING_START));
        when(jobService.declineJob(42L, 31L, 7L)).thenReturn(
                Job.builder().id(42L).ownerProfileId(5L).proposalId(31L).status(JobStatus.CANCELLED).build());
        when(ownerService.getOwnerProfileById(5L)).thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertError(() -> proposalFacade.declineProposalJob(USERNAME, 42L), ErrorCode.INTERNAL_SERVER_ERROR);
        verifyNoInteractions(paymentService);
    }

    // 의뢰 42번을 의뢰한 사장님 프로필(5번)과 그 사용자
    private void givenJobOwner() {
        when(ownerService.getOwnerProfileById(5L))
                .thenReturn(Owner.builder().id(5L).userId(OWNER_USER_ID).build());
    }
}
