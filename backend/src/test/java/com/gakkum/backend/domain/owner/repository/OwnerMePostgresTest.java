package com.gakkum.backend.domain.owner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;

/**
 * 파생 메서드가 만드는 집계 SQL의 상태 포함·제외 기준을 실제 DB로 검증한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("사장님 내 정보 PostgreSQL 집계 (보낸 의뢰·받은 제안·진행 중·완료 의뢰 수)")
class OwnerMePostgresTest {

    private static final long OWNER_PROFILE_ID = 988_001L;
    private static final long OTHER_OWNER_PROFILE_ID = 988_002L;
    private static final long INACTIVE_OWNER_PROFILE_ID = 988_099L;
    private static final long STUDENT_PROFILE_ID = 988_101L;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Test
    @DisplayName("보낸 의뢰 수는 CANCELLED만 빼고 모든 상태를 세고, 진행 중은 MATCHED만, 완료는 CLOSED만 센다")
    void countsOwnerJobsByStatus() {
        for (JobStatus status : JobStatus.values()) {
            job(OWNER_PROFILE_ID, status, null);
        }
        job(OWNER_PROFILE_ID, JobStatus.MATCHED, null);
        job(OWNER_PROFILE_ID, JobStatus.CANCELLED, null);

        assertThat(jobRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, JobStatus.CANCELLED))
                .isEqualTo(5);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.MATCHED)).isEqualTo(2);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.CLOSED)).isEqualTo(1);
    }

    @Test
    @DisplayName("제안 수락·결제로 생긴 의뢰도 직접 등록한 의뢰와 함께 세고, 그중 취소된 의뢰는 뺀다")
    void countsJobsCreatedFromProposals() {
        job(OWNER_PROFILE_ID, JobStatus.OPEN, null);
        job(OWNER_PROFILE_ID, JobStatus.AWAITING_START, 988_501L);
        job(OWNER_PROFILE_ID, JobStatus.MATCHED, 988_502L);
        job(OWNER_PROFILE_ID, JobStatus.CLOSED, 988_503L);
        job(OWNER_PROFILE_ID, JobStatus.CANCELLED, 988_504L);

        assertThat(jobRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, JobStatus.CANCELLED))
                .isEqualTo(4);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.MATCHED)).isEqualTo(1);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.CLOSED)).isEqualTo(1);
    }

    @Test
    @DisplayName("받은 제안 수는 CANCELLED만 빼고 나머지 상태를 모두 센다")
    void countsReceivedProposalsExcludingCancelled() {
        for (ProposalStatus status : ProposalStatus.values()) {
            proposal(OWNER_PROFILE_ID, status);
        }
        proposal(OWNER_PROFILE_ID, ProposalStatus.CANCELLED);

        assertThat(proposalRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, ProposalStatus.CANCELLED))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("다른 사장님의 의뢰와 제안은 세지 않고, 활동이 없는 사장님은 모두 0이다")
    void excludesOtherOwners() {
        job(OWNER_PROFILE_ID, JobStatus.MATCHED, null);
        proposal(OWNER_PROFILE_ID, ProposalStatus.PENDING);
        for (JobStatus status : JobStatus.values()) {
            job(OTHER_OWNER_PROFILE_ID, status, null);
        }
        for (ProposalStatus status : ProposalStatus.values()) {
            proposal(OTHER_OWNER_PROFILE_ID, status);
        }

        assertThat(jobRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, JobStatus.CANCELLED))
                .isEqualTo(1);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.MATCHED)).isEqualTo(1);
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(OWNER_PROFILE_ID, JobStatus.CLOSED)).isZero();
        assertThat(proposalRepository.countByOwnerProfileIdAndStatusNot(OWNER_PROFILE_ID, ProposalStatus.CANCELLED))
                .isEqualTo(1);

        assertThat(jobRepository.countByOwnerProfileIdAndStatusNot(INACTIVE_OWNER_PROFILE_ID, JobStatus.CANCELLED))
                .isZero();
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(INACTIVE_OWNER_PROFILE_ID, JobStatus.MATCHED))
                .isZero();
        assertThat(jobRepository.countByOwnerProfileIdAndStatus(INACTIVE_OWNER_PROFILE_ID, JobStatus.CLOSED))
                .isZero();
        assertThat(proposalRepository.countByOwnerProfileIdAndStatusNot(
                INACTIVE_OWNER_PROFILE_ID, ProposalStatus.CANCELLED)).isZero();
    }

    private void job(long ownerProfileId, JobStatus status, Long proposalId) {
        jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(ownerProfileId)
                .title("사장님 내 정보 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(1)
                .status(status)
                .proposalId(proposalId)
                .build());
    }

    private void proposal(long ownerProfileId, ProposalStatus status) {
        proposalRepository.saveAndFlush(Proposal.builder()
                .studentProfileId(STUDENT_PROFILE_ID)
                .ownerProfileId(ownerProfileId)
                .title("사장님 내 정보 테스트 제안")
                .customerProblem("문제")
                .proposedSolution("해결")
                .workPlan("작업 계획")
                .proposedFee(80000L)
                .draftDays(3)
                .finalDays(7)
                .referenceImageUrls(List.of())
                .likeCount(0)
                .status(status)
                .build());
    }
}
