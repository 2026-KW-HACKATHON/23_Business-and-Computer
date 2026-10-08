package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.gakkum.backend.application.job.dto.JobSubmissionResponse;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient.PresignedFileUpload;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.PrepareSubmissionFileUploadCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;
import com.gakkum.backend.domain.job.dto.JobSubmissionFileType;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

class JobFacadeSubmissionTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5F";
    private static final String FILE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/f/a.pdf";
    private static final String KEY = "job-submissions/42/7/f/a.pdf";
    private static final String SECOND_FILE_URL =
            "https://bucket.s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/g/b.png";
    private static final String SECOND_KEY = "job-submissions/42/7/g/b.png";

    private final UserService userService = mock(UserService.class);
    private final JobService jobService = mock(JobService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final JobSubmissionFileStorageClient storageClient = mock(JobSubmissionFileStorageClient.class);
    private final ChatAttachmentPolicy chatAttachmentPolicy = mock(ChatAttachmentPolicy.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final JobFacade jobFacade = new JobFacade(
            userService, ownerService, jobService, mock(SpecialtyCategoryService.class),
            mock(SpecialtyService.class), studentService,
            storageClient, chatAttachmentPolicy, mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), eventPublisher,
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));

    @Test
    @DisplayName("업로드 준비는 채팅 첨부 규칙으로 검증한 형식으로 서명하고 공개 파일 URL을 반환한다")
    void preparesUpload() {
        givenStudent(UserRole.STUDENT);
        when(chatAttachmentPolicy.validate(ChatMessageType.FILE, "draft.pdf", "Application/PDF", 1048576L))
                .thenReturn("application/pdf");
        when(storageClient.newKey(42L, 7L, "draft.pdf")).thenReturn(KEY);
        when(storageClient.presignUpload(KEY, "application/pdf", 1048576L)).thenReturn(new PresignedFileUpload(
                "https://upload", Map.of("content-type", "application/pdf"), Instant.parse("2026-09-27T12:10:00Z"),
                FILE_URL));

        PrepareSubmissionFileUploadResult result = jobFacade.prepareSubmissionFileUpload(
                PrepareSubmissionFileUploadCommand.of(USERNAME, 42L, JobSubmissionFileType.FILE, "draft.pdf",
                        "Application/PDF", 1048576L));

        verify(jobService).getSubmittableJob(42L, 7L);
        assertThat(result.getUploadUrl()).isEqualTo("https://upload");
        assertThat(result.getUploadHeaders()).containsEntry("content-type", "application/pdf");
        assertThat(result.getUploadUrlExpiresAt()).isNotNull();
        assertThat(result.getFileUrl()).isEqualTo(FILE_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = { "UTC", "Asia/Seoul" })
    @DisplayName("업로드 URL 만료 시각은 JVM 기본 시간대와 무관하게 UTC 시각으로 넘기고 응답은 한국 시각으로 내린다")
    void expiresAtIgnoresDefaultTimeZone(String defaultZone) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(defaultZone));
        try {
            givenStudent(UserRole.STUDENT);
            when(chatAttachmentPolicy.validate(ChatMessageType.FILE, "draft.pdf", "application/pdf", 1048576L))
                    .thenReturn("application/pdf");
            when(storageClient.newKey(42L, 7L, "draft.pdf")).thenReturn(KEY);
            when(storageClient.presignUpload(KEY, "application/pdf", 1048576L)).thenReturn(new PresignedFileUpload(
                    "https://upload", Map.of("content-type", "application/pdf"),
                    Instant.parse("2026-09-27T12:10:00Z"), FILE_URL));

            PrepareSubmissionFileUploadResult result = jobFacade.prepareSubmissionFileUpload(
                    PrepareSubmissionFileUploadCommand.of(USERNAME, 42L, JobSubmissionFileType.FILE, "draft.pdf",
                            "application/pdf", 1048576L));

            assertThat(result.getUploadUrlExpiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 12, 10));
            assertThat(JobSubmissionResponse.PrepareFileUpload.from(result).getUploadUrlExpiresAt())
                    .hasToString("2026-09-27T21:10+09:00");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("이미지 업로드 준비는 채팅 IMAGE 규칙을 적용하고, 규칙 위반이면 URL을 발급하지 않는다")
    void rejectsImageViolatingPolicy() {
        givenStudent(UserRole.STUDENT);
        when(chatAttachmentPolicy.validate(ChatMessageType.IMAGE, "a.bmp", "image/bmp", 10L))
                .thenThrow(new BusinessException(ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED));

        assertError(() -> jobFacade.prepareSubmissionFileUpload(PrepareSubmissionFileUploadCommand.of(
                USERNAME, 42L, JobSubmissionFileType.IMAGE, "a.bmp", "image/bmp", 10L)),
                ErrorCode.CHAT_UPLOAD_TYPE_NOT_ALLOWED);
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("학생이 아닌 사용자는 JOB_SUBMISSION_403으로 거부하고 의뢰를 조회하지 않는다")
    void rejectsNonStudent() {
        givenStudent(UserRole.OWNER);

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL))), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        assertError(() -> jobFacade.prepareSubmissionFileUpload(PrepareSubmissionFileUploadCommand.of(
                USERNAME, 42L, JobSubmissionFileType.FILE, "a.pdf", "application/pdf", 10L)),
                ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        verifyNoInteractions(jobService, storageClient);
    }

    @Test
    @DisplayName("학생 역할이어도 학생 프로필이 없으면 JOB_SUBMISSION_403으로 거부한다")
    void rejectsStudentWithoutProfile() {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(UserRole.STUDENT).build());
        when(studentService.findStudentProfileByUserId(USER_ID)).thenReturn(Optional.empty());

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL))), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        verifyNoInteractions(jobService);
    }

    @Test
    @DisplayName("URL과 업로드를 확인한 뒤 초안을 저장하고 제출 결과를 반환하며 의뢰한 사장님에게 초안 도착을 알린다")
    void submitsDraft() {
        givenStudent(UserRole.STUDENT);
        givenJobOwner();
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findKey(SECOND_FILE_URL, 42L, 7L)).thenReturn(Optional.of(SECOND_KEY));
        when(storageClient.findSize(KEY)).thenReturn(Optional.of(1048576L));
        when(storageClient.findSize(SECOND_KEY)).thenReturn(Optional.of(2048L));
        CreateJobSubmissionCommand command = command(List.of(FILE_URL, SECOND_FILE_URL));
        when(jobService.submitDraft(command, 7L, Map.of(FILE_URL, 1048576L, SECOND_FILE_URL, 2048L))).thenReturn(JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(JobSubmissionType.DRAFT)
                .revisionNumber(0)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build());

        JobSubmissionCreateResult result = jobFacade.submitDraft(command);

        verify(jobService).validateDraftSubmittable(42L, 7L);
        assertThat(result.getSubmissionId()).isEqualTo(81L);
        assertThat(result.getJobId()).isEqualTo(42L);
        assertThat(result.getSubmissionType()).isEqualTo("DRAFT");
        assertThat(result.getRevisionNumber()).isZero();
        assertThat(result.getReviewStatus()).isEqualTo("PENDING");
        assertThat(publishedEvents()).containsExactly(NotificationEventFactory.jobDraftSubmitted(
                OWNER_USER_ID, 81L, 42L, "메뉴판 디자인", "김학생"));
    }

    @Test
    @DisplayName("발급하지 않은 URL이 하나라도 있으면 JOB_SUBMISSION_400_FILE_URL로 거부하고 S3를 조회하지 않는다")
    void rejectsForeignUrl() {
        givenStudent(UserRole.STUDENT);
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findKey("https://evil.example.com/a.pdf", 42L, 7L)).thenReturn(Optional.empty());

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL, "https://evil.example.com/a.pdf"))),
                ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID);
        verify(storageClient, never()).findSize(anyString());
        verify(jobService, never()).submitDraft(any(), anyLong(), any());
    }

    @Test
    @DisplayName("업로드되지 않은 파일이 있으면 JOB_SUBMISSION_409_FILE_NOT_UPLOADED로 거부하고 저장하지 않는다")
    void rejectsNotUploadedFile() {
        givenStudent(UserRole.STUDENT);
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findSize(KEY)).thenReturn(Optional.empty());

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL))),
                ErrorCode.JOB_SUBMISSION_FILE_NOT_UPLOADED);
        verify(jobService, never()).submitDraft(any(), anyLong(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("초안 저장이 실패하면 알림을 발행하지 않는다")
    void doesNotNotifyWhenDraftSaveFails() {
        givenStudent(UserRole.STUDENT);
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findSize(KEY)).thenReturn(Optional.of(1048576L));
        when(jobService.submitDraft(any(), anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS));

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL))),
                ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        verifyNoInteractions(eventPublisher, ownerService);
    }

    @Test
    @DisplayName("이미 초안을 냈다면 S3 확인 전에 JOB_SUBMISSION_409_DUPLICATE로 거부한다")
    void rejectsDuplicateBeforeStorageCheck() {
        givenStudent(UserRole.STUDENT);
        doThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS))
                .when(jobService).validateDraftSubmittable(42L, 7L);

        assertError(() -> jobFacade.submitDraft(command(List.of(FILE_URL))),
                ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("수정안도 URL과 업로드를 확인한 뒤 저장하고 REVISION 결과를 반환하며 의뢰한 사장님에게 수정안 도착을 알린다")
    void submitsRevision() {
        givenStudent(UserRole.STUDENT);
        givenJobOwner();
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findKey(SECOND_FILE_URL, 42L, 7L)).thenReturn(Optional.of(SECOND_KEY));
        when(storageClient.findSize(KEY)).thenReturn(Optional.of(1048576L));
        when(storageClient.findSize(SECOND_KEY)).thenReturn(Optional.of(2048L));
        CreateJobSubmissionCommand command = command(List.of(FILE_URL, SECOND_FILE_URL));
        when(jobService.submitRevision(command, 7L, Map.of(FILE_URL, 1048576L, SECOND_FILE_URL, 2048L))).thenReturn(JobSubmission.builder()
                .id(82L)
                .jobId(42L)
                .submissionType(JobSubmissionType.REVISION)
                .revisionNumber(1)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build());

        JobSubmissionCreateResult result = jobFacade.submitRevision(command);

        verify(jobService).validateRevisionSubmittable(42L, 7L);
        assertThat(result.getSubmissionId()).isEqualTo(82L);
        assertThat(result.getSubmissionType()).isEqualTo("REVISION");
        assertThat(result.getRevisionNumber()).isEqualTo(1);
        assertThat(result.getReviewStatus()).isEqualTo("PENDING");
        assertThat(publishedEvents()).containsExactly(NotificationEventFactory.jobRevisionSubmitted(
                OWNER_USER_ID, 82L, 42L, "메뉴판 디자인", "김학생"));
    }

    @Test
    @DisplayName("수정안을 여러 차수 제출하면 차수마다 서로 다른 이벤트로 알린다")
    void notifiesEachRevisionSeparately() {
        givenStudent(UserRole.STUDENT);
        givenJobOwner();
        when(storageClient.findKey(FILE_URL, 42L, 7L)).thenReturn(Optional.of(KEY));
        when(storageClient.findSize(KEY)).thenReturn(Optional.of(1048576L));
        CreateJobSubmissionCommand command = command(List.of(FILE_URL));
        when(jobService.submitRevision(command, 7L, Map.of(FILE_URL, 1048576L)))
                .thenReturn(revision(82L, 1))
                .thenReturn(revision(83L, 2));

        jobFacade.submitRevision(command);
        jobFacade.submitRevision(command);

        List<NotificationEvent> events = publishedEvents();
        assertThat(events).containsExactly(
                NotificationEventFactory.jobRevisionSubmitted(OWNER_USER_ID, 82L, 42L, "메뉴판 디자인", "김학생"),
                NotificationEventFactory.jobRevisionSubmitted(OWNER_USER_ID, 83L, 42L, "메뉴판 디자인", "김학생"));
        assertThat(events.get(0).eventId()).isNotEqualTo(events.get(1).eventId());
    }

    @Test
    @DisplayName("수정안에 발급하지 않은 URL이 있으면 JOB_SUBMISSION_400_FILE_URL로 거부하고 저장하지 않는다")
    void rejectsRevisionWithForeignUrl() {
        givenStudent(UserRole.STUDENT);
        when(storageClient.findKey(anyString(), anyLong(), anyLong())).thenReturn(Optional.empty());

        assertError(() -> jobFacade.submitRevision(command(List.of("https://evil.example.com/a.pdf"))),
                ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID);
        verify(storageClient, never()).findSize(anyString());
        verify(jobService, never()).submitRevision(any(), anyLong(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("수정 요청이 없으면 S3 확인 전에 JOB_SUBMISSION_409_REVISION_NOT_REQUESTED로 거부한다")
    void rejectsRevisionNotRequestedBeforeStorageCheck() {
        givenStudent(UserRole.STUDENT);
        doThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED))
                .when(jobService).validateRevisionSubmittable(42L, 7L);

        assertError(() -> jobFacade.submitRevision(command(List.of(FILE_URL))),
                ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
        verifyNoInteractions(storageClient);
    }

    @Test
    @DisplayName("학생이 아닌 사용자의 수정안은 JOB_SUBMISSION_403으로 거부한다")
    void rejectsRevisionFromNonStudent() {
        givenStudent(UserRole.OWNER);

        assertError(() -> jobFacade.submitRevision(command(List.of(FILE_URL))), ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        verifyNoInteractions(jobService, storageClient);
    }

    private void givenStudent(UserRole role) {
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).name("김학생").role(role).build());
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(USER_ID).build()));
    }

    /** 제출 저장 뒤 알림 수신자를 찾을 때 읽는 의뢰와 의뢰한 사장님. */
    private void givenJobOwner() {
        when(jobService.getJobsByIds(List.of(42L))).thenReturn(Map.of(42L,
                Job.builder().id(42L).ownerProfileId(5L).title("메뉴판 디자인").build()));
        when(ownerService.getOwnerProfileById(5L)).thenReturn(
                Owner.builder().id(5L).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
    }

    private JobSubmission revision(Long id, int revisionNumber) {
        return JobSubmission.builder().id(id).jobId(42L).submissionType(JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber).reviewStatus(JobSubmissionReviewStatus.PENDING).build();
    }

    private List<NotificationEvent> publishedEvents() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private CreateJobSubmissionCommand command(List<String> fileUrls) {
        return CreateJobSubmissionCommand.of(USERNAME, 42L, fileUrls, "초안입니다.");
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
