package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * 컬럼의 NOT NULL·길이·UNIQUE 제약은 Flyway가 적용된 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("의뢰 지원서 PostgreSQL 매핑 (저장·재조회·필수·길이·중복 제약)")
class JobApplicationPostgresTest {

    private static final long JOB_ID = 987_001L;
    private static final long STUDENT_PROFILE_ID = 987_101L;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("한 줄 요약·작업계획서·결과물 전달 방법은 저장한 값 그대로 재조회된다")
    void savesAndReadsContentFields() {
        Long id = jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID,
                "매장 분위기에 맞는 메뉴판을 제작하겠습니다.",
                "요구사항 확인 후 시안을 제작하고 피드백을 반영하겠습니다.",
                "인쇄용 PDF와 편집 가능한 원본 파일로 전달하겠습니다.")).getId();
        entityManager.clear();

        JobApplication found = jobApplicationRepository.findById(id).orElseThrow();

        assertThat(found.getSummary()).isEqualTo("매장 분위기에 맞는 메뉴판을 제작하겠습니다.");
        assertThat(found.getWorkPlan()).isEqualTo("요구사항 확인 후 시안을 제작하고 피드백을 반영하겠습니다.");
        assertThat(found.getDeliveryMethod()).isEqualTo("인쇄용 PDF와 편집 가능한 원본 파일로 전달하겠습니다.");
        assertThat(found.getStatus()).isEqualTo(JobApplicationStatus.PENDING);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("한 줄 요약 255자, 작업계획서·결과물 전달 방법 500자까지 저장된다")
    void savesMaxLengthValues() {
        Long id = jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID,
                "가".repeat(255), "나".repeat(500), "다".repeat(500))).getId();
        entityManager.clear();

        JobApplication found = jobApplicationRepository.findById(id).orElseThrow();

        assertThat(found.getSummary()).hasSize(255);
        assertThat(found.getWorkPlan()).hasSize(500);
        assertThat(found.getDeliveryMethod()).hasSize(500);
    }

    @ParameterizedTest
    @ValueSource(strings = { "summary", "work_plan", "delivery_method" })
    @DisplayName("한 줄 요약·작업계획서·결과물 전달 방법 중 하나라도 길이를 넘으면 저장을 거부한다")
    void rejectsTooLongValue(String column) {
        JobApplication application = application(STUDENT_PROFILE_ID,
                "가".repeat(column.equals("summary") ? 256 : 255),
                "나".repeat(column.equals("work_plan") ? 501 : 500),
                "다".repeat(column.equals("delivery_method") ? 501 : 500));

        assertThatThrownBy(() -> jobApplicationRepository.saveAndFlush(application))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "summary", "work_plan", "delivery_method" })
    @DisplayName("한 줄 요약·작업계획서·결과물 전달 방법 중 하나라도 NULL이면 DB가 저장을 거부한다")
    void rejectsNullValue(String column) {
        // 엔티티 저장은 Hibernate가 먼저 막으므로 컬럼 제약은 네이티브 SQL로 확인한다
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                insert into job_applications (student_profile_id, job_id, status, summary, work_plan, delivery_method)
                values (:studentProfileId, :jobId, 'PENDING', :summary, :workPlan, :deliveryMethod)
                """)
                .setParameter("studentProfileId", STUDENT_PROFILE_ID)
                .setParameter("jobId", JOB_ID)
                .setParameter("summary", column.equals("summary") ? null : "한 줄 요약")
                .setParameter("workPlan", column.equals("work_plan") ? null : "작업계획서")
                .setParameter("deliveryMethod", column.equals("delivery_method") ? null : "결과물 전달 방법")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining(column);
    }

    @Test
    @DisplayName("같은 학생이 같은 의뢰에 두 번 지원하면 저장을 거부한다")
    void rejectsDuplicateApplication() {
        jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID, "요약", "계획", "전달"));

        assertThatThrownBy(() -> jobApplicationRepository.saveAndFlush(
                application(STUDENT_PROFILE_ID, "다른 요약", "다른 계획", "다른 전달")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("job_applications_job_id_student_profile_id_key");
    }

    @Test
    @DisplayName("다른 학생은 같은 의뢰에 지원할 수 있다")
    void allowsOtherStudentOnSameJob() {
        jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID, "요약", "계획", "전달"));
        jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID + 1, "요약", "계획", "전달"));

        assertThat(jobApplicationRepository.findByStudentProfileId(STUDENT_PROFILE_ID)).hasSize(1);
        assertThat(jobApplicationRepository.findByStudentProfileId(STUDENT_PROFILE_ID + 1)).hasSize(1);
    }

    @Test
    @DisplayName("학생과 의뢰 ID 목록으로 조회하면 상태와 무관하게 그 학생이 그 의뢰들에 낸 지원서만 반환한다")
    void findsByStudentAndJobIdsRegardlessOfStatus() {
        jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID, JOB_ID, JobApplicationStatus.PENDING));
        jobApplicationRepository.saveAndFlush(
                application(STUDENT_PROFILE_ID, JOB_ID + 1, JobApplicationStatus.ACCEPTED));
        jobApplicationRepository.saveAndFlush(
                application(STUDENT_PROFILE_ID, JOB_ID + 2, JobApplicationStatus.REJECTED));
        // 조회 목록에 없는 의뢰와 다른 학생만 지원한 의뢰는 빠진다
        jobApplicationRepository.saveAndFlush(application(STUDENT_PROFILE_ID, JOB_ID + 3, JobApplicationStatus.PENDING));
        jobApplicationRepository.saveAndFlush(
                application(STUDENT_PROFILE_ID + 1, JOB_ID + 4, JobApplicationStatus.PENDING));
        entityManager.clear();

        assertThat(jobApplicationRepository.findByStudentProfileIdAndJobIdIn(
                STUDENT_PROFILE_ID, List.of(JOB_ID, JOB_ID + 1, JOB_ID + 2, JOB_ID + 4)))
                .extracting(JobApplication::getJobId)
                .containsExactlyInAnyOrder(JOB_ID, JOB_ID + 1, JOB_ID + 2);
    }

    private static JobApplication application(Long studentProfileId, Long jobId, JobApplicationStatus status) {
        return JobApplication.builder()
                .jobId(jobId)
                .studentProfileId(studentProfileId)
                .summary("요약")
                .workPlan("계획")
                .deliveryMethod("전달")
                .status(status)
                .build();
    }

    private static JobApplication application(
            Long studentProfileId, String summary, String workPlan, String deliveryMethod) {
        return JobApplication.builder()
                .jobId(JOB_ID)
                .studentProfileId(studentProfileId)
                .summary(summary)
                .workPlan(workPlan)
                .deliveryMethod(deliveryMethod)
                .status(JobApplicationStatus.PENDING)
                .build();
    }
}
