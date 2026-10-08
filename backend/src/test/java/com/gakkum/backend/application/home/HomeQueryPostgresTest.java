package com.gakkum.backend.application.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.home.dto.OwnerHomeResponse;
import com.gakkum.backend.application.home.facade.HomeFacade;
import com.gakkum.backend.application.home.service.HomeQueryService;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

/**
 * 원천 조회마다 트랜잭션이 따로 끝나는지는 실제 PostgreSQL에서만 확인할 수 있다. 한 트랜잭션 안에서 오류가 나면
 * PostgreSQL은 그 트랜잭션의 이후 쿼리를 모두 거부하므로, 조회가 한 트랜잭션에 묶여 있으면 뒤 섹션도 함께 실패한다.
 * 공용 DB에 테스트 행을 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하고, 커밋한 행은 테스트 뒤에 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ HomeFacade.class, HomeQueryService.class, JobService.class, ProposalService.class,
        SpecialtyCategoryService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("홈 집계 PostgreSQL 통합 (원천 조회별 독립 트랜잭션·본인 범위)")
class HomeQueryPostgresTest {

    private static final String USERNAME = "KAKAO_HOME_TEST";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2HOME1";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2HOME2";
    private static final Long STUDENT_PROFILE_ID = 987_201L;

    @Autowired
    private HomeFacade homeFacade;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProposalService proposalService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private OwnerService ownerService;
    @MockitoBean
    private StudentService studentService;
    @MockitoBean
    private PaymentFacade paymentFacade;

    private final List<Long> jobIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long jobId : jobIds) {
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    @DisplayName("받은 제안 조회에서 PostgreSQL 오류가 나도 뒤따르는 조회는 새 트랜잭션으로 성공하고 본인 의뢰만 반환한다")
    void continuesAfterPostgresErrorAndReadsOnlyOwnJobs() {
        long ownerProfileId = ThreadLocalRandom.current().nextLong(900_000_000L, 999_000_000L);
        long otherOwnerProfileId = ownerProfileId + 1;
        Long openJobId = saveJob(ownerProfileId, JobStatus.OPEN, null);
        Long matchedJobId = saveJob(ownerProfileId, JobStatus.MATCHED, STUDENT_PROFILE_ID);
        saveJob(otherOwnerProfileId, JobStatus.OPEN, null);
        saveJob(otherOwnerProfileId, JobStatus.MATCHED, STUDENT_PROFILE_ID);
        when(userService.getActiveUser(USERNAME)).thenReturn(
                User.builder().id(USER_ID).username(USERNAME).role(UserRole.OWNER).build());
        when(ownerService.getOwnerProfile(USER_ID)).thenReturn(Owner.builder().id(ownerProfileId).build());
        when(studentService.getStudentProfilesByIds(List.of(STUDENT_PROFILE_ID))).thenReturn(Map.of(
                STUDENT_PROFILE_ID, Student.builder().id(STUDENT_PROFILE_ID).userId(STUDENT_USER_ID).build()));
        when(userService.getUsersByIds(List.of(STUDENT_USER_ID))).thenReturn(Map.of(
                STUDENT_USER_ID, User.builder().id(STUDENT_USER_ID).name("김학생").build()));
        // 조회 트랜잭션 안에서 실제 SQL 오류(0으로 나누기)를 일으킨다
        doAnswer(invocation -> jdbcTemplate.queryForObject("select 1 / 0", Integer.class))
                .when(proposalService).getReceivedProposals(any());

        OwnerHomeResponse home = (OwnerHomeResponse) homeFacade.getHome(USERNAME);

        // 받은 제안에 의존하는 todos만 실패한다
        assertThat(home.getTodos()).isNull();
        assertThat(home.getWaiting()).extracting(OwnerHomeResponse.Waiting::getJobId).containsExactly(openJobId);
        // 받은 제안 뒤에 실행된 진행·종료 조회가 중단된 트랜잭션에 묶이지 않았다
        assertThat(home.getWorking()).extracting(OwnerHomeResponse.Working::getJobId).containsExactly(matchedJobId);
        assertThat(home.getWorking().get(0).getStudentName()).isEqualTo("김학생");
        assertThat(home.getDone()).isEmpty();
        assertThat(home.getFirstVisit()).isFalse();
    }

    private Long saveJob(long ownerProfileId, JobStatus status, Long selectedStudentProfileId) {
        Long jobId = jobRepository.saveAndFlush(Job.builder()
                .ownerProfileId(ownerProfileId)
                .title("홈 집계 통합 테스트 의뢰")
                .description("설명")
                .budget(50000L)
                .draftDeadline(LocalDate.of(2031, 2, 1))
                .finalDeadline(LocalDate.of(2031, 2, 10))
                .revisionCount(2)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .build()).getId();
        jobIds.add(jobId);
        return jobId;
    }
}
