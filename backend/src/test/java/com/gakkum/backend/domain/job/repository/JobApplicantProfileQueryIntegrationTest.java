package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.gakkum.backend.domain.certificate.entity.StudentCertificate;
import com.gakkum.backend.domain.certificate.repository.StudentCertificateRepository;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.repository.ReviewRepository;
import com.gakkum.backend.domain.review.service.ReviewService;

/** 각 테스트는 트랜잭션 안에서 실행되고 끝나면 롤백된다. 학생 ID는 실제 데이터와 겹치지 않는 값을 쓴다. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JobApplicantProfileQueryIntegrationTest {

    private static final long OWNER_PROFILE_ID = 988_005L;
    private static final long STUDENT = 988_001L;
    private static final long OTHER_STUDENT = 988_002L;
    private static final long NO_DATA_STUDENT = 988_003L;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private StudentCertificateRepository studentCertificateRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JobService jobService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("PostgreSQL에서 학생이 보낸 제안을 모든 상태로 세고 의뢰 지원과 다른 학생의 제안은 세지 않는다")
    void countsProposalsOfAllStatuses() {
        for (ProposalStatus status : ProposalStatus.values()) {
            Proposal saved = saveProposal(STUDENT);
            entityManager.createNativeQuery("update proposals set status = :status where id = :id")
                    .setParameter("status", status.name()).setParameter("id", saved.getId()).executeUpdate();
        }
        saveProposal(OTHER_STUDENT);
        jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .jobId(saveJob(null, false, false).getId())
                .studentProfileId(STUDENT)
                .summary("한 줄 요약")
                .workPlan("작업계획서")
                .deliveryMethod("결과물 전달 방법")
                .status(JobApplicationStatus.PENDING)
                .build());

        assertThat(proposalService.countProposals(STUDENT)).isEqualTo((long) ProposalStatus.values().length);
        assertThat(proposalService.countProposals(OTHER_STUDENT)).isEqualTo(1L);
        assertThat(proposalService.countProposals(NO_DATA_STUDENT)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생의 완료 의뢰만 세고 진행 중·취소 건과 다른 학생의 의뢰는 세지 않는다")
    void countsOnlyClosedJobs() {
        saveJob(STUDENT, true, false);
        saveJob(STUDENT, true, false);
        saveJob(STUDENT, false, false);
        saveJob(STUDENT, false, true);
        saveJob(OTHER_STUDENT, true, false);

        assertThat(jobService.countClosedJobs(STUDENT)).isEqualTo(2L);
        assertThat(jobService.countClosedJobs(NO_DATA_STUDENT)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생의 자격증만 취득년도 내림차순, 같은 년도는 자격증 ID 내림차순으로 조회한다")
    void findsCertificatesInOrder() {
        StudentCertificate older = saveCertificate(STUDENT, "SQLD", 2023);
        StudentCertificate sameYearFirst = saveCertificate(STUDENT, "정보처리기사", 2025);
        StudentCertificate sameYearSecond = saveCertificate(STUDENT, "리눅스마스터", 2025);
        saveCertificate(OTHER_STUDENT, "컴퓨터활용능력", 2026);

        assertThat(certificateService.getStudentCertificates(STUDENT))
                .extracting(StudentCertificate::getId)
                .containsExactly(sameYearSecond.getId(), sameYearFirst.getId(), older.getId());
        assertThat(certificateService.getStudentCertificates(NO_DATA_STUDENT)).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL에서 학생이 모든 사장님에게 받은 리뷰만 최신순으로 조회하고 작성 시각이 없으면 마지막에 둔다")
    void findsReviewsOfStudentInOrder() {
        Review withoutCreatedAt = saveReview(OWNER_PROFILE_ID, STUDENT);
        Review first = saveReview(OWNER_PROFILE_ID, STUDENT);
        Review second = saveReview(988_006L, STUDENT);
        saveReview(OWNER_PROFILE_ID, OTHER_STUDENT);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        entityManager.createNativeQuery("update reviews set created_at = null where id = :id")
                .setParameter("id", withoutCreatedAt.getId()).executeUpdate();
        // 작성 시각이 같으면 리뷰 ID 내림차순이다
        entityManager.createNativeQuery("update reviews set created_at = :createdAt where id in (:ids)")
                .setParameter("createdAt", createdAt)
                .setParameter("ids", List.of(first.getId(), second.getId()))
                .executeUpdate();
        entityManager.clear();

        assertThat(reviewService.getStudentReviews(STUDENT))
                .extracting(Review::getId)
                .containsExactly(second.getId(), first.getId(), withoutCreatedAt.getId());
        assertThat(reviewService.getStudentReviews(NO_DATA_STUDENT)).isEmpty();
    }

    private Proposal saveProposal(long studentProfileId) {
        return proposalRepository.saveAndFlush(Proposal.create(studentProfileId, OWNER_PROFILE_ID, "메뉴판 개선 제안",
                "문제", "해결", "계획", 50000L, 0, 7, List.of(), null));
    }

    private StudentCertificate saveCertificate(long studentProfileId, String name, int acquiredYear) {
        return studentCertificateRepository.saveAndFlush(
                StudentCertificate.create(studentProfileId, name, acquiredYear));
    }

    /** 리뷰는 의뢰당 하나만 저장할 수 있어 리뷰마다 완료 의뢰를 새로 만든다. */
    private Review saveReview(long ownerProfileId, long studentProfileId) {
        Job job = saveJob(studentProfileId, true, false);
        return reviewRepository.saveAndFlush(
                Review.create(job.getId(), ownerProfileId, studentProfileId, List.of(), "리뷰", 5));
    }

    private Job saveJob(Long studentProfileId, boolean closed, boolean cancelled) {
        Job job = Job.create(OWNER_PROFILE_ID, "지원자 프로필 테스트 의뢰", "설명", 50000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        if (studentProfileId != null) {
            job.match(studentProfileId);
        }
        if (closed) {
            job.complete(LocalDateTime.now());
        }
        if (cancelled) {
            job.cancel(LocalDateTime.now(), "취소 이유", "남길 말");
        }
        return jobRepository.saveAndFlush(job);
    }
}
