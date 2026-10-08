package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.application.job.scheduler.JobAutoCompletionScheduler;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
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

/**
 * 대상 조회 쿼리, 의뢰 행 잠금 뒤 재확인, 의뢰별 트랜잭션의 커밋·롤백과 커밋 후 알림은 실제 PostgreSQL에서만 확인된다.
 * 공용 DB에 테스트 행을 커밋하거나 남의 의뢰를 완료하지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며,
 * 이 테스트가 만든 의뢰와 그 제출물만 지운다. 알림은 Redis 대신 커밋 후 리스너로 받아 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ JobService.class, JobFacade.class, ClockConfig.class,
        JobAutoCompletionPersistenceIntegrationTest.CommittedEvents.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("미확인 제출물 자동 완료 PostgreSQL 통합 (대상 조회·잠금 후 재확인·경합·의뢰별 커밋과 롤백·커밋 후 알림)")
class JobAutoCompletionPersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 986_305L;
    private static final long STUDENT_PROFILE_ID = 986_401L;
    private static final String OWNER_USERNAME = "TEST_AUTO_COMPLETION_OWNER";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2ACOWN";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2ACSTU";
    private static final String JOB_TITLE = "자동 완료 통합 테스트 의뢰";
    private static final LocalDateTime REFERENCE_TIME = LocalDateTime.of(2026, 9, 28, 3, 0);
    private static final LocalDateTime EXPIRED_AT = REFERENCE_TIME.minusHours(168);

    /** 실제로 커밋된 트랜잭션의 알림 이벤트만 모은다. 롤백된 트랜잭션의 이벤트는 받지 않는다. */
    static class CommittedEvents {

        private final List<NotificationEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void record(NotificationEvent event) {
            events.add(event);
        }
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private JobFacade jobFacade;

    @Autowired
    private Clock clock;

    @Autowired
    private CommittedEvents committedEvents;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private JobSubmissionFileStorageClient jobSubmissionFileStorageClient;

    @MockitoBean
    private ChatAttachmentPolicy chatAttachmentPolicy;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private CertificateService certificateService;

    @MockitoBean
    private ProposalService proposalService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ChatRoomService chatRoomService;

    private final List<Long> jobIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Owner owner = Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build();
        when(userService.getActiveUser(OWNER_USERNAME)).thenReturn(
                User.builder().id(OWNER_USER_ID).username(OWNER_USERNAME).role(UserRole.OWNER).build());
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(owner);
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(owner);
        when(studentService.getStudentProfile(STUDENT_PROFILE_ID)).thenReturn(
                Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build());
        when(userService.getUser(STUDENT_USER_ID)).thenReturn(
                User.builder().id(STUDENT_USER_ID).name("김학생").role(UserRole.STUDENT).build());
        committedEvents.events.clear();
    }

    @AfterEach
    void cleanUp() {
        for (Long jobId : jobIds) {
            jdbcTemplate.update("delete from job_submissions where job_id = ?", jobId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    @DisplayName("초안·수정안 모두 제출 후 168시간 직전은 대상이 아니고, 정확히 168시간과 그 이후는 대상이 되어 완료된다")
    void selectsAndCompletesFromExactly168Hours() {
        Long draftBefore = saveJobWithDraft(EXPIRED_AT.plus(1, ChronoUnit.MICROS));
        Long draftExact = saveJobWithDraft(EXPIRED_AT);
        Long draftAfter = saveJobWithDraft(EXPIRED_AT.minusDays(40));
        Long revisionBefore = saveJobWithRevision(EXPIRED_AT.minusDays(9), EXPIRED_AT.plus(1, ChronoUnit.MICROS));
        Long revisionExact = saveJobWithRevision(EXPIRED_AT.minusDays(9), EXPIRED_AT);
        Long revisionAfter = saveJobWithRevision(EXPIRED_AT.minusDays(9), EXPIRED_AT.minusSeconds(1));
        LocalDateTime before = now();

        assertThat(ownTargetIds()).containsExactly(draftExact, draftAfter, revisionExact, revisionAfter);
        for (Long jobId : List.of(draftBefore, revisionBefore)) {
            assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isFalse();
            assertInProgress(jobId, JobSubmissionReviewStatus.PENDING);
        }
        for (Long jobId : List.of(draftExact, draftAfter, revisionExact, revisionAfter)) {
            assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isTrue();
            assertCompleted(jobId, before);
        }
        // 승인되는 것은 최신 제출물뿐이고 앞선 수정 요청 초안은 그대로다
        assertThat(submissions(revisionExact)).extracting(JobSubmission::getReviewStatus).containsExactly(
                JobSubmissionReviewStatus.REVISION_REQUESTED, JobSubmissionReviewStatus.APPROVED);
        assertThat(ownTargetIds()).isEmpty();
    }

    @Test
    @DisplayName("수정 요청 상태, 이미 완료·취소된 의뢰, 제출물 없는 의뢰, 데모 의뢰는 기간이 지나도 대상이 아니고 직접 처리해도 건너뛴다")
    void excludesIneligibleJobs() {
        LocalDateTime old = EXPIRED_AT.minusDays(10);
        Long revisionRequested = saveJob(JobStatus.MATCHED, null);
        saveSubmission(revisionRequested, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED, old);
        Long closed = saveJob(JobStatus.CLOSED, null);
        saveSubmission(closed, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.APPROVED, old);
        Long cancelled = saveJob(JobStatus.CANCELLED, null);
        saveSubmission(cancelled, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING, old);
        Long withoutSubmission = saveJob(JobStatus.MATCHED, null);
        Long demo = saveJob(JobStatus.MATCHED, "01K58M6PJV8VAJMXHBHJ2ACDEM");
        saveSubmission(demo, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING, old);

        assertThat(ownTargetIds()).isEmpty();
        for (Long jobId : List.of(revisionRequested, closed, cancelled, withoutSubmission, demo)) {
            assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isFalse();
        }
        assertInProgress(revisionRequested, JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(findJob(cancelled).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(findJob(withoutSubmission).getStatus()).isEqualTo(JobStatus.MATCHED);
        assertInProgress(demo, JobSubmissionReviewStatus.PENDING);
        assertThat(committedEvents.events).isEmpty();
    }

    @Test
    @DisplayName("오래된 초안 뒤에 새 수정안이 있으면 최신 수정안의 제출 시각으로 판단해 대상에서 빼고, 그 수정안이 만료되면 완료한다")
    void judgesByLatestSubmission() {
        Long jobId = saveJobWithRevision(EXPIRED_AT.minusDays(30), EXPIRED_AT.plusDays(6));

        assertThat(ownTargetIds()).isEmpty();
        assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isFalse();
        assertInProgress(jobId, JobSubmissionReviewStatus.PENDING);

        LocalDateTime later = REFERENCE_TIME.plusDays(6);
        assertThat(jobService.getAutoCompletableJobIds(jobId - 1, later, 1)).containsExactly(jobId);
        assertThat(jobFacade.autoCompleteSubmission(jobId, later)).isTrue();
    }

    @Test
    @DisplayName("대상 조회 뒤 사장님이 수정을 요청하면 잠금 후 재확인에서 건너뛰고 알림을 준비하지 않는다")
    void skipsWhenRevisionRequestedAfterQuery() {
        Long jobId = saveJobWithDraft(EXPIRED_AT.minusDays(1));
        assertThat(ownTargetIds()).containsExactly(jobId);

        jobService.requestRevision(revisionCommand(jobId), OWNER_PROFILE_ID);

        assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isFalse();
        assertInProgress(jobId, JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(committedEvents.events).isEmpty();
    }

    @Test
    @DisplayName("알림은 커밋 뒤에만 발행하고, 결제 완료 기록이 있으면 후기 요청과 정산 내역을, 없으면 후기 요청만 발행한다")
    void publishesAfterCommitDependingOnPayment() {
        Long paid = saveJobWithDraft(EXPIRED_AT);
        Long unpaid = saveJobWithDraft(EXPIRED_AT);
        when(paymentService.findPaidPayment(paid)).thenReturn(Optional.of(
                new ApprovedPaymentData(91L, "order-auto-completion", 100_000L, Instant.parse("2026-09-01T00:00:00Z"))));

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(jobFacade.autoCompleteSubmission(paid, REFERENCE_TIME)).isTrue();
            assertThat(committedEvents.events).isEmpty();
        });
        assertThat(jobFacade.autoCompleteSubmission(unpaid, REFERENCE_TIME)).isTrue();

        assertThat(committedEvents.events).containsExactly(
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, paid, JOB_TITLE, "김학생"),
                NotificationEventFactory.paymentSettled(STUDENT_USER_ID, 91L, paid, JOB_TITLE, 100_000L),
                NotificationEventFactory.jobReviewRequested(OWNER_USER_ID, unpaid, JOB_TITLE, "김학생"));
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 완료도 알림도 남지 않는다")
    void publishesNothingOnRollback() {
        Long jobId = saveJobWithDraft(EXPIRED_AT);

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)).isTrue();
            status.setRollbackOnly();
        });

        assertInProgress(jobId, JobSubmissionReviewStatus.PENDING);
        assertThat(committedEvents.events).isEmpty();
    }

    @Test
    @DisplayName("스케줄러는 100건을 넘는 대상을 모두 완료하고, 다시 실행해도 완료 시각과 알림이 늘지 않는다")
    void completesMoreThanOneBatchOnce() {
        LocalDateTime submittedAt = now().minusDays(8);
        List<Long> targets = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            targets.add(saveJobWithDraft(submittedAt));
        }
        JobAutoCompletionScheduler scheduler = new JobAutoCompletionScheduler(jobService, jobFacade, clock);

        scheduler.completeExpiredSubmissions();

        List<Map<String, Object>> first = completions();
        assertThat(first).hasSize(101).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("CLOSED");
            assertThat(row.get("completed_at")).isNotNull();
        });
        assertThat(reviewRequestTargets()).containsExactlyInAnyOrderElementsOf(targets);

        scheduler.completeExpiredSubmissions();

        assertThat(completions()).isEqualTo(first);
        assertThat(committedEvents.events).hasSize(101);
    }

    @Test
    @DisplayName("한 건의 알림 준비가 실패하면 그 의뢰만 롤백하고 나머지는 완료하며, 실패한 의뢰는 다음 실행에서 완료한다")
    void rollsBackOnlyFailedJobAndRetriesNextRun() {
        LocalDateTime submittedAt = now().minusDays(8);
        Long first = saveJobWithDraft(submittedAt);
        Long failing = saveJobWithDraft(submittedAt);
        Long last = saveJobWithDraft(submittedAt);
        doThrow(new IllegalStateException("테스트 결제 조회 실패")).when(paymentService).findPaidPayment(failing);
        JobAutoCompletionScheduler scheduler = new JobAutoCompletionScheduler(jobService, jobFacade, clock);

        scheduler.completeExpiredSubmissions();

        assertThat(findJob(first).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(findJob(last).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertInProgress(failing, JobSubmissionReviewStatus.PENDING);
        assertThat(reviewRequestTargets()).containsExactlyInAnyOrder(first, last);

        doReturn(Optional.empty()).when(paymentService).findPaidPayment(anyLong());
        scheduler.completeExpiredSubmissions();

        assertThat(findJob(failing).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(reviewRequestTargets()).containsExactlyInAnyOrder(first, last, failing);
    }

    @Test
    @DisplayName("같은 의뢰를 동시에 자동 완료하면 한 번만 완료하고 알림도 한 번만 준비한다")
    void completesOnceWhenRunConcurrently() throws Exception {
        Long jobId = saveJobWithDraft(EXPIRED_AT);
        Callable<Boolean> autoComplete = () -> jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME);

        List<Boolean> results = runConcurrently(List.of(autoComplete, autoComplete, autoComplete));

        assertThat(results).containsExactlyInAnyOrder(true, false, false);
        assertThat(findJob(jobId).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(reviewRequestTargets()).containsExactly(jobId);
    }

    @Test
    @DisplayName("사장님의 수동 완료와 자동 완료가 겹치면 둘 중 하나만 완료하고 알림도 한 번만 준비한다")
    void completesOnceWhenRacingWithManualCompletion() throws Exception {
        Long jobId = saveJobWithDraft(EXPIRED_AT);
        Long submissionId = submissions(jobId).get(0).getId();
        Callable<Boolean> manual = () -> {
            try {
                jobFacade.completeSubmission(OWNER_USERNAME, jobId, submissionId);
                return true;
            } catch (BusinessException exception) {
                return false;
            }
        };

        List<Boolean> results = runConcurrently(
                List.of(manual, () -> jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(findJob(jobId).getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(submissions(jobId)).extracting(JobSubmission::getReviewStatus)
                .containsExactly(JobSubmissionReviewStatus.APPROVED);
        assertThat(reviewRequestTargets()).containsExactly(jobId);
    }

    @Test
    @DisplayName("사장님의 수정 요청과 자동 완료가 겹치면 먼저 잠근 쪽만 반영되고 의뢰와 제출물 상태가 어긋나지 않는다")
    void appliesOnlyOneWhenRacingWithRevisionRequest() throws Exception {
        Long jobId = saveJobWithDraft(EXPIRED_AT);
        Callable<Boolean> requestRevision = () -> {
            try {
                jobService.requestRevision(revisionCommand(jobId), OWNER_PROFILE_ID);
                return true;
            } catch (BusinessException exception) {
                return false;
            }
        };

        List<Boolean> results = runConcurrently(
                List.of(requestRevision, () -> jobFacade.autoCompleteSubmission(jobId, REFERENCE_TIME)));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        if (results.get(0)) {
            assertInProgress(jobId, JobSubmissionReviewStatus.REVISION_REQUESTED);
            assertThat(committedEvents.events).isEmpty();
        } else {
            assertThat(findJob(jobId).getStatus()).isEqualTo(JobStatus.CLOSED);
            assertThat(submissions(jobId)).extracting(JobSubmission::getReviewStatus)
                    .containsExactly(JobSubmissionReviewStatus.APPROVED);
            assertThat(reviewRequestTargets()).containsExactly(jobId);
        }
    }

    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /** 로컬 DB에 남아 있는 다른 의뢰와 섞이지 않도록 이 테스트가 만든 의뢰만 남긴다. */
    private List<Long> ownTargetIds() {
        return jobService.getAutoCompletableJobIds(jobIds.get(0) - 1, REFERENCE_TIME, 1000).stream()
                .filter(jobIds::contains)
                .toList();
    }

    private List<Long> reviewRequestTargets() {
        return committedEvents.events.stream()
                .filter(event -> event.recipientUserId().equals(OWNER_USER_ID))
                .map(event -> Long.valueOf(event.targetId()))
                .toList();
    }

    private List<Map<String, Object>> completions() {
        return jdbcTemplate.queryForList("select id, status, completed_at from jobs where id >= ? and id <= ?"
                + " and owner_profile_id = ? order by id", jobIds.get(0), jobIds.get(jobIds.size() - 1),
                OWNER_PROFILE_ID);
    }

    private void assertCompleted(Long jobId, LocalDateTime before) {
        Job job = findJob(jobId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        assertThat(job.getCompletedAt()).isBetween(before, now());
        List<JobSubmission> submissions = submissions(jobId);
        assertThat(submissions.get(submissions.size() - 1).getReviewStatus())
                .isEqualTo(JobSubmissionReviewStatus.APPROVED);
    }

    private void assertInProgress(Long jobId, JobSubmissionReviewStatus latestReviewStatus) {
        Job job = findJob(jobId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.MATCHED);
        assertThat(job.getCompletedAt()).isNull();
        List<JobSubmission> submissions = submissions(jobId);
        assertThat(submissions.get(submissions.size() - 1).getReviewStatus()).isEqualTo(latestReviewStatus);
    }

    private Job findJob(Long jobId) {
        return transactionTemplate.execute(status -> jobRepository.findById(jobId).orElseThrow());
    }

    private List<JobSubmission> submissions(Long jobId) {
        return transactionTemplate.execute(status -> jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(jobId));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private Long saveJobWithDraft(LocalDateTime submittedAt) {
        Long jobId = saveJob(JobStatus.MATCHED, null);
        saveSubmission(jobId, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.PENDING, submittedAt);
        return jobId;
    }

    private Long saveJobWithRevision(LocalDateTime draftSubmittedAt, LocalDateTime revisionSubmittedAt) {
        Long jobId = saveJob(JobStatus.MATCHED, null);
        saveSubmission(jobId, JobSubmissionType.DRAFT, 0, JobSubmissionReviewStatus.REVISION_REQUESTED,
                draftSubmittedAt);
        saveSubmission(jobId, JobSubmissionType.REVISION, 1, JobSubmissionReviewStatus.PENDING, revisionSubmittedAt);
        return jobId;
    }

    private Long saveJob(JobStatus status, String demoSessionId) {
        Long jobId = jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(OWNER_PROFILE_ID)
                .title(JOB_TITLE)
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(2)
                .status(status)
                .selectedStudentProfileId(STUDENT_PROFILE_ID)
                .demoSessionId(demoSessionId)
                .build()).getId();
        jobIds.add(jobId);
        return jobId;
    }

    // 제출 시각(created_at)은 저장할 때 현재 시각으로 채워지고 수정할 수 없어 SQL로 직접 맞춘다
    private void saveSubmission(Long jobId, JobSubmissionType type, int revisionNumber,
            JobSubmissionReviewStatus reviewStatus, LocalDateTime submittedAt) {
        Long submissionId = jobSubmissionRepository.saveAndFlush(JobSubmission.builder()
                .jobId(jobId)
                .submissionType(type)
                .revisionNumber(revisionNumber)
                .fileUrls(List.of("https://example.com/a.png"))
                .message("제출 메시지")
                .reviewStatus(reviewStatus)
                .build()).getId();
        jdbcTemplate.update("update job_submissions set created_at = ? where id = ?",
                Timestamp.valueOf(submittedAt), submissionId);
    }

    private RequestJobSubmissionRevisionCommand revisionCommand(Long jobId) {
        return RequestJobSubmissionRevisionCommand.of(OWNER_USERNAME, jobId, submissions(jobId).get(0).getId(),
                "로고를 조금 더 크게 해주세요.", List.of());
    }
}
