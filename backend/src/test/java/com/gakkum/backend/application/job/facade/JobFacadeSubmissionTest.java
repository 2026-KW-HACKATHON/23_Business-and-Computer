package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

class JobFacadeSubmissionTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
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
    private final JobFacade jobFacade = new JobFacade(
            userService, mock(OwnerService.class), jobService, mock(SpecialtyCategoryService.class),
            mock(SpecialtyService.class), studentService,
            storageClient, chatAttachmentPolicy, mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class));

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
    @DisplayName("URL과 업로드를 확인한 뒤 초안을 저장하고 제출 결과를 반환한다")
    void submitsDraft() {
        givenStudent(UserRole.STUDENT);
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
    @DisplayName("수정안도 URL과 업로드를 확인한 뒤 저장하고 REVISION 결과를 반환한다")
    void submitsRevision() {
        givenStudent(UserRole.STUDENT);
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
                User.builder().id(USER_ID).username(USERNAME).role(role).build());
        when(studentService.findStudentProfileByUserId(USER_ID))
                .thenReturn(Optional.of(Student.builder().id(7L).userId(USER_ID).build()));
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
