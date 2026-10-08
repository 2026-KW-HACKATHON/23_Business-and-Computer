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
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
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
 * PostgreSQL에서 탐색용 의뢰 쿼리의 취소·제안 기반 의뢰·본인 의뢰·지원 탈락 의뢰 제외와 정렬·커서 경계·대분류 조건을 확인한다. 각 테스트는 끝나면 롤백된다.
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
    private JobApplicationRepository jobApplicationRepository;

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
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, null, T2, second, Limit.of(10)))).containsExactly(first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, null, T2, Long.MAX_VALUE, Limit.of(10)))).containsExactly(otherCategory, second, first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, JobStatus.CANCELLED, null, T2, Limit.of(1)))).containsExactly(older);

        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, null, T2, first, Limit.of(10)))).containsExactly(second, otherCategory);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, JobStatus.CANCELLED, null, BEFORE_T1, Limit.of(4)))).containsExactly(older, first, second, otherCategory);
    }

    @Test
    @DisplayName("대분류 쿼리는 취소를 뺀 그 대분류 의뢰만 튜플 경계 뒤부터 정렬 순서대로 읽는다")
    void readsCategoryQueriesWithoutCancelled() {
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, null, null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(second, first, older);
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, null, null, categoryA, T2, second, Limit.of(10)))).containsExactly(first, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                null, JobStatus.CANCELLED, null, null, categoryA, T1, older, Limit.of(10)))).containsExactly(first, second);
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
        return readPages(categoryId, oldestFirst, pages, 2, null, null);
    }

    private List<Long> readPages(Long categoryId, boolean oldestFirst, int pages, int size,
            Long viewerOwnerProfileId, Long viewerStudentProfileId) {
        LocalDateTime createdAt = oldestFirst ? BEFORE_T1 : AFTER_T2;
        Long idBound = Long.MAX_VALUE;
        List<Long> read = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<ExploreJobData> items = jobService.getExploreJobs(GetExploreJobsCommand.forViewer(
                    null, categoryId, oldestFirst, createdAt, idBound, size, viewerOwnerProfileId, viewerStudentProfileId));
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

        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, null, T2, Long.MAX_VALUE, Limit.of(3)))).containsExactly(otherCategory, second, first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                null, JobStatus.CANCELLED, null, AFTER_T2, Limit.of(4)))).containsExactly(otherCategory, second, first, older);

        assertThat(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, null, T1, older, Limit.of(10))).isEmpty();
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                null, JobStatus.CANCELLED, null, T2, first, Limit.of(2)))).containsExactly(second, otherCategory);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, JobStatus.CANCELLED, null, BEFORE_T1, Limit.of(4)))).containsExactly(older, first, second, otherCategory);

        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, null, null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(3))))
                .containsExactly(second, first, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                null, JobStatus.CANCELLED, null, null, categoryA, BEFORE_T1, Long.MAX_VALUE, Limit.of(3))))
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

    @Test
    @DisplayName("본인이 작성한 의뢰는 구간 쿼리와 대분류 쿼리에서 빠지고, 한 장씩 읽어도 다른 사장님의 의뢰가 순서대로 이어진다")
    void excludesOwnJobsBeforeLimit() {
        // second·older만 사장님 6의 의뢰로 바꾼다. 나머지는 사장님 5의 의뢰다
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE jobs SET owner_profile_id = 6 WHERE id IN (:ids)")
                .setParameter("ids", List.of(second, older)).executeUpdate();
        entityManager.clear();

        // 제외된 second가 limit 자리를 차지하지 않는다
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                null, JobStatus.CANCELLED, 6L, T2, Long.MAX_VALUE, Limit.of(2)))).containsExactly(otherCategory, first);
        assertThat(ids(jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndOwnerProfileIdNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                null, JobStatus.CANCELLED, 5L, BEFORE_T1, Limit.of(4)))).containsExactly(older, second);
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, 6L, null, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(3))))
                .containsExactly(first);

        assertThat(readPages(null, false, 5, 1, 6L, null)).containsExactly(otherCategory, first);
        assertThat(readPages(null, true, 5, 1, 6L, null)).containsExactly(first, otherCategory);
        assertThat(readPages(null, false, 5, 1, 5L, null)).containsExactly(second, older);
        assertThat(readPages(categoryA, true, 5, 1, 5L, null)).containsExactly(older, second);
        // 두 사장님 모두 아닌 조회자와 사장님 프로필이 없는 조회자는 전부 본다
        assertThat(readPages(null, false, 5, 1, 9L, null)).containsExactly(otherCategory, second, first, older);
        assertThat(readPages(null, false, 5, 1, null, null)).containsExactly(otherCategory, second, first, older);
    }

    /**
     * 학생 77의 지원 이력. older(모집 중)에는 대기 지원, first(학생 7 선정)에는 대기 지원, second(학생 7 선정)에는 탈락 지원이 있고
     * otherCategory에는 지원하지 않았다. 새로 만드는 selectedMine·acceptedMine(T2, 대분류 A)은 학생 77이 선정된 의뢰로
     * 지원서가 각각 대기·선정 상태다. older에는 다른 학생 78의 탈락 지원도 있다.
     * @return selectedMine, acceptedMine의 ID
     */
    private Long[] givenApplicationsOfStudent77() {
        Long selectedMine = job(JobStatus.MATCHED, specialtyA, false, 77L);
        Long acceptedMine = job(JobStatus.MATCHED, specialtyA, false, 77L);
        setCreatedAt(T2, selectedMine, acceptedMine);
        application(older, 77L, JobApplicationStatus.PENDING);
        application(older, 78L, JobApplicationStatus.REJECTED);
        application(first, 77L, JobApplicationStatus.PENDING);
        application(second, 77L, JobApplicationStatus.REJECTED);
        application(selectedMine, 77L, JobApplicationStatus.PENDING);
        application(acceptedMine, 77L, JobApplicationStatus.ACCEPTED);
        entityManager.flush();
        entityManager.clear();
        return new Long[] { selectedMine, acceptedMine };
    }

    @Test
    @DisplayName("저장된 탈락 지원과 다른 학생이 선정된 대기 지원의 의뢰는 빠지고, 미지원·모집 중 대기·본인 선정 의뢰는 남는다")
    void excludesJobsWhereViewerApplicationIsRejected() {
        Long[] mine = givenApplicationsOfStudent77();
        Long selectedMine = mine[0];
        Long acceptedMine = mine[1];

        assertThat(ids(jobRepository.findExploreLatestForStudent(
                null, JobStatus.CANCELLED, null, 77L, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(acceptedMine, selectedMine, otherCategory, older);
        assertThat(ids(jobRepository.findExploreOldestForStudent(
                null, JobStatus.CANCELLED, null, 77L, BEFORE_T1, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(older, otherCategory, selectedMine, acceptedMine);
        assertThat(ids(jobRepository.findExploreLatestInCategory(
                null, JobStatus.CANCELLED, null, 77L, categoryA, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(acceptedMine, selectedMine, older);
        assertThat(ids(jobRepository.findExploreOldestInCategory(
                null, JobStatus.CANCELLED, null, 77L, categoryA, BEFORE_T1, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(older, selectedMine, acceptedMine);
        // 다른 학생 78은 older에서만 탈락했고, 지원 이력이 없는 학생 79는 전부 본다
        assertThat(ids(jobRepository.findExploreLatestForStudent(
                null, JobStatus.CANCELLED, null, 78L, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(acceptedMine, selectedMine, otherCategory, second, first);
        assertThat(ids(jobRepository.findExploreLatestForStudent(
                null, JobStatus.CANCELLED, null, 79L, AFTER_T2, Long.MAX_VALUE, Limit.of(10))))
                .containsExactly(acceptedMine, selectedMine, otherCategory, second, first, older);
        // 본인 의뢰 제외와 함께 걸린다
        assertThat(ids(jobRepository.findExploreLatestForStudent(
                null, JobStatus.CANCELLED, 5L, 79L, AFTER_T2, Long.MAX_VALUE, Limit.of(10)))).isEmpty();
    }

    @Test
    @DisplayName("탈락한 의뢰가 같은 생성 시각에 연속해 있어도 한 장씩 읽을 때 정렬과 대분류 유무마다 남은 의뢰가 중복·누락 없이 이어진다")
    void continuesAfterCursorWithoutRejectedApplicationJobs() {
        Long[] mine = givenApplicationsOfStudent77();
        Long selectedMine = mine[0];
        Long acceptedMine = mine[1];

        assertThat(readPages(null, false, 8, 1, null, 77L))
                .containsExactly(acceptedMine, selectedMine, otherCategory, older);
        assertThat(readPages(null, true, 8, 1, null, 77L))
                .containsExactly(older, otherCategory, selectedMine, acceptedMine);
        assertThat(readPages(categoryA, false, 8, 1, null, 77L)).containsExactly(acceptedMine, selectedMine, older);
        assertThat(readPages(categoryA, true, 8, 1, null, 77L)).containsExactly(older, selectedMine, acceptedMine);
        // 학생 프로필이 없으면 지원 이력으로 거르지 않는다
        assertThat(readPages(null, false, 8, 1, null, null))
                .containsExactly(acceptedMine, selectedMine, otherCategory, second, first, older);
    }

    private void application(Long jobId, Long studentProfileId, JobApplicationStatus status) {
        jobApplicationRepository.save(JobApplication.builder()
                .jobId(jobId)
                .studentProfileId(studentProfileId)
                .summary("요약")
                .workPlan("계획")
                .deliveryMethod("전달")
                .status(status)
                .build());
    }

    private Long job(JobStatus status, Long specialtyId) {
        return job(status, specialtyId, false);
    }

    private Long job(JobStatus status, Long specialtyId, boolean fromProposal) {
        return job(status, specialtyId, fromProposal, status == JobStatus.OPEN ? null : 7L);
    }

    private Long job(JobStatus status, Long specialtyId, boolean fromProposal, Long selectedStudentProfileId) {
        Job job = jobRepository.save(Job.builder()
                .ownerProfileId(5L)
                .title("의뢰")
                .description("설명")
                .budget(10000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(1)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
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
