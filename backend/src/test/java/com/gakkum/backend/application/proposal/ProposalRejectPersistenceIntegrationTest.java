package com.gakkum.backend.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.client.KakaoPayClient;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.repository.PaymentRepository;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetExploreProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalExploreOrder;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalRejectResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.entity.ProposalSpecialty;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 사장님 제안 거절의 커밋·롤백·행 잠금과 결제·취소·공감과의 순서를 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 요청마다 별도 트랜잭션으로 실행되고, 운영 데이터를 건드리지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ PaymentApprovalService.class, PaymentPreparationService.class, PaymentFacade.class, ProposalFacade.class,
        ProposalService.class, JobService.class, PaymentService.class, ChatRoomService.class,
        ProposalRejectPersistenceIntegrationTest.MutableClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("사장님 제안 거절 PostgreSQL 통합 (저장·조회 유지·격리·결제 연계·공감 차단·동시성·롤백)")
class ProposalRejectPersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_405L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2RJCOW";
    private static final String OWNER_USERNAME = "TEST_REJECT_OWNER";
    // 1번 학생이 제안의 작성자다
    private static final long AUTHOR_PROFILE_ID = profileId(1);
    private static final String DEMO_SESSION = "01K6DEMORJCT0000000000000A";
    private static final String OTHER_DEMO_SESSION = "01K6DEMORJCT0000000000000B";
    private static final List<String> IMAGE_URLS = List.of("https://bucket/b.png", "https://bucket/a.png");
    private static final Instant APPROVED_AT = Instant.parse("2026-08-31T15:30:00Z");
    // UTC 9월 1일 15:30 = 한국 9월 2일 00:30. 나노초는 저장 정밀도(마이크로초)에 맞춰 잘린다
    private static final Instant REJECTED_AT = Instant.parse("2026-09-01T15:30:00.123456789Z");
    private static final LocalDateTime REJECTED_AT_UTC = LocalDateTime.of(2026, 9, 1, 15, 30, 0, 123_456_000);
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(REJECTED_AT);

    @Autowired
    private ProposalFacade proposalFacade;

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentApprovalService approvalService;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ProposalSpecialtyRepository proposalSpecialtyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentPreparationService preparationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private ProposalService proposalService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private KakaoPayClient kakaoPayClient;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ReviewService reviewService;

    private final List<Long> proposalIds = new ArrayList<>();
    private String author;
    private String owner;

    /** 거절 시각을 고정하고 테스트 중에 옮길 수 있는 시계. 최초 거절 시각이 보존되는지 확인하는 데 쓴다. */
    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        Clock clock() {
            return new Clock() {
                @Override
                public java.time.ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return Clock.fixed(NOW.get(), zone);
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        author = givenStudent(1, null);
        NOW.set(REJECTED_AT);
        owner = givenOwner(1, null);
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(Owner.builder().id(OWNER_PROFILE_ID).build());
        when(kakaoPayClient.cid()).thenReturn("TC0ONETIME");
    }

    @AfterEach
    void cleanUp() {
        reset(proposalService);
        for (Long proposalId : proposalIds) {
            jdbcTemplate.update(
                    "delete from job_specialties where job_id in (select id from jobs where proposal_id = ?)",
                    proposalId);
            jdbcTemplate.update("delete from payments where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from jobs where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposal_likes where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposal_specialties where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposals where id = ?", proposalId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 결제 전 받은 제안을 거절하면 상태가 거절로 바뀌고 거절 주체와 UTC 거절 시각이 저장되며 공감·소분류·사진은 남는다")
    void persistsReject() {
        Long proposalId = saveProposal(null);
        Long otherProposalId = saveProposal(null);
        proposalFacade.likeProposal(author, proposalId);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);

        ProposalRejectResult result = proposalFacade.rejectProposal(owner, proposalId);

        assertThat(result.getProposalId()).isEqualTo(proposalId);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertRejectedByOwner(proposalId);
        assertThat(jdbcTemplate.queryForObject("select rejected_by from proposals where id = ?", String.class,
                proposalId)).isEqualTo("OWNER");
        // 기존 공감 기록과 공감 수, 소분류·사진은 그대로다
        assertThat(likeCount(proposalId)).isEqualTo(2);
        assertThat(likedProfileIds(proposalId)).containsExactlyInAnyOrder(profileId(1), profileId(2));
        Proposal found = proposalRepository.findById(proposalId).orElseThrow();
        assertThat(found.getReferenceImageUrls()).containsExactlyElementsOf(IMAGE_URLS);
        assertThat(specialtyIds(proposalId)).containsExactlyInAnyOrder(3L, 11L);
        // 결제·의뢰는 만들거나 바꾸지 않고, 다른 제안은 그대로다
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposalId)).isZero();
        assertPendingWithoutRejection(otherProposalId);
    }

    @Test
    @DisplayName("PostgreSQL에서 본인이 이미 거절한 제안의 반복 거절은 같은 결과로 성공하고 최초 거절 시각을 보존한다")
    void repeatsRejectKeepingFirstRejectedAt() {
        Long proposalId = saveProposal(null);
        proposalFacade.rejectProposal(owner, proposalId);
        NOW.set(REJECTED_AT.plusSeconds(3600));

        ProposalRejectResult repeated = proposalFacade.rejectProposal(owner, proposalId);

        assertThat(repeated.getProposalId()).isEqualTo(proposalId);
        assertThat(repeated.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertRejectedByOwner(proposalId);
    }

    @ParameterizedTest(name = "{0} / 거절 주체 {1}")
    @CsvSource(value = { "AWAITING_START,NULL", "ACCEPTED,NULL", "CANCELLED,NULL", "REJECTED,STUDENT",
            "REJECTED,NULL" }, nullValues = "NULL")
    @DisplayName("PostgreSQL에서 결제됐거나 취소된 제안, 학생이 거절했거나 거절 주체가 없는 기존 거절 제안의 거절은 PROPOSAL_409_REJECT로 거부하고 상태와 거절 기록을 그대로 둔다")
    void rejectsRejectOfUnavailableStatus(String status, String rejectedBy) {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);
        jdbcTemplate.update("update proposals set status = ?, rejected_by = ? where id = ?",
                status, rejectedBy, proposalId);

        assertError(() -> proposalFacade.rejectProposal(owner, proposalId), ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);

        assertThat(status(proposalId)).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject("select rejected_by from proposals where id = ?", String.class,
                proposalId)).isEqualTo(rejectedBy);
        assertThat(count("select count(*) from proposals where id = ? and rejected_at is null", proposalId))
                .isEqualTo(1);
        assertThat(likeCount(proposalId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "거래번호 있음 {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("PostgreSQL에서 결제 대기 주문이 있으면 거래번호 유무와 관계없이 PROPOSAL_409_REJECT_PAYMENT_PENDING으로 거부하고 제안·주문을 그대로 둔다")
    void rejectsRejectWithPendingPayment(boolean withTid) {
        Long proposalId = saveProposal(null);
        String orderId = savePendingPayment(proposalId, withTid ? newTid() : null);

        assertError(() -> proposalFacade.rejectProposal(owner, proposalId),
                ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING);

        assertPendingWithoutRejection(proposalId);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PENDING' "
                + "and order_id = ?", proposalId, orderId)).isEqualTo(1);
        // 거절은 외부 결제 호출을 하지 않는다
        org.mockito.Mockito.verify(kakaoPayClient, org.mockito.Mockito.never()).ready(any());
        org.mockito.Mockito.verify(kakaoPayClient, org.mockito.Mockito.never()).order(any());
    }

    @Test
    @DisplayName("PostgreSQL에서 대기 주문 없이 대체·준비 실패한 주문만 남은 제안은 거절되고 그 주문은 바뀌지 않는다")
    void rejectsWithOnlyClosedPayments() {
        Long proposalId = saveProposal(null);
        String superseded = savePendingPayment(proposalId, null);
        jdbcTemplate.update("update payments set status = 'SUPERSEDED' where order_id = ?", superseded);
        String failed = savePendingPayment(proposalId, null);
        jdbcTemplate.update("update payments set status = 'READY_FAILED' where order_id = ?", failed);

        proposalFacade.rejectProposal(owner, proposalId);

        assertRejectedByOwner(proposalId);
        assertThat(jdbcTemplate.queryForList(
                "select status from payments where proposal_id = ? order by id", String.class, proposalId))
                .containsExactly("SUPERSEDED", "READY_FAILED");
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 사장님은 PROPOSAL_403_REJECT, 격리 범위가 다른 사용자는 받은 사장님 여부와 무관하게 PROPOSAL_404로 거부하고 제안을 그대로 둔다")
    void rejectsOtherOwnerAndIsolatedSession() {
        Long proposalId = saveProposal(DEMO_SESSION);
        String demoOwner = givenOwner(1, DEMO_SESSION);
        long missingProposalId = maxProposalId() + 1_000_000L;

        // 프로필은 받은 사장님과 같지만 격리 범위가 다른 사장님
        assertError(() -> proposalFacade.rejectProposal(owner, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.rejectProposal(givenOwner(1, OTHER_DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_NOT_FOUND);
        // 격리 범위 검사가 받은 사장님 검사보다 먼저다
        assertError(() -> proposalFacade.rejectProposal(givenOwner(2, null), proposalId),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.rejectProposal(givenOwner(2, DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        assertError(() -> proposalFacade.rejectProposal(demoOwner, missingProposalId), ErrorCode.PROPOSAL_NOT_FOUND);
        // 학생은 제안한 본인이어도 거절할 수 없다
        assertError(() -> proposalFacade.rejectProposal(givenStudent(1, DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        assertPendingWithoutRejection(proposalId);

        // 다른 사장님은 이미 거절된 제안에도 반복 성공이 아닌 403을 받는다
        proposalFacade.rejectProposal(demoOwner, proposalId);
        assertError(() -> proposalFacade.rejectProposal(givenOwner(2, DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_REJECT_FORBIDDEN);
        assertRejectedByOwner(proposalId);
    }

    @Test
    @DisplayName("PostgreSQL에서 거절된 제안은 양쪽 목록과 탐색의 모든 정렬·후속 커서·대분류 필터에 거절 상태로 남고 제안 수에도 남는다")
    void keepsRejectedProposalInListsAndExplore() {
        // 세 제안의 생성 시각과 공감 수가 모두 달라 가운데 제안의 자리가 드러난다
        Long oldest = saveProposal(DEMO_SESSION, LocalDateTime.of(2032, 3, 1, 9, 0), 1);
        Long middle = saveProposal(DEMO_SESSION, LocalDateTime.of(2032, 3, 2, 9, 0), 2);
        Long newest = saveProposal(DEMO_SESSION, LocalDateTime.of(2032, 3, 3, 9, 0), 3);
        long countBefore = proposalService.countProposalsExcludingCancelled(AUTHOR_PROFILE_ID);
        long receivedBefore = proposalService.countReceivedProposalsExcludingCancelled(OWNER_PROFILE_ID);

        proposalFacade.rejectProposal(givenOwner(1, DEMO_SESSION), middle);

        List<ExploreProposalData> mine = proposalService.getMyProposals(GetMyProposalsCommand.of(AUTHOR_PROFILE_ID));
        assertThat(ids(mine)).containsSubsequence(newest, middle, oldest);
        List<ExploreProposalData> received =
                proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(OWNER_PROFILE_ID));
        assertThat(ids(received)).containsSubsequence(newest, middle, oldest);
        for (List<ExploreProposalData> list : List.of(mine, received)) {
            assertThat(list).filteredOn(data -> data.getProposal().getId().equals(middle))
                    .singleElement().satisfies(data -> {
                        assertThat(data.getProposal().getStatus()).isEqualTo(ProposalStatus.REJECTED);
                        assertThat(data.getProposal().getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
                        assertThat(data.getProposal().getRejectedAt()).isEqualTo(REJECTED_AT_UTC);
                    });
            assertThat(list).filteredOn(data -> data.getProposal().getId().equals(newest))
                    .singleElement().satisfies(data -> {
                        assertThat(data.getProposal().getRejectedBy()).isNull();
                        assertThat(data.getProposal().getRejectedAt()).isNull();
                    });
        }
        assertThat(proposalService.countProposalsExcludingCancelled(AUTHOR_PROFILE_ID)).isEqualTo(countBefore);
        assertThat(proposalService.countReceivedProposalsExcludingCancelled(OWNER_PROFILE_ID))
                .isEqualTo(receivedBefore);

        LocalDateTime latestStart = LocalDateTime.of(9999, 1, 1, 0, 0);
        LocalDateTime oldestStart = LocalDateTime.of(1970, 1, 1, 0, 0);
        assertThat(explore(null, ProposalExploreOrder.LATEST, null, latestStart, Long.MAX_VALUE, 3))
                .containsExactly(newest, middle, oldest);
        assertThat(explore(null, ProposalExploreOrder.OLDEST, null, oldestStart, Long.MIN_VALUE, 3))
                .containsExactly(oldest, middle, newest);
        assertThat(explore(null, ProposalExploreOrder.LIKES, Integer.MAX_VALUE, latestStart, Long.MAX_VALUE, 3))
                .containsExactly(newest, middle, oldest);
        // 첫 카드 뒤의 커서 구간에서도 거절된 제안이 제자리에 나온다
        assertThat(explore(null, ProposalExploreOrder.LATEST, null, LocalDateTime.of(2032, 3, 3, 9, 0), newest, 1))
                .containsExactly(middle);
        assertThat(explore(null, ProposalExploreOrder.OLDEST, null, LocalDateTime.of(2032, 3, 1, 9, 0), oldest, 1))
                .containsExactly(middle);
        assertThat(explore(null, ProposalExploreOrder.LIKES, 3, LocalDateTime.of(2032, 3, 3, 9, 0), newest, 1))
                .containsExactly(middle);
        assertThat(proposalService.getExploreProposals(GetExploreProposalsCommand.of(DEMO_SESSION, null,
                ProposalExploreOrder.LATEST, null, LocalDateTime.of(2032, 3, 3, 9, 0), newest, 1)))
                .singleElement().satisfies(data ->
                        assertThat(data.getProposal().getStatus()).isEqualTo(ProposalStatus.REJECTED));
        // 세 제안에 새 대분류의 소분류를 연결해 대분류 필터에서도 남는지 확인한다
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into specialty_categories (name) values (?) returning id", Long.class,
                "거절-" + UUID.randomUUID());
        Long specialtyId = jdbcTemplate.queryForObject(
                "insert into specialties (specialty_category_id, name) values (?, ?) returning id", Long.class,
                categoryId, "거절-" + UUID.randomUUID());
        try {
            for (Long proposalId : List.of(oldest, middle, newest)) {
                // 새 소분류 ID가 제안에 이미 연결한 고정 ID(3, 11)와 겹치는 빈 DB에서도 실패하지 않게 한다
                jdbcTemplate.update("insert into proposal_specialties (proposal_id, specialty_id) values (?, ?) "
                        + "on conflict do nothing", proposalId, specialtyId);
            }
            assertThat(explore(categoryId, ProposalExploreOrder.LATEST, null, latestStart, Long.MAX_VALUE, 3))
                    .containsExactly(newest, middle, oldest);
            assertThat(explore(categoryId, ProposalExploreOrder.OLDEST, null, oldestStart, Long.MIN_VALUE, 3))
                    .containsExactly(oldest, middle, newest);
            assertThat(explore(categoryId, ProposalExploreOrder.LIKES, 3, LocalDateTime.of(2032, 3, 3, 9, 0),
                    newest, 1)).containsExactly(middle);
        } finally {
            jdbcTemplate.update("delete from specialties where id = ?", specialtyId);
            jdbcTemplate.update("delete from specialty_categories where id = ?", categoryId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 거절된 제안 상세는 받은 사장님·제안한 학생·다른 학생 모두에게 거절 상태와 거절 주체·원본 거절 시각을 내리고 공감 수를 유지한다")
    void showsRejectedDetailToEveryone() {
        Long proposalId = saveProposalWithoutSpecialties(null);
        String otherStudent = givenStudent(2, null);
        proposalFacade.likeProposal(author, proposalId);
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(
                Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
        when(studentService.getStudentProfile(AUTHOR_PROFILE_ID)).thenReturn(
                Student.builder().id(AUTHOR_PROFILE_ID).userId(userId(1)).build());
        when(userService.getUser(userId(1))).thenReturn(User.builder().id(userId(1)).name("김학생").build());
        ProposalDetailResult before = proposalFacade.getProposalDetail(owner, proposalId);
        assertThat(before.getRejectedBy()).isNull();
        assertThat(before.getRejectedAt()).isNull();

        proposalFacade.rejectProposal(owner, proposalId);

        for (String viewer : List.of(owner, author, otherStudent)) {
            ProposalDetailResult detail = proposalFacade.getProposalDetail(viewer, proposalId);
            assertThat(detail.getStatus()).isEqualTo(ProposalStatus.REJECTED);
            assertThat(detail.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
            assertThat(detail.getRejectedAt()).isEqualTo(REJECTED_AT_UTC);
            assertThat(detail.getLikeCount()).isEqualTo(1);
            assertThat(detail.getEstimatedDraftDeadline()).isNull();
            assertThat(detail.getJobId()).isNull();
            assertThat(detail.getAgreement()).isNull();
        }
        assertThat(proposalFacade.getProposalDetail(author, proposalId).isLikedByMe()).isTrue();
    }

    @Test
    @DisplayName("PostgreSQL에서 거절된 제안의 공감 추가·취소는 PROPOSAL_409_LIKE, 결제 준비와 학생 취소는 각 409로 거부되고 공감 기록·주문이 바뀌지 않는다")
    void rejectsLikePaymentAndCancelAfterReject() {
        Long proposalId = saveProposal(null);
        String otherStudent = givenStudent(2, null);
        proposalFacade.likeProposal(author, proposalId);
        proposalFacade.rejectProposal(owner, proposalId);

        for (String username : List.of(author, otherStudent)) {
            assertError(() -> proposalFacade.likeProposal(username, proposalId),
                    ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);
            assertError(() -> proposalFacade.unlikeProposal(username, proposalId),
                    ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);
        }
        assertError(() -> paymentFacade.prepareProposalPayment(
                PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null)),
                ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
        assertError(() -> proposalFacade.cancelProposal(author, proposalId), ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);

        assertRejectedByOwner(proposalId);
        assertThat(likeCount(proposalId)).isEqualTo(1);
        assertThat(likedProfileIds(proposalId)).containsExactly(profileId(1));
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 거절을 DB에 보낸 뒤 같은 트랜잭션이 실패하면 상태·거절 주체·거절 시각이 모두 롤백되고 재요청이 거절을 반영한다")
    void rollsBackRejectWhenFailingAfterWrites() {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(author, proposalId);
        // 잠근 제안의 상태·거절 주체·거절 시각을 DB에 보낸 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            Proposal locked = (Proposal) invocation.callRealMethod();
            Proposal failing = org.mockito.Mockito.spy(locked);
            doAnswer(inner -> {
                locked.rejectByOwner(inner.getArgument(0));
                proposalRepository.flush();
                throw new IllegalStateException("거절 저장 실패");
            }).when(failing).rejectByOwner(any());
            return failing;
        }).when(proposalService).getRejectableProposalForUpdate(any(), any(), any());

        assertThatThrownBy(() -> proposalFacade.rejectProposal(owner, proposalId))
                .isInstanceOf(IllegalStateException.class);

        assertPendingWithoutRejection(proposalId);
        assertThat(likeCount(proposalId)).isEqualTo(1);

        reset(proposalService);
        assertThat(proposalFacade.rejectProposal(owner, proposalId).getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertRejectedByOwner(proposalId);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 제안을 동시에 반복 거절해도 모든 요청이 거절 상태로 성공하고 최초 거절 시각 하나만 남는다")
    void rejectsOnceForConcurrentRejects() throws Exception {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);

        List<Outcome> outcomes = runConcurrently(repeat(6, () -> proposalFacade.rejectProposal(owner, proposalId)));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(outcomes).extracting(outcome -> ((ProposalRejectResult) outcome.value()).getStatus())
                .containsOnly(ProposalStatus.REJECTED);
        assertRejectedByOwner(proposalId);
        assertThat(likeCount(proposalId)).isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 거절과 여러 학생의 공감 추가·취소가 동시에 와도 거절 뒤의 공감 변경은 409로 거부되고 공감 수가 공감 기록 수와 같다")
    void keepsLikesConsistentAfterConcurrentRejectAndLikes() throws Exception {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(author, proposalId);
        List<Callable<Object>> requests = new ArrayList<>();
        for (int number = 2; number <= 6; number++) {
            String student = givenStudent(number, null);
            requests.add(() -> proposalFacade.likeProposal(student, proposalId));
        }
        requests.add(() -> proposalFacade.unlikeProposal(author, proposalId));
        requests.add(2, () -> proposalFacade.rejectProposal(owner, proposalId));

        List<Outcome> outcomes = runConcurrently(requests);

        // 거절보다 먼저 처리된 공감 변경은 그대로 남고, 뒤에 온 공감 변경은 409로 거부된다
        assertThat(outcomes.get(2).error()).isNull();
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
                .allSatisfy(outcome -> assertThat(outcome.error()).isEqualTo(ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE));
        assertRejectedByOwner(proposalId);
        long succeededLikes = outcomes.subList(0, 2).stream().filter(outcome -> outcome.error() == null).count()
                + outcomes.subList(3, 6).stream().filter(outcome -> outcome.error() == null).count();
        int expected = 1 + (int) succeededLikes - (outcomes.get(6).error() == null ? 1 : 0);
        assertThat(likeCount(proposalId)).isEqualTo(expected);
        assertThat(likedProfileIds(proposalId)).hasSize(expected);
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 거절과 결제 준비가 동시에 와도 거절된 제안에 결제 대기 주문이 생기거나 결제 대기 중인 제안이 거절되지 않는다")
    void serializesConcurrentRejectAndPaymentPreparation() throws Exception {
        Long proposalId = saveProposal(null);
        when(kakaoPayClient.ready(any())).thenAnswer(invocation ->
                new ReadyResult(newTid(), "pc", "mobile"));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> paymentFacade.prepareProposalPayment(
                        PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null))));

        Outcome reject = outcomes.get(0);
        Outcome prepare = outcomes.get(1);
        int pendingPayments = count(
                "select count(*) from payments where proposal_id = ? and status = 'PENDING'", proposalId);
        if (reject.error() == null) {
            // 거절이 먼저: 결제 준비는 결제할 수 없는 상태로 거부되고 주문이 없다
            assertThat(prepare.error()).isEqualTo(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
            assertRejectedByOwner(proposalId);
            assertThat(pendingPayments).isZero();
        } else {
            // 결제 준비가 먼저: 거절은 결제 대기로 거부되고 제안은 결제 전 그대로다
            assertThat(reject.error()).isEqualTo(ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING);
            assertThat(prepare.error()).isNull();
            assertPendingWithoutRejection(proposalId);
            assertThat(pendingPayments).isEqualTo(1);
        }
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 거절과 결제 승인이 동시에 와도 거절은 거부되고 승인만 반영되어 거절 기록 없는 수락 대기 제안과 의뢰 하나가 남는다")
    void keepsApprovalWhenRejectRacesWithIt() throws Exception {
        Long proposalId = saveProposal(null);
        String tid = newTid();
        String orderId = savePendingPayment(proposalId, tid);
        when(kakaoPayClient.order(tid)).thenReturn(new PaymentResult(
                tid, "TC0ONETIME", orderId, OWNER_USER_ID, 50_000L, "SUCCESS_PAYMENT", APPROVED_AT));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> approvalService.approve(OWNER_USERNAME, orderId, "pg-token")));

        assertThat(outcomes.get(1).error()).isNull();
        // 승인 전에는 결제 대기 주문 때문에, 승인 뒤에는 수락 대기 상태 때문에 거절이 거부된다
        assertThat(outcomes.get(0).error())
                .isIn(ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING, ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);
        assertThat(count("select count(*) from proposals where id = ? and status = 'AWAITING_START' "
                + "and rejected_by is null and rejected_at is null", proposalId)).isEqualTo(1);
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposalId)).isEqualTo(1);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PAID'", proposalId))
                .isEqualTo(1);
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 사장님 거절과 학생 취소가 동시에 와도 먼저 반영된 상태만 남고 뒤에 온 요청은 409로 거부된다")
    void keepsFirstOfConcurrentRejectAndCancel() throws Exception {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> proposalFacade.cancelProposal(author, proposalId)));

        Outcome reject = outcomes.get(0);
        Outcome cancel = outcomes.get(1);
        if (reject.error() == null) {
            // 거절이 먼저: 취소는 거부되고 공감 기록이 남는다
            assertThat(cancel.error()).isEqualTo(ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);
            assertRejectedByOwner(proposalId);
            assertThat(likeCount(proposalId)).isEqualTo(1);
            assertThat(likedProfileIds(proposalId)).containsExactly(profileId(2));
        } else {
            // 취소가 먼저: 거절은 거부되고 거절 기록 없이 취소된다
            assertThat(reject.error()).isEqualTo(ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);
            assertThat(cancel.error()).isNull();
            assertThat(count("select count(*) from proposals where id = ? and status = 'CANCELLED' "
                    + "and rejected_by is null and rejected_at is null", proposalId)).isEqualTo(1);
            assertThat(likeCount(proposalId)).isZero();
            assertThat(likedProfileIds(proposalId)).isEmpty();
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 결제 준비가 제안 잠금을 쥔 동안 거절은 대기하고, 준비 커밋 뒤 PROPOSAL_409_REJECT_PAYMENT_PENDING으로 거부된다")
    void rejectsWaitingRejectAfterPaymentPreparationCommitted() throws Exception {
        Long proposalId = saveProposal(null);

        assertRejectedAfterCommitted(
                () -> preparationService.createPendingForProposal(
                        PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null)),
                () -> proposalFacade.rejectProposal(owner, proposalId),
                ErrorCode.PROPOSAL_REJECT_PAYMENT_PENDING);

        assertPendingWithoutRejection(proposalId);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PENDING'", proposalId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 거절이 제안 잠금을 쥔 동안 결제 준비는 대기하고, 거절 커밋 뒤 PROPOSAL_409_PAYMENT로 거부되어 주문이 생기지 않는다")
    void rejectsWaitingPaymentPreparationAfterRejectCommitted() throws Exception {
        Long proposalId = saveProposal(null);

        assertRejectedAfterCommitted(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> paymentFacade.prepareProposalPayment(
                        PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null)),
                ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);

        assertRejectedByOwner(proposalId);
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
        org.mockito.Mockito.verify(kakaoPayClient, org.mockito.Mockito.never()).ready(any());
    }

    @Test
    @DisplayName("PostgreSQL에서 학생 취소가 제안 잠금을 쥔 동안 거절은 대기하고, 취소 커밋 뒤 PROPOSAL_409_REJECT로 거부되어 거절 기록이 남지 않는다")
    void rejectsWaitingRejectAfterCancelCommitted() throws Exception {
        Long proposalId = saveProposal(null);

        assertRejectedAfterCommitted(
                () -> proposalFacade.cancelProposal(author, proposalId),
                () -> proposalFacade.rejectProposal(owner, proposalId),
                ErrorCode.PROPOSAL_REJECT_NOT_AVAILABLE);

        assertThat(count("select count(*) from proposals where id = ? and status = 'CANCELLED' "
                + "and rejected_by is null and rejected_at is null", proposalId)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 거절이 제안 잠금을 쥔 동안 학생 취소는 대기하고, 거절 커밋 뒤 PROPOSAL_409_CANCEL로 거부되어 공감 기록이 남는다")
    void rejectsWaitingCancelAfterRejectCommitted() throws Exception {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);

        assertRejectedAfterCommitted(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> proposalFacade.cancelProposal(author, proposalId),
                ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);

        assertRejectedByOwner(proposalId);
        assertThat(likeCount(proposalId)).isEqualTo(1);
        assertThat(likedProfileIds(proposalId)).containsExactly(profileId(2));
    }

    @Test
    @DisplayName("PostgreSQL에서 거절이 제안 잠금을 쥔 동안 공감 추가는 대기하고, 거절 커밋 뒤 PROPOSAL_409_LIKE로 거부되어 공감 수가 바뀌지 않는다")
    void rejectsWaitingLikeAfterRejectCommitted() throws Exception {
        Long proposalId = saveProposal(null);
        String student = givenStudent(2, null);

        assertRejectedAfterCommitted(
                () -> proposalFacade.rejectProposal(owner, proposalId),
                () -> proposalFacade.likeProposal(student, proposalId),
                ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE);

        assertRejectedByOwner(proposalId);
        assertThat(likeCount(proposalId)).isZero();
        assertThat(likedProfileIds(proposalId)).isEmpty();
    }

    /**
     * 먼저 실행한 호출이 제안 행 잠금을 쥔 채 커밋하지 않는 동안 나중 호출이 그 잠금에서 기다리고, 커밋 후 바뀐 상태를 보고 거부되는지 확인한다.
     * 나중 호출이 늦게 시작한 것과 구분하도록, DB가 그 세션을 먼저 실행한 세션에 막힌 것으로 보고할 때까지 기다린 뒤 잠금을 푼다.
     */
    private void assertRejectedAfterCommitted(Runnable first, Runnable second, ErrorCode expected) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        try {
            // 호출의 트랜잭션이 바깥 트랜잭션에 참여하므로 잠금이 release까지 유지된다
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                first.run();
                firstPid.set(((Number) entityManager.createNativeQuery("select pg_backend_pid()")
                        .getSingleResult()).intValue());
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            Future<?> waiter = executor.submit(second);
            awaitSessionBlockedBy(firstPid.get(), waiter);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> waiter.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(expected));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /** 제안 행 잠금을 기다리는 세션이 생길 때까지 기다린다. 나중 호출이 기다리지 않고 끝나거나 제한 시간 안에 막히지 않으면 실패한다. */
    private void awaitSessionBlockedBy(int blockingPid, Future<?> waiter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    select count(*) from pg_stat_activity
                    where ? = any(pg_blocking_pids(pid)) and wait_event_type = 'Lock' and query ilike '%proposals%'
                    """, Integer.class, blockingPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            assertThat(waiter.isDone()).as("나중 호출이 제안 행 잠금을 기다리지 않고 끝났다").isFalse();
            Thread.sleep(50);
        }
        fail("나중 호출이 제안 행 잠금에서 대기하지 않았다");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    // 카카오페이 거래번호 컬럼 길이(20자)에 맞춘 고유 값
    private static String newTid() {
        return "TRJC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static long profileId(int number) {
        return 987_400L + number;
    }

    private static String userId(int number) {
        return "01K58M6PJV8VAJMXHBHJ2RJCS" + number;
    }

    // 학생 프로필이 있는 활성 학생으로 로그인한 상태를 만든다. 같은 번호는 격리 범위가 달라도 같은 학생 프로필이다
    private String givenStudent(int number, String demoSessionId) {
        String username = "TEST_REJECT_STUDENT_" + number + (demoSessionId == null ? "" : "_" + demoSessionId);
        when(userService.getActiveUser(username)).thenReturn(User.builder()
                .id(userId(number)).username(username).role(UserRole.STUDENT).demoSessionId(demoSessionId).build());
        when(studentService.findStudentProfileByUserId(userId(number))).thenReturn(Optional.of(
                Student.builder().id(profileId(number)).userId(userId(number)).build()));
        return username;
    }

    // 사장님 프로필이 있는 활성 사장님으로 로그인한 상태를 만든다. 1번이 제안을 받은 사장님이고 같은 번호는 격리 범위가 달라도 같은 프로필이다
    private String givenOwner(int number, String demoSessionId) {
        String userId = number == 1 ? OWNER_USER_ID : "01K58M6PJV8VAJMXHBHJ2RJCO" + number;
        String username = number == 1 && demoSessionId == null ? OWNER_USERNAME
                : "TEST_REJECT_OWNER_" + number + "_" + demoSessionId;
        when(userService.getActiveUser(username)).thenReturn(User.builder()
                .id(userId).username(username).role(UserRole.OWNER).demoSessionId(demoSessionId).build());
        when(ownerService.findOwnerProfileByUserId(userId)).thenReturn(Optional.of(
                Owner.builder().id(OWNER_PROFILE_ID + number - 1).userId(userId).build()));
        return username;
    }

    private Long saveProposal(String demoSessionId) {
        Long proposalId = saveProposalWithoutSpecialties(demoSessionId);
        proposalSpecialtyRepository.saveAllAndFlush(List.of(
                ProposalSpecialty.create(proposalId, 3L), ProposalSpecialty.create(proposalId, 11L)));
        return proposalId;
    }

    private Long saveProposal(String demoSessionId, LocalDateTime createdAt, int likeCount) {
        Long proposalId = saveProposal(demoSessionId);
        jdbcTemplate.update("update proposals set created_at = ?, like_count = ? where id = ?",
                createdAt, likeCount, proposalId);
        return proposalId;
    }

    private Long saveProposalWithoutSpecialties(String demoSessionId) {
        Proposal proposal = proposalRepository.saveAndFlush(Proposal.create(
                AUTHOR_PROFILE_ID, OWNER_PROFILE_ID, "메뉴판 개선 제안", "문제", "해결", "계획",
                50_000L, 3, 7, IMAGE_URLS, demoSessionId));
        proposalIds.add(proposal.getId());
        return proposal.getId();
    }

    // 사장님이 결제를 준비해 남은 대기 주문. tid가 없으면 결제창이 열리기 전 상태다
    private String savePendingPayment(Long proposalId, String tid) {
        String orderId = UUID.randomUUID().toString();
        Payment payment = Payment.pendingForProposal(proposalId, OWNER_USER_ID, orderId, 50_000L, 1, null,
                Instant.parse("2026-08-31T15:00:00Z"));
        if (tid != null) {
            payment.recordKakaoTid(tid);
        }
        paymentRepository.saveAndFlush(payment);
        return orderId;
    }

    private List<Long> explore(Long categoryId, ProposalExploreOrder order, Integer likeCountBound,
            LocalDateTime createdAtBound, Long idBound, int limit) {
        return ids(proposalService.getExploreProposals(GetExploreProposalsCommand.of(
                DEMO_SESSION, categoryId, order, likeCountBound, createdAtBound, idBound, limit)));
    }

    private List<Long> ids(List<ExploreProposalData> proposals) {
        return proposals.stream().map(data -> data.getProposal().getId()).toList();
    }

    // 사장님이 거절한 상태이고 거절 주체와 최초 거절 시각이 저장됐는지 확인한다
    private void assertRejectedByOwner(Long proposalId) {
        Proposal found = proposalRepository.findById(proposalId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(found.getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(found.getRejectedAt()).isEqualTo(REJECTED_AT_UTC);
    }

    // 결제 전 그대로이고 거절 기록이 없는지 확인한다
    private void assertPendingWithoutRejection(Long proposalId) {
        assertThat(count("select count(*) from proposals where id = ? and status = 'PENDING' "
                + "and rejected_by is null and rejected_at is null", proposalId)).isEqualTo(1);
    }

    private String status(Long proposalId) {
        return jdbcTemplate.queryForObject("select status from proposals where id = ?", String.class, proposalId);
    }

    private int likeCount(Long proposalId) {
        return count("select like_count from proposals where id = ?", proposalId);
    }

    private List<Long> likedProfileIds(Long proposalId) {
        return jdbcTemplate.queryForList(
                "select student_profile_id from proposal_likes where proposal_id = ?", Long.class, proposalId);
    }

    private List<Long> specialtyIds(Long proposalId) {
        return jdbcTemplate.queryForList(
                "select specialty_id from proposal_specialties where proposal_id = ?", Long.class, proposalId);
    }

    private long maxProposalId() {
        Long max = jdbcTemplate.queryForObject("select coalesce(max(id), 0) from proposals", Long.class);
        return max == null ? 0 : max;
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private static List<Callable<Object>> repeat(int requests, Callable<Object> action) {
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            actions.add(action);
        }
        return actions;
    }

    /** 동시 요청 하나의 결과. 비즈니스 오류로 거부되면 error에 오류 코드가 담긴다. */
    private record Outcome(Object value, ErrorCode error) {
    }

    // 모든 요청을 각자의 스레드와 트랜잭션에서 같은 순간에 시작하고, 비즈니스 오류는 결과로 모은다
    private List<Outcome> runConcurrently(List<Callable<Object>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return new Outcome(action.call(), null);
                    } catch (BusinessException exception) {
                        return new Outcome(null, exception.getErrorCode());
                    }
                }));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
