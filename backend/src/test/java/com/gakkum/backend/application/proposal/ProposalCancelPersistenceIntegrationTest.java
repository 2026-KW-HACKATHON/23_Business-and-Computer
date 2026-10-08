package com.gakkum.backend.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.application.payment.service.PaymentApprovalService;
import com.gakkum.backend.application.payment.service.PaymentPreparationService;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.config.ClockConfig;
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
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCancelResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
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

/**
 * 제안 취소의 커밋·롤백·행 잠금과 결제·공감과의 순서를 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 요청마다 별도 트랜잭션으로 실행되고, 운영 데이터를 건드리지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ PaymentApprovalService.class, PaymentPreparationService.class, PaymentFacade.class, ProposalFacade.class,
        ProposalService.class, JobService.class, PaymentService.class, ChatRoomService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("제안 취소 PostgreSQL 통합 (저장·조회 제외·격리·결제 연계·동시성·롤백)")
class ProposalCancelPersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_305L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2CNCOW";
    private static final String OWNER_USERNAME = "TEST_CANCEL_OWNER";
    // 1번 학생이 제안의 작성자다
    private static final long AUTHOR_PROFILE_ID = profileId(1);
    private static final String DEMO_SESSION = "01K6DEMOCNCL0000000000000A";
    private static final String OTHER_DEMO_SESSION = "01K6DEMOCNCL0000000000000B";
    private static final List<String> IMAGE_URLS = List.of("https://bucket/b.png", "https://bucket/a.png");
    private static final Instant APPROVED_AT = Instant.parse("2026-08-31T15:30:00Z");

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

    @BeforeEach
    void setUp() {
        author = givenStudent(1, null);
        givenOwner(null);
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(Owner.builder().id(OWNER_PROFILE_ID).build());
        // 알림 수신자를 찾을 때 읽는 제안 당사자 프로필
        when(studentService.getStudentProfile(AUTHOR_PROFILE_ID)).thenReturn(
                Student.builder().id(AUTHOR_PROFILE_ID).userId(userId(1)).build());
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(
                Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
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
    @DisplayName("PostgreSQL에서 결제 전 제안을 취소하면 상태가 취소로 바뀌고 모든 학생의 공감 기록이 지워지며 공감 수가 0이 되고 소분류·사진은 남는다")
    void persistsCancel() {
        Long proposalId = saveProposal(null);
        Long otherProposalId = saveProposal(null);
        proposalFacade.likeProposal(author, proposalId);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);
        proposalFacade.likeProposal(givenStudent(3, null), proposalId);
        proposalFacade.likeProposal(givenStudent(2, null), otherProposalId);

        ProposalCancelResult result = proposalFacade.cancelProposal(author, proposalId);

        assertThat(result.getProposalId()).isEqualTo(proposalId);
        assertThat(result.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertCancelled(proposalId);
        Proposal found = proposalRepository.findById(proposalId).orElseThrow();
        assertThat(found.getReferenceImageUrls()).containsExactlyElementsOf(IMAGE_URLS);
        assertThat(found.getTitle()).isEqualTo("메뉴판 개선 제안");
        assertThat(specialtyIds(proposalId)).containsExactlyInAnyOrder(3L, 11L);
        // 결제·의뢰는 만들거나 바꾸지 않고, 다른 제안의 공감은 그대로다
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposalId)).isZero();
        assertThat(likeCount(otherProposalId)).isEqualTo(1);
        assertThat(likedProfileIds(otherProposalId)).containsExactly(profileId(2));
        assertThat(status(otherProposalId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("PostgreSQL에서 이미 취소한 본인 제안의 반복 취소는 같은 결과로 성공하고 아무것도 바꾸지 않는다")
    void repeatsCancelWithoutChange() {
        Long proposalId = saveProposal(null);
        proposalFacade.cancelProposal(author, proposalId);

        ProposalCancelResult repeated = proposalFacade.cancelProposal(author, proposalId);

        assertThat(repeated.getProposalId()).isEqualTo(proposalId);
        assertThat(repeated.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertCancelled(proposalId);
        assertThat(specialtyIds(proposalId)).containsExactlyInAnyOrder(3L, 11L);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "AWAITING_START", "ACCEPTED", "REJECTED" })
    @DisplayName("PostgreSQL에서 결제됐거나 거절된 제안의 취소는 PROPOSAL_409_CANCEL로 거부하고 상태와 공감을 그대로 둔다")
    void rejectsCancelOfUnavailableStatus(ProposalStatus status) {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);
        jdbcTemplate.update("update proposals set status = ? where id = ?", status.name(), proposalId);

        assertError(() -> proposalFacade.cancelProposal(author, proposalId), ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);

        assertThat(status(proposalId)).isEqualTo(status.name());
        assertThat(likeCount(proposalId)).isEqualTo(1);
        assertThat(likedProfileIds(proposalId)).containsExactly(profileId(2));
    }

    @ParameterizedTest(name = "거래번호 있음 {0}")
    @ValueSource(booleans = { true, false })
    @DisplayName("PostgreSQL에서 결제 대기 주문이 있으면 거래번호 유무와 관계없이 PROPOSAL_409_CANCEL_PAYMENT_PENDING으로 거부하고 제안·공감·주문을 그대로 둔다")
    void rejectsCancelWithPendingPayment(boolean withTid) {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);
        String orderId = savePendingPayment(proposalId, withTid ? newTid() : null);

        assertError(() -> proposalFacade.cancelProposal(author, proposalId),
                ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING);

        assertThat(status(proposalId)).isEqualTo("PENDING");
        assertThat(likeCount(proposalId)).isEqualTo(1);
        assertThat(likedProfileIds(proposalId)).containsExactly(profileId(2));
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PENDING' "
                + "and order_id = ?", proposalId, orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("PostgreSQL에서 대기 주문 없이 대체·준비 실패한 주문만 남은 제안은 취소되고 그 주문은 바뀌지 않는다")
    void cancelsWithOnlyClosedPayments() {
        Long proposalId = saveProposal(null);
        String superseded = savePendingPayment(proposalId, null);
        jdbcTemplate.update("update payments set status = 'SUPERSEDED' where order_id = ?", superseded);
        String failed = savePendingPayment(proposalId, null);
        jdbcTemplate.update("update payments set status = 'READY_FAILED' where order_id = ?", failed);

        proposalFacade.cancelProposal(author, proposalId);

        assertCancelled(proposalId);
        assertThat(jdbcTemplate.queryForList(
                "select status from payments where proposal_id = ? order by id", String.class, proposalId))
                .containsExactly("SUPERSEDED", "READY_FAILED");
    }

    @Test
    @DisplayName("PostgreSQL에서 다른 학생은 PROPOSAL_403_CANCEL, 격리 범위가 다른 사용자는 작성자 여부와 무관하게 PROPOSAL_404로 거부하고 제안과 공감을 그대로 둔다")
    void rejectsOtherStudentAndIsolatedSession() {
        Long proposalId = saveProposal(DEMO_SESSION);
        String demoAuthor = givenStudent(1, DEMO_SESSION);
        String otherStudent = givenStudent(2, DEMO_SESSION);
        proposalFacade.likeProposal(otherStudent, proposalId);
        // 프로필 ID는 작성자와 같지만 격리 범위가 다른 학생들
        String realAuthor = givenStudent(1, null);
        String otherSessionAuthor = givenStudent(1, OTHER_DEMO_SESSION);
        long missingProposalId = maxProposalId() + 1_000_000L;

        assertError(() -> proposalFacade.cancelProposal(realAuthor, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.cancelProposal(otherSessionAuthor, proposalId),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.cancelProposal(givenStudent(2, null), proposalId),
                ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.cancelProposal(givenStudent(2, DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        assertError(() -> proposalFacade.cancelProposal(demoAuthor, missingProposalId),
                ErrorCode.PROPOSAL_NOT_FOUND);

        assertThat(status(proposalId)).isEqualTo("PENDING");
        assertThat(likeCount(proposalId)).isEqualTo(1);

        // 다른 학생은 이미 취소된 제안에도 반복 성공이 아닌 403을 받는다
        proposalFacade.cancelProposal(givenStudent(1, DEMO_SESSION), proposalId);
        assertError(() -> proposalFacade.cancelProposal(givenStudent(2, DEMO_SESSION), proposalId),
                ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        assertCancelled(proposalId);
    }

    @Test
    @DisplayName("PostgreSQL에서 취소된 제안은 양쪽 목록과 탐색의 모든 정렬·후속 커서 구간에서 빠지고 누적 제안 수에는 남는다")
    void excludesCancelledProposalFromListsAndExplore() {
        // 세 제안의 생성 시각과 공감 수가 모두 달라 가운데 제안이 빠진 자리가 드러난다
        Long oldest = saveProposal(DEMO_SESSION, LocalDateTime.of(2031, 3, 1, 9, 0), 1);
        Long middle = saveProposal(DEMO_SESSION, LocalDateTime.of(2031, 3, 2, 9, 0), 2);
        Long newest = saveProposal(DEMO_SESSION, LocalDateTime.of(2031, 3, 3, 9, 0), 3);
        long countBefore = proposalService.countProposals(AUTHOR_PROFILE_ID);

        proposalFacade.cancelProposal(givenStudent(1, DEMO_SESSION), middle);

        assertThat(ids(proposalService.getMyProposals(GetMyProposalsCommand.of(AUTHOR_PROFILE_ID))))
                .containsSubsequence(newest, oldest).doesNotContain(middle);
        assertThat(ids(proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(OWNER_PROFILE_ID))))
                .containsSubsequence(newest, oldest).doesNotContain(middle);
        assertThat(proposalService.countProposals(AUTHOR_PROFILE_ID)).isEqualTo(countBefore);

        LocalDateTime latestStart = LocalDateTime.of(9999, 1, 1, 0, 0);
        LocalDateTime oldestStart = LocalDateTime.of(1970, 1, 1, 0, 0);
        // 취소된 제안이 자리를 차지하지 않아 첫 페이지가 남은 두 제안으로 채워진다
        assertThat(explore(null, ProposalExploreOrder.LATEST, null, latestStart, Long.MAX_VALUE, 2))
                .containsExactly(newest, oldest);
        assertThat(explore(null, ProposalExploreOrder.OLDEST, null, oldestStart, Long.MIN_VALUE, 2))
                .containsExactly(oldest, newest);
        assertThat(explore(null, ProposalExploreOrder.LIKES, Integer.MAX_VALUE, latestStart, Long.MAX_VALUE, 2))
                .containsExactly(newest, oldest);
        // 첫 카드 뒤의 커서 구간도 취소된 제안을 건너뛴다
        assertThat(explore(null, ProposalExploreOrder.LATEST, null, LocalDateTime.of(2031, 3, 3, 9, 0), newest, 1))
                .containsExactly(oldest);
        assertThat(explore(null, ProposalExploreOrder.OLDEST, null, LocalDateTime.of(2031, 3, 1, 9, 0), oldest, 1))
                .containsExactly(newest);
        assertThat(explore(null, ProposalExploreOrder.LIKES, 3, LocalDateTime.of(2031, 3, 3, 9, 0), newest, 1))
                .containsExactly(oldest);
        // 세 제안에 새 대분류의 소분류를 연결해 대분류 필터에서도 빠지는지 확인한다
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into specialty_categories (name) values (?) returning id", Long.class,
                "취소-" + UUID.randomUUID());
        Long specialtyId = jdbcTemplate.queryForObject(
                "insert into specialties (specialty_category_id, name) values (?, ?) returning id", Long.class,
                categoryId, "취소-" + UUID.randomUUID());
        try {
            for (Long proposalId : List.of(oldest, middle, newest)) {
                jdbcTemplate.update("insert into proposal_specialties (proposal_id, specialty_id) values (?, ?)",
                        proposalId, specialtyId);
            }
            assertThat(explore(categoryId, ProposalExploreOrder.LATEST, null, latestStart, Long.MAX_VALUE, 2))
                    .containsExactly(newest, oldest);
            assertThat(explore(categoryId, ProposalExploreOrder.OLDEST, null, oldestStart, Long.MIN_VALUE, 2))
                    .containsExactly(oldest, newest);
            assertThat(explore(categoryId, ProposalExploreOrder.LIKES, Integer.MAX_VALUE, latestStart,
                    Long.MAX_VALUE, 2)).containsExactly(newest, oldest);
            assertThat(explore(categoryId, ProposalExploreOrder.LIKES, 3, LocalDateTime.of(2031, 3, 3, 9, 0),
                    newest, 1)).containsExactly(oldest);
        } finally {
            jdbcTemplate.update("delete from specialties where id = ?", specialtyId);
            jdbcTemplate.update("delete from specialty_categories where id = ?", categoryId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 취소된 제안 상세는 작성자에게만 취소 상태와 공감 수 0으로 내리고 받은 사장님과 다른 학생에게는 PROPOSAL_404다")
    void showsCancelledDetailOnlyToAuthor() {
        Long proposalId = saveProposalWithoutSpecialties(null);
        String otherStudent = givenStudent(2, null);
        proposalFacade.likeProposal(author, proposalId);
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(
                Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
        when(studentService.getStudentProfile(AUTHOR_PROFILE_ID)).thenReturn(
                Student.builder().id(AUTHOR_PROFILE_ID).userId(userId(1)).build());
        when(userService.getUser(userId(1))).thenReturn(User.builder().id(userId(1)).name("김학생").build());
        assertThat(proposalFacade.getProposalDetail(OWNER_USERNAME, proposalId).getStatus())
                .isEqualTo(ProposalStatus.PENDING);

        proposalFacade.cancelProposal(author, proposalId);

        ProposalDetailResult detail = proposalFacade.getProposalDetail(author, proposalId);
        assertThat(detail.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertThat(detail.getLikeCount()).isZero();
        assertThat(detail.isLikedByMe()).isFalse();
        assertThat(detail.getEstimatedDraftDeadline()).isNull();
        assertThat(detail.getJobId()).isNull();
        assertThat(detail.getAgreement()).isNull();
        assertError(() -> proposalFacade.getProposalDetail(OWNER_USERNAME, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
        assertError(() -> proposalFacade.getProposalDetail(otherStudent, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
    }

    @Test
    @DisplayName("PostgreSQL에서 취소된 제안의 공감 추가·취소와 결제 준비는 거부되고 공감 기록·주문이 생기지 않는다")
    void rejectsLikeAndPaymentAfterCancel() {
        Long proposalId = saveProposal(null);
        String otherStudent = givenStudent(2, null);
        proposalFacade.cancelProposal(author, proposalId);

        for (String username : List.of(author, otherStudent)) {
            assertError(() -> proposalFacade.likeProposal(username, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
            assertError(() -> proposalFacade.unlikeProposal(username, proposalId), ErrorCode.PROPOSAL_NOT_FOUND);
        }
        assertError(() -> paymentFacade.prepareProposalPayment(
                PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null)),
                ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);

        assertCancelled(proposalId);
        assertThat(count("select count(*) from payments where proposal_id = ?", proposalId)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 상태 전환과 공감 기록 삭제 뒤에 실패하면 상태·공감 기록·공감 수가 모두 롤백되고 재요청이 취소를 반영한다")
    void rollsBackCancelWhenFailingAfterWrites() {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(author, proposalId);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);
        // 상태 전환·공감 수 초기화·공감 기록 삭제를 DB에 보낸 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            proposalRepository.flush();
            throw new IllegalStateException("취소 저장 실패");
        }).when(proposalService).cancelProposal(any());

        assertThatThrownBy(() -> proposalFacade.cancelProposal(author, proposalId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(status(proposalId)).isEqualTo("PENDING");
        assertThat(likeCount(proposalId)).isEqualTo(2);
        assertThat(likedProfileIds(proposalId)).containsExactlyInAnyOrder(profileId(1), profileId(2));

        reset(proposalService);
        assertThat(proposalFacade.cancelProposal(author, proposalId).getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        assertCancelled(proposalId);
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 제안을 동시에 반복 취소해도 모든 요청이 취소 상태로 성공한다")
    void cancelsOnceForConcurrentCancels() throws Exception {
        Long proposalId = saveProposal(null);
        proposalFacade.likeProposal(givenStudent(2, null), proposalId);

        List<Outcome> outcomes = runConcurrently(repeat(6, () -> proposalFacade.cancelProposal(author, proposalId)));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
        assertThat(outcomes).extracting(outcome -> ((ProposalCancelResult) outcome.value()).getStatus())
                .containsOnly(ProposalStatus.CANCELLED);
        assertCancelled(proposalId);
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 취소와 여러 학생의 공감 추가가 동시에 와도 취소 뒤에는 공감 기록이 남지 않고 공감 수가 0이다")
    void leavesNoLikeAfterConcurrentCancelAndLikes() throws Exception {
        Long proposalId = saveProposal(null);
        List<Callable<Object>> requests = new ArrayList<>();
        for (int number = 2; number <= 6; number++) {
            String student = givenStudent(number, null);
            requests.add(() -> proposalFacade.likeProposal(student, proposalId));
        }
        requests.add(2, () -> proposalFacade.cancelProposal(author, proposalId));

        List<Outcome> outcomes = runConcurrently(requests);

        // 취소보다 먼저 처리된 공감은 성공했다가 취소로 지워지고, 뒤에 온 공감은 404로 거부된다
        assertThat(outcomes.get(2).error()).isNull();
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
                .allSatisfy(outcome -> assertThat(outcome.error()).isEqualTo(ErrorCode.PROPOSAL_NOT_FOUND));
        assertCancelled(proposalId);
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 취소와 결제 준비가 동시에 와도 취소된 제안에 결제 대기 주문이 생기거나 결제 대기 중인 제안이 취소되지 않는다")
    void serializesConcurrentCancelAndPaymentPreparation() throws Exception {
        Long proposalId = saveProposal(null);
        when(kakaoPayClient.ready(any())).thenAnswer(invocation ->
                new ReadyResult(newTid(), "pc", "mobile"));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> proposalFacade.cancelProposal(author, proposalId),
                () -> paymentFacade.prepareProposalPayment(
                        PrepareProposalPaymentCommand.of(OWNER_USERNAME, proposalId, 50_000L, 1, null))));

        Outcome cancel = outcomes.get(0);
        Outcome prepare = outcomes.get(1);
        int pendingPayments = count(
                "select count(*) from payments where proposal_id = ? and status = 'PENDING'", proposalId);
        if (cancel.error() == null) {
            // 취소가 먼저: 결제 준비는 결제할 수 없는 상태로 거부되고 주문이 없다
            assertThat(prepare.error()).isEqualTo(ErrorCode.PROPOSAL_PAYMENT_NOT_AVAILABLE);
            assertThat(status(proposalId)).isEqualTo("CANCELLED");
            assertThat(pendingPayments).isZero();
        } else {
            // 결제 준비가 먼저: 취소는 결제 대기로 거부되고 제안은 결제 전 그대로다
            assertThat(cancel.error()).isEqualTo(ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING);
            assertThat(prepare.error()).isNull();
            assertThat(status(proposalId)).isEqualTo("PENDING");
            assertThat(pendingPayments).isEqualTo(1);
        }
    }

    @RepeatedTest(3)
    @DisplayName("PostgreSQL에서 취소와 결제 승인이 동시에 와도 취소는 결제 대기로 거부되고 승인만 반영되어 수락 대기 제안과 의뢰 하나가 남는다")
    void keepsApprovalWhenCancelRacesWithIt() throws Exception {
        Long proposalId = saveProposal(null);
        String tid = newTid();
        String orderId = savePendingPayment(proposalId, tid);
        when(kakaoPayClient.order(tid)).thenReturn(new PaymentResult(
                tid, "TC0ONETIME", orderId, OWNER_USER_ID, 50_000L, "SUCCESS_PAYMENT", APPROVED_AT));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> proposalFacade.cancelProposal(author, proposalId),
                () -> approvalService.approve(OWNER_USERNAME, orderId, "pg-token")));

        assertThat(outcomes.get(1).error()).isNull();
        // 승인 전에는 결제 대기 주문 때문에, 승인 뒤에는 수락 대기 상태 때문에 취소가 거부된다
        assertThat(outcomes.get(0).error())
                .isIn(ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING, ErrorCode.PROPOSAL_CANCEL_NOT_AVAILABLE);
        assertThat(status(proposalId)).isEqualTo("AWAITING_START");
        assertThat(count("select count(*) from jobs where proposal_id = ?", proposalId)).isEqualTo(1);
        assertThat(count("select count(*) from payments where proposal_id = ? and status = 'PAID'", proposalId))
                .isEqualTo(1);
    }

    // 카카오페이 거래번호 컬럼 길이(20자)에 맞춘 고유 값
    private static String newTid() {
        return "TCNC" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static long profileId(int number) {
        return 987_300L + number;
    }

    private static String userId(int number) {
        return "01K58M6PJV8VAJMXHBHJ2CNCS" + number;
    }

    // 학생 프로필이 있는 활성 학생으로 로그인한 상태를 만든다. 같은 번호는 격리 범위가 달라도 같은 학생 프로필이다
    private String givenStudent(int number, String demoSessionId) {
        String username = "TEST_CANCEL_STUDENT_" + number + (demoSessionId == null ? "" : "_" + demoSessionId);
        when(userService.getActiveUser(username)).thenReturn(User.builder()
                .id(userId(number)).username(username).role(UserRole.STUDENT).demoSessionId(demoSessionId).build());
        when(studentService.findStudentProfileByUserId(userId(number))).thenReturn(Optional.of(
                Student.builder().id(profileId(number)).userId(userId(number)).build()));
        return username;
    }

    private void givenOwner(String demoSessionId) {
        when(userService.getActiveUser(OWNER_USERNAME)).thenReturn(User.builder()
                .id(OWNER_USER_ID).username(OWNER_USERNAME).role(UserRole.OWNER).demoSessionId(demoSessionId)
                .build());
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

    // 취소 상태이고 공감 수와 공감 기록이 모두 비었는지 확인한다
    private void assertCancelled(Long proposalId) {
        assertThat(status(proposalId)).isEqualTo("CANCELLED");
        assertThat(likeCount(proposalId)).isZero();
        assertThat(likedProfileIds(proposalId)).isEmpty();
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
