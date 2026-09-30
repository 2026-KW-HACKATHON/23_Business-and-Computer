package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

/**
 * PostgreSQL에서 탐색용 의뢰 쿼리의 취소 제외·정렬·커서 경계·대분류 조건을 확인한다. 각 테스트는 끝나면 롤백된다.
 * 공유 DB의 다른 데이터와 섞이지 않도록 먼 미래 생성 시각을 쓰고, 대분류는 테스트마다 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JobExploreQueryIntegrationTest {

    private static final LocalDateTime BEFORE_T1 = LocalDateTime.of(2031, 1, 1, 0, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2031, 1, 1, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2031, 1, 2, 9, 0, 0, 123_456_000);
    private static final LocalDateTime AFTER_T2 = LocalDateTime.of(2031, 1, 3, 0, 0);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobSpecialtyRepository jobSpecialtyRepository;

    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    private Long categoryA;
    private Long older;
    private Long first;
    private Long second;
    private Long cancelled;
    private Long otherCategory;

    /** older(T1, OPEN, A), first(T2, MATCHED, A), cancelled(T2, CANCELLED, A), second(T2, CLOSED, A), otherCategory(T2, OPEN, B). */
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        categoryA = specialtyCategoryRepository.save(SpecialtyCategory.builder().name("탐색A-" + suffix).build()).getId();
        Long categoryB = specialtyCategoryRepository.save(SpecialtyCategory.builder().name("탐색B-" + suffix).build()).getId();
        Long a = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryA).name("로고").build()).getId();
        Long b = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryB).name("숏폼").build()).getId();

        older = job(JobStatus.OPEN, a);
        first = job(JobStatus.MATCHED, a);
        cancelled = job(JobStatus.CANCELLED, a);
        second = job(JobStatus.CLOSED, a);
        otherCategory = job(JobStatus.OPEN, b);
        entityManager.flush();
        entityManager.createNativeQuery("""
                UPDATE jobs
                SET created_at = CASE WHEN id = :older THEN CAST(:t1 AS timestamp) ELSE CAST(:t2 AS timestamp) END
                WHERE id IN (:ids)
                """)
                .setParameter("t1", T1)
                .setParameter("t2", T2)
                .setParameter("older", older)
                .setParameter("ids", List.of(older, first, cancelled, second, otherCategory))
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("대분류 없는 최신순·오래된순 구간 쿼리는 취소 의뢰를 빼고 같은 시각 행을 경계 ID로 자르며 이전·이후 행을 시각·ID 순으로 읽는다")
    void readsCreatedAtSegmentsWithoutCancelled() {
        assertThat(ids(jobRepository.findByStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                JobStatus.CANCELLED, T2, second, Limit.of(10)))).containsExactly(first);
        assertThat(ids(jobRepository.findByStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                JobStatus.CANCELLED, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(otherCategory, second, first);
        assertThat(ids(jobRepository.findByStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                JobStatus.CANCELLED, T2, Limit.of(1)))).containsExactly(older);

        assertThat(ids(jobRepository.findByStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                JobStatus.CANCELLED, T2, first, Limit.of(10)))).containsExactly(second, otherCategory);
        assertThat(ids(jobRepository.findByStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                JobStatus.CANCELLED, BEFORE_T1, Limit.of(4)))).containsExactly(older, first, second, otherCategory);
    }

    @Test
    @DisplayName("대분류 쿼리는 취소를 뺀 그 대분류 의뢰만 튜플 경계 뒤부터 정렬 순서대로 읽는다")
    void readsCategoryQueriesWithoutCancelled() {
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                JobStatus.CANCELLED, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(second, first, older);
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                JobStatus.CANCELLED, categoryA, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                JobStatus.CANCELLED, categoryA, T1, older, Limit.of(10)))).containsExactly(first, second);
    }

    private Long job(JobStatus status, Long specialtyId) {
        Job job = jobRepository.save(Job.builder()
                .ownerProfileId(5L)
                .title("의뢰")
                .description("설명")
                .budget(10000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(1)
                .status(status)
                .selectedStudentProfileId(status == JobStatus.OPEN ? null : 7L)
                .build());
        jobSpecialtyRepository.save(JobSpecialty.create(job.getId(), specialtyId));
        return job.getId();
    }

    private List<Long> ids(List<Job> jobs) {
        return jobs.stream().map(Job::getId).toList();
    }
}
