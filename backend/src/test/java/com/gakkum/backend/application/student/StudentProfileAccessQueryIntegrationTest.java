package com.gakkum.backend.application.student;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;

/** 사장님의 학생 프로필 열람 관계 조회. 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백된다. ID는 실제 데이터와 겹치지 않는 값을 쓴다. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("학생 프로필 열람 관계 PostgreSQL 조회 (의뢰 지원·선택, 받은 제안, 탐색 제안)")
class StudentProfileAccessQueryIntegrationTest {

    private static final long OWNER = 989_005L;
    private static final long OTHER_OWNER = 989_006L;
    private static final long STUDENT = 989_001L;
    private static final long OTHER_STUDENT = 989_002L;
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PNBX1";
    private static final String OTHER_DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2PNBX2";

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(JobApplicationStatus.class)
    @DisplayName("PostgreSQL에서 학생이 사장님의 의뢰에 지원했으면 지원 상태와 무관하게 관계가 있다")
    void relatedWhenStudentAppliedToOwnerJob(JobApplicationStatus status) {
        saveApplication(saveJob(OWNER, null).getId(), STUDENT, status);

        assertThat(jobService.isStudentRelatedToOwnerJobs(OWNER, STUDENT)).isTrue();
        assertThat(jobService.isStudentRelatedToOwnerJobs(OTHER_OWNER, STUDENT)).isFalse();
        assertThat(jobService.isStudentRelatedToOwnerJobs(OWNER, OTHER_STUDENT)).isFalse();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생이 사장님 의뢰의 선택된 학생이면 지원서가 없어도 관계가 있다")
    void relatedWhenStudentIsSelectedForOwnerJob() {
        saveJob(OWNER, STUDENT);
        // 다른 사장님 의뢰에 지원한 것은 이 사장님과의 관계가 아니다
        saveApplication(saveJob(OTHER_OWNER, null).getId(), OTHER_STUDENT, JobApplicationStatus.PENDING);

        assertThat(jobService.isStudentRelatedToOwnerJobs(OWNER, STUDENT)).isTrue();
        assertThat(jobService.isStudentRelatedToOwnerJobs(OTHER_OWNER, STUDENT)).isFalse();
        assertThat(jobService.isStudentRelatedToOwnerJobs(OWNER, OTHER_STUDENT)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ProposalStatus.class)
    @DisplayName("PostgreSQL에서 학생이 사장님에게 제안을 보냈으면 제안 상태와 무관하게 볼 수 있다")
    void visibleWhenStudentProposedToOwner(ProposalStatus status) {
        // 다른 격리 범위의 데모 제안이라 탐색 조건으로는 보이지 않고, 받은 제안 조건으로만 참이 된다
        saveProposal(STUDENT, OWNER, status, OTHER_DEMO_SESSION_ID);

        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, DEMO_SESSION_ID)).isTrue();
        assertThat(proposalService.isStudentProposalVisibleToOwner(OTHER_OWNER, STUDENT, DEMO_SESSION_ID)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "PENDING", "AWAITING_START", "ACCEPTED" })
    @DisplayName("PostgreSQL에서 학생의 제안이 탐색에 보이는 상태이고 격리 범위가 같으면 다른 사장님도 볼 수 있다")
    void visibleWhenStudentProposalIsShownInExplore(ProposalStatus status) {
        saveProposal(STUDENT, OTHER_OWNER, status, DEMO_SESSION_ID);

        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, DEMO_SESSION_ID)).isTrue();
        // 실제 사용자(격리 범위 null)와 다른 데모 세션에는 보이지 않는다
        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, null)).isFalse();
        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, OTHER_DEMO_SESSION_ID)).isFalse();
        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, OTHER_STUDENT, DEMO_SESSION_ID)).isFalse();
    }

    @Test
    @DisplayName("PostgreSQL에서 실제 사용자의 탐색 제안은 격리 범위가 null인 사장님에게 보인다")
    void visibleToRealOwnerWhenRealStudentProposalIsShownInExplore() {
        saveProposal(STUDENT, OTHER_OWNER, ProposalStatus.PENDING, null);

        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, null)).isTrue();
        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, DEMO_SESSION_ID)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "CANCELLED", "REJECTED" })
    @DisplayName("PostgreSQL에서 취소·거절되어 탐색에서 빠진 제안만 있으면 다른 사장님은 볼 수 없다")
    void hiddenWhenOnlyCancelledOrRejectedProposalsExist(ProposalStatus status) {
        saveProposal(STUDENT, OTHER_OWNER, status, DEMO_SESSION_ID);

        assertThat(proposalService.isStudentProposalVisibleToOwner(OWNER, STUDENT, DEMO_SESSION_ID)).isFalse();
    }

    private Job saveJob(long ownerProfileId, Long selectedStudentProfileId) {
        Job job = Job.create(ownerProfileId, "프로필 열람 테스트 의뢰", "설명", 50000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        if (selectedStudentProfileId != null) {
            job.match(selectedStudentProfileId);
        }
        return jobRepository.saveAndFlush(job);
    }

    private void saveApplication(Long jobId, long studentProfileId, JobApplicationStatus status) {
        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .jobId(jobId)
                .studentProfileId(studentProfileId)
                .summary("한 줄 요약")
                .workPlan("작업계획서")
                .deliveryMethod("결과물 전달 방법")
                .status(status)
                .build());
    }

    private void saveProposal(long studentProfileId, long ownerProfileId, ProposalStatus status,
            String demoSessionId) {
        Proposal saved = proposalRepository.saveAndFlush(Proposal.create(studentProfileId, ownerProfileId,
                "메뉴판 개선 제안", "문제", "해결", "계획", 50000L, 0, 7, List.of(), demoSessionId));
        entityManager.createNativeQuery("update proposals set status = :status where id = :id")
                .setParameter("status", status.name()).setParameter("id", saved.getId()).executeUpdate();
        entityManager.clear();
    }
}
