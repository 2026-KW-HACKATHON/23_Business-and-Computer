package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

/**
 * PostgreSQL에서 탐색용 의뢰 쿼리의 취소·제안 기반 의뢰 제외와 정렬·커서 경계·대분류 조건을 확인한다. 각 테스트는 끝나면 롤백된다.
 * 공유 DB의 다른 데이터와 섞이지 않도록 먼 미래 생성 시각을 쓰고, 대분류는 테스트마다 새로 만든다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JobExploreQueryIntegrationTest {

    private static final LocalDateTime BEFORE_T1 = LocalDateTime.of(2031, 1, 1, 0, 0);
    private static final LocalDateTime T0 = LocalDateTime.of(2031, 1, 1, 3, 0, 0, 123_456_000);
    private static final LocalDateTime T1 = LocalDateTime.of(2031, 1, 1, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T2 = LocalDateTime.of(2031, 1, 2, 9, 0, 0, 123_456_000);
    private static final LocalDateTime T3 = LocalDateTime.of(2031, 1, 2, 12, 0, 0, 123_456_000);
    private static final LocalDateTime AFTER_T2 = LocalDateTime.of(2031, 1, 3, 0, 0);

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobSpecialtyRepository jobSpecialtyRepository;

    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private EntityManager entityManager;

    private Long categoryA;
    private Long specialtyA;
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
        specialtyA = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryA).name("로고").build()).getId();
        Long a = specialtyA;
        Long b = specialtyRepository.save(Specialty.builder().specialtyCategoryId(categoryB).name("숏폼").build()).getId();

        older = job(JobStatus.OPEN, a);
        first = job(JobStatus.MATCHED, a);
        cancelled = job(JobStatus.CANCELLED, a);
        second = job(JobStatus.CLOSED, a);
        otherCategory = job(JobStatus.OPEN, b);
        setCreatedAt(T1, older);
        setCreatedAt(T2, first, cancelled, second, otherCategory);
        entityManager.clear();
    }

    @Test
    @DisplayName("대분류 없는 최신순·오래된순 구간 쿼리는 취소 의뢰를 빼고 같은 시각 행을 경계 ID로 자르며 이전·이후 행을 시각·ID 순으로 읽는다")
    void readsCreatedAtSegmentsWithoutCancelled() {
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, T2, second, Limit.of(10)))).containsExactly(first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(otherCategory, second, first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, JobStatus.CANCELLED, T2, Limit.of(1)))).containsExactly(older);

        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, T2, first, Limit.of(10)))).containsExactly(second, otherCategory);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, JobStatus.CANCELLED, BEFORE_T1, Limit.of(4)))).containsExactly(older, first, second, otherCategory);
    }

    @Test
    @DisplayName("대분류 쿼리는 취소를 뺀 그 대분류 의뢰만 튜플 경계 뒤부터 정렬 순서대로 읽는다")
    void readsCategoryQueriesWithoutCancelled() {
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(second, first, older);
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, categoryA, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                null, JobStatus.CANCELLED, categoryA, T1, older, Limit.of(10)))).containsExactly(first, second);
    }

    /**
     * 대분류 A에 제안으로 만든 의뢰를 정렬상 앞뒤와 같은 생성 시각에 섞는다. ID가 일반 의뢰보다 커서
     * T2에서는 최신순 맨 앞, T1에서는 오래된순으로 older와 first 사이에 놓인다.
     */
    private void givenProposalJobsAroundRegularJobs() {
        setCreatedAt(T0, job(JobStatus.CLOSED, specialtyA, true));
        setCreatedAt(T1, job(JobStatus.AWAITING_START, specialtyA, true));
        setCreatedAt(T2, job(JobStatus.MATCHED, specialtyA, true), job(JobStatus.CLOSED, specialtyA, true));
        setCreatedAt(T3, job(JobStatus.AWAITING_START, specialtyA, true));
    }

    private void setCreatedAt(LocalDateTime createdAt, Long... jobIds) {
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE jobs SET created_at = CAST(:createdAt AS timestamp) WHERE id IN (:ids)")
                .setParameter("createdAt", createdAt)
                .setParameter("ids", List.of(jobIds))
                .executeUpdate();
        entityManager.clear();
    }

    /** 커서를 마지막 행의 (createdAt, id)로 옮기며 pages번 읽어 이어 붙인다. */
    private List<Long> readPages(Long categoryId, boolean oldestFirst, int pages) {
        LocalDateTime createdAt = oldestFirst ? BEFORE_T1 : AFTER_T2;
        Long idBound = Long.MAX_VALUE;
        List<Long> read = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<ExploreJobData> items = jobService.getExploreJobs(
                    GetExploreJobsCommand.of(null, categoryId, oldestFirst, createdAt, idBound, 2));
            if (items.isEmpty()) {
                break;
            }
            Job last = items.get(items.size() - 1).getJob();
            createdAt = last.getCreatedAt();
            idBound = last.getId();
            items.forEach(item -> read.add(item.getJob().getId()));
        }
        return read;
    }

    @Test
    @DisplayName("제안으로 만든 의뢰는 상태와 무관하게 조회 6개 모두에서 빠지고, 정렬상 앞이나 같은 시각에 있어도 일반 의뢰가 조회 제한을 채운다")
    void excludesProposalJobsFromEveryExploreQuery() {
        givenProposalJobsAroundRegularJobs();

        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, T2, Long.MAX_VALUE, Limit.of(3)))).containsExactly(otherCategory, second, first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, JobStatus.CANCELLED, AFTER_T2, Limit.of(4)))).containsExactly(otherCategory, second, first, older);

        assertThat(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, T1, older, Limit.of(10))).isEmpty();
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, T2, first, Limit.of(2)))).containsExactly(second, otherCategory);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, JobStatus.CANCELLED, BEFORE_T1, Limit.of(4)))).containsExactly(older, first, second, otherCategory);

        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(3))))
                .containsExactly(second, first, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                null, JobStatus.CANCELLED, categoryA, BEFORE_T1, Long.MAX_VALUE, Limit.of(3))))
                .containsExactly(older, first, second);
    }

    @Test
    @DisplayName("제안으로 만든 의뢰가 섞여 있어도 최신순·오래된순과 대분류 유무마다 커서 뒤 일반 의뢰가 중복·누락 없이 이어진다")
    void continuesAfterCursorWithoutProposalJobs() {
        givenProposalJobsAroundRegularJobs();

        assertThat(readPages(null, false, 2)).containsExactly(otherCategory, second, first, older);
        assertThat(readPages(null, true, 2)).containsExactly(older, first, second, otherCategory);
        assertThat(readPages(categoryA, false, 3)).containsExactly(second, first, older);
        assertThat(readPages(categoryA, true, 3)).containsExactly(older, first, second);
    }

    private Long job(JobStatus status, Long specialtyId) {
        return job(status, specialtyId, false);
    }

    private Long job(JobStatus status, Long specialtyId, boolean fromProposal) {
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
                // proposal_id는 UNIQUE라 공유 DB의 기존 값과 겹치지 않게 음수 난수를 쓴다
                .proposalId(fromProposal ? Long.valueOf(-ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)) : null)
                .build());
        jobSpecialtyRepository.save(JobSpecialty.create(job.getId(), specialtyId));
        return job.getId();
    }

    private List<Long> ids(List<Job> jobs) {
        return jobs.stream().map(Job::getId).toList();
    }
}
