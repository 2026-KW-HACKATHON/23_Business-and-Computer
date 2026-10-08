package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetLatestJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 수정 요청 내용의 JSONB 저장과 잠금 전 프로젝션 조회, 최신 차수 조회는 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("수정 요청 내용 저장과 사장님·학생 최신 제출물 PostgreSQL 조회")
class JobSubmissionRevisionRequestPostgresTest {

    private static final long OWNER_PROFILE_ID = 988_301L;
    private static final long STUDENT_PROFILE_ID = 988_302L;
    private static final Instant NOW = Instant.parse("2031-03-01T03:15:30.123456Z");
    private static final String MESSAGE = "로고를 조금 더 크게 해주세요.";
    private static final List<String> IMAGES = List.of(
            "https://images.example.com/images/job/owner/b.png", "https://images.example.com/images/job/owner/a.png");

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;

    @Autowired
    private EntityManager entityManager;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
                mock(JobApplicationRepository.class), jobSubmissionRepository, Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("새 제출물은 수정 요청 참고 사진이 빈 JSONB 배열로 저장되고 재조회된다")
    void savesNewSubmissionWithEmptyImages() {
        Long jobId = job(JobStatus.MATCHED, 2);
        Long id = submission(jobId, 0, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        JobSubmission found = jobSubmissionRepository.findById(id).orElseThrow();
        assertThat(found.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(found.getReviewComment()).isNull();
        assertThat(found.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("수정을 요청하면 상태·요청 내용·참고 사진 순서·요청 시각이 함께 저장되고 제출 메시지와 파일은 보존된다")
    void persistsRevisionRequestTogether() {
        Long jobId = job(JobStatus.MATCHED, 2);
        Long id = submission(jobId, 0, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        jobService.validateRevisionRequestable(jobId, id, OWNER_PROFILE_ID);
        jobService.requestRevision(command(jobId, id, IMAGES), OWNER_PROFILE_ID);
        entityManager.flush();
        entityManager.clear();

        JobSubmission found = jobSubmissionRepository.findById(id).orElseThrow();
        assertThat(found.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(found.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(found.getRevisionReferenceImageUrls()).containsExactlyElementsOf(IMAGES);
        assertThat(found.getReviewedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(found.getMessage()).isEqualTo("제출 메시지");
        assertThat(found.getFileUrls()).containsExactly("https://example.com/b.png", "https://example.com/a.pdf");
    }

    @Test
    @DisplayName("거부된 수정 요청은 상태·요청 내용·참고 사진·요청 시각 중 어느 것도 저장하지 않는다")
    void persistsNothingWhenRejected() {
        Long jobId = job(JobStatus.MATCHED, 0);
        Long id = submission(jobId, 0, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        assertError(() -> jobService.validateRevisionRequestable(jobId, id, OWNER_PROFILE_ID),
                ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        assertError(() -> jobService.requestRevision(command(jobId, id, IMAGES), OWNER_PROFILE_ID),
                ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        entityManager.flush();
        entityManager.clear();

        JobSubmission found = jobSubmissionRepository.findById(id).orElseThrow();
        assertThat(found.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(found.getReviewComment()).isNull();
        assertThat(found.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(found.getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("잠금 전 사전 확인은 다른 사장님의 의뢰·다른 의뢰의 제출물·이미 검토된 제출물을 실제 조회로 거부한다")
    void validatesAgainstRealRows() {
        Long jobId = job(JobStatus.MATCHED, 2);
        Long otherJobId = job(JobStatus.MATCHED, 2);
        Long reviewed = submission(jobId, 0, JobSubmissionReviewStatus.REVISION_REQUESTED);
        Long otherJobSubmission = submission(otherJobId, 0, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        assertError(() -> jobService.validateRevisionRequestable(jobId, reviewed, OWNER_PROFILE_ID + 1),
                ErrorCode.JOB_NOT_FOUND);
        assertError(() -> jobService.validateRevisionRequestable(jobId, otherJobSubmission, OWNER_PROFILE_ID),
                ErrorCode.JOB_SUBMISSION_NOT_FOUND);
        assertError(() -> jobService.validateRevisionRequestable(jobId, reviewed, OWNER_PROFILE_ID),
                ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
    }

    @Test
    @DisplayName("최신 제출물 조회는 수정 번호가 가장 큰 제출물을 고르고 이전 차수의 수정 요청 내용은 그 제출물에만 남는다")
    void returnsHighestRevisionAsLatest() {
        Long jobId = job(JobStatus.MATCHED, 2);
        Long draft = submission(jobId, 0, JobSubmissionReviewStatus.PENDING);
        jobService.requestRevision(command(jobId, draft, IMAGES), OWNER_PROFILE_ID);
        entityManager.flush();
        entityManager.clear();

        JobSubmission requested = jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.of(jobId, STUDENT_PROFILE_ID));
        assertThat(requested.getId()).isEqualTo(draft);
        assertThat(requested.getReviewComment()).isEqualTo(MESSAGE);

        Long revision = submission(jobId, 1, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        JobSubmission latest = jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.of(jobId, STUDENT_PROFILE_ID));
        assertThat(latest.getId()).isEqualTo(revision);
        assertThat(latest.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(latest.getReviewComment()).isNull();
        assertThat(latest.getRevisionReferenceImageUrls()).isEmpty();
        assertThat(latest.getCreatedAt()).isNotNull();
        assertThat(jobSubmissionRepository.findById(draft).orElseThrow().getRevisionReferenceImageUrls())
                .containsExactlyElementsOf(IMAGES);
    }

    @Test
    @DisplayName("최신 제출물 조회는 다른 학생의 의뢰를 JOB_404, 제출물이 없는 본인 의뢰를 JOB_SUBMISSION_404_LATEST로 거부한다")
    void rejectsLatestOfOtherStudentOrEmptyJob() {
        Long jobId = job(JobStatus.MATCHED, 2);
        entityManager.clear();

        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.of(jobId, STUDENT_PROFILE_ID + 1)), ErrorCode.JOB_NOT_FOUND);
        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.of(jobId, STUDENT_PROFILE_ID)),
                ErrorCode.JOB_SUBMISSION_LATEST_NOT_FOUND);
    }

    @Test
    @DisplayName("의뢰한 사장님의 최신 제출물 조회는 수정 요청 내용이 담긴 초안을, 재제출 뒤에는 수정 요청이 없는 새 수정안을 고른다")
    void returnsHighestRevisionAsLatestToOwner() {
        Long jobId = job(JobStatus.MATCHED, 2);
        Long draft = submission(jobId, 0, JobSubmissionReviewStatus.PENDING);
        jobService.requestRevision(command(jobId, draft, IMAGES), OWNER_PROFILE_ID);
        entityManager.flush();
        entityManager.clear();

        JobSubmission requested = jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.ofOwner(jobId, OWNER_PROFILE_ID));
        assertThat(requested.getId()).isEqualTo(draft);
        assertThat(requested.getRevisionNumber()).isZero();
        assertThat(requested.getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(requested.getReviewComment()).isEqualTo(MESSAGE);
        assertThat(requested.getRevisionReferenceImageUrls()).containsExactlyElementsOf(IMAGES);
        assertThat(requested.getReviewedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        Long revision = submission(jobId, 1, JobSubmissionReviewStatus.PENDING);
        entityManager.clear();

        JobSubmission latest = jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.ofOwner(jobId, OWNER_PROFILE_ID));
        assertThat(latest.getId()).isEqualTo(revision);
        assertThat(latest.getRevisionNumber()).isEqualTo(1);
        assertThat(latest.getReviewComment()).isNull();
        assertThat(latest.getRevisionReferenceImageUrls()).isEmpty();
    }

    @Test
    @DisplayName("사장님 최신 제출물 조회는 다른 사장님과 담당 학생 ID를 쓴 사장님을 JOB_404, 제출물이 없는 본인 의뢰를 JOB_SUBMISSION_404_LATEST로 거부한다")
    void rejectsLatestOfOtherOwnerOrEmptyJob() {
        Long jobId = job(JobStatus.MATCHED, 2);
        entityManager.clear();

        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.ofOwner(jobId, OWNER_PROFILE_ID + 2)), ErrorCode.JOB_NOT_FOUND);
        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.ofOwner(jobId, STUDENT_PROFILE_ID)), ErrorCode.JOB_NOT_FOUND);
        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.of(jobId, OWNER_PROFILE_ID)), ErrorCode.JOB_NOT_FOUND);
        assertError(() -> jobService.getLatestSubmission(
                GetLatestJobSubmissionCommand.ofOwner(jobId, OWNER_PROFILE_ID)),
                ErrorCode.JOB_SUBMISSION_LATEST_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "'{}'::jsonb", "'\"url\"'::jsonb"})
    @DisplayName("DB는 NULL과 배열이 아닌 수정 요청 참고 사진 JSON을 거부한다")
    void rejectsNonArrayImages(String sqlValue) {
        Long id = submission(job(JobStatus.MATCHED, 2), 0, JobSubmissionReviewStatus.PENDING);

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "update job_submissions set revision_reference_image_urls = " + sqlValue + " where id = :id")
                .setParameter("id", id).executeUpdate()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("파일 크기를 넘기지 않은 제출물은 빈 JSONB 객체로, 넘긴 제출물은 URL별 바이트 크기로 저장되고 재조회된다")
    void savesAndReloadsFileSizes() {
        Long legacyId = submission(job(JobStatus.MATCHED, 2), 0, JobSubmissionReviewStatus.PENDING);
        Map<String, Long> fileSizes = Map.of("https://example.com/b.png", 2048L, "https://example.com/a.pdf", 5_368_709_120L);
        Long sizedId = jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                job(JobStatus.MATCHED, 2), JobSubmissionType.DRAFT, 0,
                List.of("https://example.com/b.png", "https://example.com/a.pdf"), fileSizes, "제출 메시지")).getId();
        entityManager.clear();

        assertThat(jobSubmissionRepository.findById(legacyId).orElseThrow().getFileSizes()).isEmpty();
        JobSubmission sized = jobSubmissionRepository.findById(sizedId).orElseThrow();
        assertThat(sized.getFileSizes()).containsExactlyInAnyOrderEntriesOf(fileSizes);
        assertThat(sized.getFileUrls()).containsExactly("https://example.com/b.png", "https://example.com/a.pdf");
        assertThat(entityManager.createNativeQuery(
                        "select jsonb_typeof(file_sizes) from job_submissions where id = :id")
                .setParameter("id", legacyId).getSingleResult()).isEqualTo("object");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "'[]'::jsonb", "'1'::jsonb"})
    @DisplayName("DB는 NULL과 객체가 아닌 파일 크기 JSON을 거부한다")
    void rejectsNonObjectFileSizes(String sqlValue) {
        Long id = submission(job(JobStatus.MATCHED, 2), 0, JobSubmissionReviewStatus.PENDING);

        assertThatThrownBy(() -> entityManager.createNativeQuery(
                        "update job_submissions set file_sizes = " + sqlValue + " where id = :id")
                .setParameter("id", id).executeUpdate()).isInstanceOf(Exception.class);
    }

    private Long job(JobStatus status, int revisionCount) {
        return jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(OWNER_PROFILE_ID)
                .title("수정 요청 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(revisionCount)
                .status(status)
                .selectedStudentProfileId(STUDENT_PROFILE_ID)
                .build()).getId();
    }

    private Long submission(Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        return jobSubmissionRepository.saveAndFlush(JobSubmission.builder()
                .jobId(jobId)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/b.png", "https://example.com/a.pdf"))
                .message("제출 메시지")
                .reviewStatus(reviewStatus)
                .build()).getId();
    }

    private RequestJobSubmissionRevisionCommand command(Long jobId, Long submissionId, List<String> images) {
        return RequestJobSubmissionRevisionCommand.of("KAKAO_12345", jobId, submissionId, MESSAGE, images);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
