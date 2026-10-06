package com.gakkum.backend.application.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.gakkum.backend.domain.category.entity.BusinessCategory;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.GetExploreStoresCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.util.UlidGenerator;

import jakarta.persistence.EntityManager;

/**
 * 격리 범위(demo_session_id) 조건은 NULL 비교가 섞여 PostgreSQL에서만 확인할 수 있어 실제 DB로 검증한다.
 * 실제 데이터 한 벌과 데모 세션 A·B 한 벌씩을 서비스로 만들고, 탐색 쿼리 20개가 모두 조회자의 범위만 읽는지 본다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하며, 트랜잭션은 테스트마다 롤백된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("데모 데이터 격리 PostgreSQL 조회 (탐색 목록·생성 전파·데모 계정)")
class DemoIsolationPostgresTest {

    private static final LocalDateTime LATEST_START = LocalDateTime.of(3000, 1, 1, 0, 0);
    private static final LocalDateTime OLDEST_START = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final int LIMIT = 1000;

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobSpecialtyRepository jobSpecialtyRepository;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private JobSubmissionRepository jobSubmissionRepository;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessCategoryRepository businessCategoryRepository;
    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private EntityManager entityManager;

    private JobService jobService;
    private ProposalService proposalService;
    private OwnerService ownerService;
    private UserService userService;

    private final String sessionA = UlidGenerator.generate();
    private final String sessionB = UlidGenerator.generate();
    private Long businessCategoryId;
    private Long specialtyCategoryId;
    private Long specialtyId;
    private Data real;
    private Data demoA;
    private Data demoB;

    /** 한 격리 범위에 만든 매장·의뢰·제안의 ID. */
    private record Data(Long storeId, Long jobId, Long proposalId) {
    }

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobSpecialtyRepository, jobApplicationRepository,
                jobSubmissionRepository, Clock.systemUTC());
        proposalService = new ProposalService(proposalRepository, proposalSpecialtyRepository,
                mock(ProposalLikeRepository.class));
        ownerService = new OwnerService(ownerRepository);
        userService = new UserService(userRepository, mock(JwtService.class));

        String suffix = UUID.randomUUID().toString();
        businessCategoryId = businessCategoryRepository.saveAndFlush(
                BusinessCategory.builder().name("격리 업종 " + suffix).build()).getId();
        specialtyCategoryId = specialtyCategoryRepository.saveAndFlush(
                SpecialtyCategory.builder().name("격리 대분류 " + suffix).build()).getId();
        specialtyId = specialtyRepository.saveAndFlush(
                Specialty.builder().specialtyCategoryId(specialtyCategoryId).name("격리 소분류").build()).getId();

        real = seed(null);
        demoA = seed(sessionA);
        demoB = seed(sessionB);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("의뢰 탐색은 정렬·대분류 조합 모두에서 조회자와 격리 범위가 같은 의뢰만 읽는다")
    void exploresJobsWithinViewerDemoSession() {
        for (boolean oldestFirst : List.of(false, true)) {
            for (Long categoryId : new Long[] {null, specialtyCategoryId}) {
                assertThat(jobIds(sessionA, categoryId, oldestFirst)).containsExactly(demoA.jobId());
                assertThat(jobIds(sessionB, categoryId, oldestFirst)).containsExactly(demoB.jobId());
                assertThat(jobIds(null, categoryId, oldestFirst))
                        .contains(real.jobId()).doesNotContain(demoA.jobId(), demoB.jobId());
            }
            assertThat(jobIds(null, specialtyCategoryId, oldestFirst)).containsExactly(real.jobId());
        }
    }

    @Test
    @DisplayName("제안 탐색은 최신순·오래된순·좋아요순과 대분류 조합 모두에서 조회자와 격리 범위가 같은 제안만 읽는다")
    void exploresProposalsWithinViewerDemoSession() {
        for (ProposalExploreOrder order : ProposalExploreOrder.values()) {
            for (Long categoryId : new Long[] {null, specialtyCategoryId}) {
                assertThat(proposalIds(sessionA, categoryId, order)).containsExactly(demoA.proposalId());
                assertThat(proposalIds(sessionB, categoryId, order)).containsExactly(demoB.proposalId());
                assertThat(proposalIds(null, categoryId, order))
                        .contains(real.proposalId()).doesNotContain(demoA.proposalId(), demoB.proposalId());
            }
            assertThat(proposalIds(null, specialtyCategoryId, order)).containsExactly(real.proposalId());
        }
    }

    @Test
    @DisplayName("매장 탐색은 정렬·업종 조합 모두에서 조회자와 격리 범위가 같은 매장만 읽는다")
    void exploresStoresWithinViewerDemoSession() {
        for (boolean oldestFirst : List.of(false, true)) {
            for (Long categoryId : new Long[] {null, businessCategoryId}) {
                assertThat(storeIds(sessionA, categoryId, oldestFirst)).containsExactly(demoA.storeId());
                assertThat(storeIds(sessionB, categoryId, oldestFirst)).containsExactly(demoB.storeId());
                assertThat(storeIds(null, categoryId, oldestFirst))
                        .contains(real.storeId()).doesNotContain(demoA.storeId(), demoB.storeId());
            }
            assertThat(storeIds(null, businessCategoryId, oldestFirst)).containsExactly(real.storeId());
        }
    }

    @Test
    @DisplayName("의뢰·제안·매장은 만든 쪽의 격리 범위로 저장되고 제안으로 만든 의뢰는 제안의 범위를 따른다")
    void storesCreatorDemoSession() {
        assertThat(ownerRepository.findById(demoA.storeId()).orElseThrow().getDemoSessionId()).isEqualTo(sessionA);
        assertThat(jobRepository.findById(demoA.jobId()).orElseThrow().getDemoSessionId()).isEqualTo(sessionA);
        Proposal proposalA = proposalRepository.findById(demoA.proposalId()).orElseThrow();
        assertThat(proposalA.getDemoSessionId()).isEqualTo(sessionA);
        assertThat(ownerRepository.findById(real.storeId()).orElseThrow().getDemoSessionId()).isNull();
        assertThat(jobRepository.findById(real.jobId()).orElseThrow().getDemoSessionId()).isNull();
        Proposal realProposal = proposalRepository.findById(real.proposalId()).orElseThrow();
        assertThat(realProposal.getDemoSessionId()).isNull();

        Long demoJobId = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                proposalA, List.of(specialtyId), LocalDate.of(2031, 1, 1), 1, "잘 부탁드립니다")).getId();
        Long realJobId = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                realProposal, List.of(specialtyId), LocalDate.of(2031, 1, 1), 1, "잘 부탁드립니다")).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(jobRepository.findById(demoJobId).orElseThrow().getDemoSessionId()).isEqualTo(sessionA);
        assertThat(jobRepository.findById(realJobId).orElseThrow().getDemoSessionId()).isNull();
    }

    @Test
    @DisplayName("데모 계정은 세션과 역할로 조회되고 다른 세션에서는 인증 오류이며 세션 수는 데모 사장님 기준으로 센다")
    void findsAndCountsDemoUsers() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        long countBefore = userService.countDemoSessionsCreatedAfter(before);
        long realUsersBefore = userRepository.count();

        User owner = userService.createDemoUser(sessionA, UserRole.OWNER, "데모 사장님", null);
        User student = userService.createDemoUser(sessionA, UserRole.STUDENT, "데모 학생",
                "demo-" + sessionA.toLowerCase() + "@example.com");
        entityManager.flush();
        entityManager.clear();

        assertThat(owner.getUsername()).isEqualTo("DEMO_" + sessionA + "_OWNER");
        assertThat(userService.getDemoUser(sessionA, UserRole.OWNER).getId()).isEqualTo(owner.getId());
        User foundStudent = userService.getDemoUser(sessionA, UserRole.STUDENT);
        assertThat(foundStudent.getId()).isEqualTo(student.getId());
        assertThat(foundStudent.getDemoSessionId()).isEqualTo(sessionA);
        assertThat(foundStudent.getRole()).isEqualTo(UserRole.STUDENT);
        assertThatThrownBy(() -> userService.getDemoUser(sessionB, UserRole.OWNER))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(userRepository.count()).isEqualTo(realUsersBefore + 2);
        assertThat(userService.countDemoSessionsCreatedAfter(before)).isEqualTo(countBefore + 1);
        assertThat(userService.countDemoSessionsCreatedAfter(LocalDateTime.now().plusHours(1))).isZero();
    }

    private Data seed(String demoSessionId) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Owner store = ownerService.createOwnerProfile(CreateOwnerProfileCommand.of(
                unique.substring(0, 26), unique, null, null, "격리 매장", businessCategoryId, null, null, null,
                List.of()), demoSessionId);
        Job job = jobService.createJob(CreateJobCommand.of(
                store.getId(), List.of(specialtyId), "격리 의뢰", "설명", 50000L,
                LocalDate.of(2031, 1, 10), LocalDate.of(2031, 1, 20), 1), demoSessionId);
        Proposal proposal = proposalService.createProposal(CreateProposalCommand.of(
                "KAKAO_12345", store.getId(), List.of(specialtyId), "격리 제안", "문제", "해결", "계획", 50000L, 3, 7,
                List.of()), 7L, demoSessionId);
        return new Data(store.getId(), job.getId(), proposal.getId());
    }

    private List<Long> jobIds(String demoSessionId, Long categoryId, boolean oldestFirst) {
        return jobService.getExploreJobs(GetExploreJobsCommand.of(demoSessionId, categoryId, oldestFirst,
                        oldestFirst ? OLDEST_START : LATEST_START, oldestFirst ? Long.MIN_VALUE : Long.MAX_VALUE, LIMIT))
                .stream().map(data -> data.getJob().getId()).toList();
    }

    private List<Long> proposalIds(String demoSessionId, Long categoryId, ProposalExploreOrder order) {
        boolean oldestFirst = order == ProposalExploreOrder.OLDEST;
        return proposalService.getExploreProposals(GetExploreProposalsCommand.of(demoSessionId, categoryId, order,
                        order == ProposalExploreOrder.LIKES ? Integer.MAX_VALUE : null,
                        oldestFirst ? OLDEST_START : LATEST_START, oldestFirst ? Long.MIN_VALUE : Long.MAX_VALUE, LIMIT))
                .stream().map(data -> data.getProposal().getId()).toList();
    }

    private List<Long> storeIds(String demoSessionId, Long categoryId, boolean oldestFirst) {
        return ownerService.getExploreStores(GetExploreStoresCommand.of(demoSessionId, categoryId, oldestFirst,
                        oldestFirst ? OLDEST_START : LATEST_START, oldestFirst ? Long.MIN_VALUE : Long.MAX_VALUE, LIMIT))
                .stream().map(Owner::getId).toList();
    }
}
