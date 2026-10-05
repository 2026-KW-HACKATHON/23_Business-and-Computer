package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentAppliedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.service.JobService;

import jakarta.persistence.EntityManager;

/**
 * 지원 시각 정렬과 demo_session_id의 NULL 비교는 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("학생 '내가 지원한 의뢰' 목록 PostgreSQL 조회 (조회 조건·정렬·데모 격리)")
class JobStudentAppliedListPostgresTest {

    private static final long STUDENT_PROFILE_ID = 988_101L;
    private static final long OTHER_STUDENT_PROFILE_ID = 988_102L;
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2DEMO1";
    private static final String OTHER_DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2DEMO2";
    private static final LocalDateTime T1 = LocalDateTime.of(2031, 1, 1, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2031, 1, 2, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T3 = LocalDateTime.of(2031, 1, 3, 9, 0, 0, 123_456_000);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSpecialtyRepository jobSpecialtyRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private EntityManager entityManager;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                mock(JobSubmissionRepository.class), Clock.systemUTC());
    }

    @Test
    @DisplayName("본인의 PENDING 지원서와 OPEN 의뢰 조합만 반환하고 다른 학생·나머지 상태 조합은 뺀다")
    void returnsOnlyOwnPendingApplicationsOfOpenJobs() {
        Long listed = job(JobStatus.OPEN, null);
        Long listedApplication = application(listed, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T1);
        jobSpecialtyRepository.saveAndFlush(JobSpecialty.create(listed, 988_901L));
        jobSpecialtyRepository.saveAndFlush(JobSpecialty.create(listed, 988_902L));

        // 모집 중이 아닌 의뢰의 대기 중 지원서
        for (JobStatus status : List.of(
                JobStatus.AWAITING_START, JobStatus.MATCHED, JobStatus.CLOSED, JobStatus.CANCELLED)) {
            application(job(status, null), STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T2);
        }
        // 모집 중 의뢰의 선정·탈락 지원서
        application(job(JobStatus.OPEN, null), STUDENT_PROFILE_ID, JobApplicationStatus.ACCEPTED, T2);
        application(job(JobStatus.OPEN, null), STUDENT_PROFILE_ID, JobApplicationStatus.REJECTED, T2);
        // 다른 학생의 지원서. 같은 의뢰에 낸 것도 본인 목록에는 나오지 않는다
        application(listed, OTHER_STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T3);
        application(job(JobStatus.OPEN, null), OTHER_STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T3);
        entityManager.clear();

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, null));

        assertThat(result).hasSize(1);
        StudentAppliedJobData data = result.get(0);
        assertThat(data.getJob().getId()).isEqualTo(listed);
        assertThat(data.getJob().getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(data.getApplication().getId()).isEqualTo(listedApplication);
        assertThat(data.getApplication().getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        assertThat(data.getApplication().getCreatedAt()).isEqualTo(T1);
        assertThat(data.getSpecialtyIds()).containsExactlyInAnyOrder(988_901L, 988_902L);
    }

    @Test
    @DisplayName("의뢰 생성일과 무관하게 지원 시각 최신순으로 정렬하고 같은 시각이면 지원서 ID 내림차순이다")
    void ordersByAppliedAtThenApplicationId() {
        // 의뢰는 지원 순서와 반대로 만든다
        Long newestJob = job(JobStatus.OPEN, null);
        Long sameTimeJob = job(JobStatus.OPEN, null);
        Long laterSameTimeJob = job(JobStatus.OPEN, null);
        Long oldestJob = job(JobStatus.OPEN, null);
        setJobCreatedAt(newestJob, T3);
        setJobCreatedAt(oldestJob, T1);

        // 지원 시각이 늦을수록 지원서 ID가 작도록 저장해 ID 내림차순만으로는 같은 순서가 나오지 않게 한다
        Long newest = application(oldestJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T3);
        Long sameTime = application(sameTimeJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T2);
        Long laterSameTime = application(laterSameTimeJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T2);
        Long oldest = application(newestJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T1);
        entityManager.clear();

        List<StudentAppliedJobData> result = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, null));

        assertThat(newest).isLessThan(sameTime);
        assertThat(oldest).isGreaterThan(laterSameTime);
        assertThat(laterSameTime).isGreaterThan(sameTime);
        assertThat(result).extracting(data -> data.getApplication().getId())
                .containsExactly(newest, laterSameTime, sameTime, oldest);
        assertThat(result).extracting(data -> data.getJob().getId())
                .containsExactly(oldestJob, laterSameTimeJob, sameTimeJob, newestJob);
    }

    @Test
    @DisplayName("마감일이 지난 의뢰도 모집 중이면 포함한다")
    void includesOpenJobPastDeadline() {
        Long pastDeadline = jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(988_001L)
                .title("지원 목록 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2020, 1, 1))
                .finalDeadline(LocalDate.of(2020, 1, 10))
                .revisionCount(1)
                .status(JobStatus.OPEN)
                .build()).getId();
        application(pastDeadline, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T1);
        entityManager.clear();

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, null)))
                .extracting(data -> data.getJob().getId())
                .containsExactly(pastDeadline);
    }

    @Test
    @DisplayName("학생과 같은 데모 격리 범위의 의뢰만 반환한다. 실제 학생(NULL)은 데모 의뢰를, 데모 학생은 실제·다른 세션 의뢰를 보지 않는다")
    void returnsJobsInSameDemoSessionOnly() {
        Long realJob = job(JobStatus.OPEN, null);
        Long demoJob = job(JobStatus.OPEN, DEMO_SESSION_ID);
        Long otherDemoJob = job(JobStatus.OPEN, OTHER_DEMO_SESSION_ID);
        application(realJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T1);
        application(demoJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T2);
        application(otherDemoJob, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T3);
        entityManager.clear();

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, null)))
                .extracting(data -> data.getJob().getId())
                .containsExactly(realJob);
        assertThat(jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, DEMO_SESSION_ID)))
                .extracting(data -> data.getJob().getId())
                .containsExactly(demoJob);
    }

    @Test
    @DisplayName("지원서가 가리키는 의뢰가 없으면 그 항목을 뺀다")
    void skipsApplicationWithoutJob() {
        Long listed = job(JobStatus.OPEN, null);
        application(listed, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T1);
        Long deleted = job(JobStatus.OPEN, null);
        application(deleted, STUDENT_PROFILE_ID, JobApplicationStatus.PENDING, T2);
        jobRepository.deleteById(deleted);
        jobRepository.flush();
        entityManager.clear();

        assertThat(jobService.getStudentAppliedJobs(GetStudentAppliedJobsCommand.of(STUDENT_PROFILE_ID, null)))
                .extracting(data -> data.getJob().getId())
                .containsExactly(listed);
    }

    private Long job(JobStatus status, String demoSessionId) {
        return jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(988_001L)
                .title("지원 목록 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(1)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 988_199L)
                .demoSessionId(demoSessionId)
                .build()).getId();
    }

    /** 지원 시각은 저장 시 자동으로 채워지므로 저장한 뒤 네이티브 SQL로 바꾼다. */
    private Long application(Long jobId, Long studentProfileId, JobApplicationStatus status, LocalDateTime appliedAt) {
        Long id = jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .jobId(jobId)
                .studentProfileId(studentProfileId)
                .status(status)
                .summary("한 줄 요약")
                .workPlan("작업계획서")
                .deliveryMethod("결과물 전달 방법")
                .build()).getId();
        entityManager.createNativeQuery(
                        "UPDATE job_applications SET created_at = CAST(:appliedAt AS timestamp) WHERE id = :id")
                .setParameter("appliedAt", appliedAt)
                .setParameter("id", id)
                .executeUpdate();
        return id;
    }

    private void setJobCreatedAt(Long jobId, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE jobs SET created_at = CAST(:createdAt AS timestamp) WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", jobId)
                .executeUpdate();
    }
}
