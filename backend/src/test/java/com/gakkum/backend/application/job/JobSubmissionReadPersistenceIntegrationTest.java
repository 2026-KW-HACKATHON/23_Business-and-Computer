package com.gakkum.backend.application.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;

/**
 * 제출물 조회는 읽기 전용 트랜잭션이라, 클래스 트랜잭션으로 감싸면 바깥 쓰기 트랜잭션에 합쳐져 잠금 조회 오류가 드러나지 않는다.
 * 그래서 클래스 트랜잭션 없이 실제 PostgreSQL에서 Facade를 부르고 만든 데이터는 직접 정리한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class JobSubmissionReadPersistenceIntegrationTest {

    private static final String STUDENT_NAME = "제출 조회 테스트 학생";
    private static final String DRAFT_FILE = "https://example.com/draft.png";

    @Autowired
    private JobFacade jobFacade;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private String ownerUsername;
    private String ownerUserId;
    private String studentUserId;
    private Long ownerProfileId;
    private Long studentProfileId;
    private Long jobId;
    private Long submissionId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        ownerUserId = unique.substring(0, 26);
        studentUserId = new StringBuilder(unique).reverse().substring(0, 26);
        ownerUsername = "TEST_SUBMISSION_OWNER_" + unique;
        userRepository.saveAndFlush(User.builder()
                .id(ownerUserId).username(ownerUsername).isLock(false).role(UserRole.OWNER).build());
        userRepository.saveAndFlush(User.builder()
                .id(studentUserId).username("TEST_SUBMISSION_STUDENT_" + unique).name(STUDENT_NAME)
                .isLock(false).role(UserRole.STUDENT).build());
        ownerProfileId = ownerRepository.saveAndFlush(Owner.builder()
                .userId(ownerUserId).businessNumber("TEST-" + unique).storeName("제출 조회 테스트 매장").categoryId(1L)
                .build()).getId();
        studentProfileId = studentRepository.saveAndFlush(Student.create(
                studentUserId, "광운대학교", "TEST-" + unique, "소프트웨어학부", null, null, null)).getId();

        Job job = Job.create(ownerProfileId, "제출 조회 테스트 의뢰", "설명", 100_000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2, null);
        job.match(studentProfileId);
        jobId = jobRepository.saveAndFlush(job).getId();
        submissionId = jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                jobId, JobSubmissionType.DRAFT, 0, List.of(DRAFT_FILE), "초안입니다.")).getId();
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jobSubmissionRepository.deleteAllById(List.of(submissionId));
            jobRepository.deleteAllById(List.of(jobId));
            studentRepository.deleteById(studentProfileId);
            ownerRepository.deleteById(ownerProfileId);
            userRepository.deleteAllById(List.of(ownerUserId, studentUserId));
        });
    }

    @Test
    @DisplayName("PostgreSQL에서 사장님의 검토 대기 제출물 조회는 읽기 전용 트랜잭션에서도 잠금 조회 오류 없이 제출물과 학생 이름을 돌려준다")
    void readsPendingSubmissionInReadOnlyTransaction() {
        JobSubmissionDetailResult result = jobFacade.getPendingSubmission(ownerUsername, jobId);

        assertThat(result.getSubmissionId()).isEqualTo(submissionId);
        assertThat(result.getSubmissionType()).isEqualTo(JobSubmissionType.DRAFT.name());
        assertThat(result.getStudentName()).isEqualTo(STUDENT_NAME);
        assertThat(result.getFileUrls()).containsExactly(DRAFT_FILE);
    }
}
