package com.gakkum.backend.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.explore.dto.ExploreCommandDto.ExploreCommand;
import com.gakkum.backend.application.explore.dto.ExploreQueryDto.ProposalCardResult;
import com.gakkum.backend.application.explore.dto.ExploreSort;
import com.gakkum.backend.application.explore.dto.ExploreType;
import com.gakkum.backend.application.explore.facade.ExploreFacade;
import com.gakkum.backend.application.proposal.facade.ProposalFacade;
import com.gakkum.backend.config.ClockConfig;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
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
 * 공감 기록과 공감 수의 커밋·롤백·행 잠금을 실제로 확인해야 하므로 클래스 트랜잭션을 끄고 직접 데이터를 정리한다.
 * 요청마다 별도 트랜잭션으로 실행되고, 운영 데이터를 건드리지 않도록 로컬 PostgreSQL에서만 실행한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@Import({ ProposalFacade.class, ExploreFacade.class, ProposalService.class, ClockConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("제안 공감 켜기·끄기 PostgreSQL 통합 (저장·조회 연계·격리·동시성·롤백)")
class ProposalLikePersistenceIntegrationTest {

    private static final long OWNER_PROFILE_ID = 987_205L;
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2LIKOW";
    private static final String OWNER_USERNAME = "TEST_LIKE_OWNER";
    // 1번 학생이 제안의 작성자다
    private static final long AUTHOR_PROFILE_ID = profileId(1);
    private static final String DEMO_SESSION = "01K6DEMOLIKE0000000000000A";
    private static final String OTHER_DEMO_SESSION = "01K6DEMOLIKE0000000000000B";

    @Autowired
    private ProposalFacade proposalFacade;

    @Autowired
    private ExploreFacade exploreFacade;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProposalService proposalService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private OwnerService ownerService;

    @MockitoBean
    private SpecialtyService specialtyService;

    @MockitoBean
    private SpecialtyCategoryService specialtyCategoryService;

    @MockitoBean
    private BusinessCategoryService businessCategoryService;

    @MockitoBean
    private MediaService mediaService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private ChatRoomService chatRoomService;

    private final List<Long> proposalIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        reset(proposalService);
        for (Long proposalId : proposalIds) {
            jdbcTemplate.update("delete from proposal_likes where proposal_id = ?", proposalId);
            jdbcTemplate.update("delete from proposals where id = ?", proposalId);
        }
    }

    @Test
    @DisplayName("PostgreSQL에서 본인 제안의 최초 공감·반복 공감·취소·반복 취소·재공감이 공감 기록과 공감 수를 한 번씩만 바꾼다")
    void persistsLikeAndUnlikeOnce() {
        Long proposalId = saveProposal(null);
        String author = givenStudent(1, null);

        ProposalLikeResult liked = proposalFacade.likeProposal(author, proposalId);
        assertThat(liked.getProposalId()).isEqualTo(proposalId);
        assertThat(liked.getLikeCount()).isEqualTo(1);
        assertThat(liked.isLikedByMe()).isTrue();
        assertLikes(proposalId, 1, AUTHOR_PROFILE_ID);

        ProposalLikeResult likedAgain = proposalFacade.likeProposal(author, proposalId);
        assertThat(likedAgain.getLikeCount()).isEqualTo(1);
        assertThat(likedAgain.isLikedByMe()).isTrue();
        assertLikes(proposalId, 1, AUTHOR_PROFILE_ID);

        ProposalLikeResult unliked = proposalFacade.unlikeProposal(author, proposalId);
        assertThat(unliked.getProposalId()).isEqualTo(proposalId);
        assertThat(unliked.getLikeCount()).isZero();
        assertThat(unliked.isLikedByMe()).isFalse();
        assertLikes(proposalId, 0);

        ProposalLikeResult unlikedAgain = proposalFacade.unlikeProposal(author, proposalId);
        assertThat(unlikedAgain.getLikeCount()).isZero();
        assertThat(unlikedAgain.isLikedByMe()).isFalse();
        assertLikes(proposalId, 0);

        assertThat(proposalFacade.likeProposal(author, proposalId).getLikeCount()).isEqualTo(1);
        assertLikes(proposalId, 1, AUTHOR_PROFILE_ID);
    }

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = { "CANCELLED", "REJECTED" }, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("PostgreSQL에서 취소·거절되지 않은 모든 상태의 제안에 공감하고 취소할 수 있고 제안 상태는 바뀌지 않는다")
    void likesProposalInEveryStatus(ProposalStatus status) {
        Long proposalId = saveProposal(null);
        jdbcTemplate.update("update proposals set status = ? where id = ?", status.name(), proposalId);
        String student = givenStudent(2, null);

        assertThat(proposalFacade.likeProposal(student, proposalId).getLikeCount()).isEqualTo(1);
        assertLikes(proposalId, 1, profileId(2));
        assertThat(proposalFacade.unlikeProposal(student, proposalId).getLikeCount()).isZero();
        assertLikes(proposalId, 0);
        assertThat(proposalRepository.findById(proposalId).orElseThrow().getStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("PostgreSQL에서 취소된 제안의 공감 추가·취소는 PROPOSAL_404로 거부하고 공감 기록과 공감 수를 바꾸지 않는다")
    void rejectsLikeForCancelledProposal() {
        Long proposalId = saveProposal(null);
        jdbcTemplate.update("update proposals set status = 'CANCELLED' where id = ?", proposalId);
        String student = givenStudent(2, null);

        assertNotFound(() -> proposalFacade.likeProposal(student, proposalId));
        assertNotFound(() -> proposalFacade.unlikeProposal(student, proposalId));

        assertLikes(proposalId, 0);
    }

    @ParameterizedTest(name = "거절 주체 {0}")
    @org.junit.jupiter.params.provider.CsvSource(value = { "OWNER", "STUDENT", "NULL" }, nullValues = "NULL")
    @DisplayName("PostgreSQL에서 거절된 제안의 공감 추가·취소는 거절 주체와 무관하게 PROPOSAL_409_LIKE로 거부하고 기존 공감 기록과 공감 수를 그대로 둔다")
    void rejectsLikeForRejectedProposal(String rejectedBy) {
        Long proposalId = saveProposal(null);
        String liked = givenStudent(2, null);
        String notLiked = givenStudent(3, null);
        proposalFacade.likeProposal(liked, proposalId);
        jdbcTemplate.update("update proposals set status = 'REJECTED', rejected_by = ? where id = ?",
                rejectedBy, proposalId);

        for (String student : List.of(liked, notLiked)) {
            for (Runnable action : List.<Runnable>of(() -> proposalFacade.likeProposal(student, proposalId),
                    () -> proposalFacade.unlikeProposal(student, proposalId))) {
                assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_LIKE_NOT_AVAILABLE));
            }
        }

        assertLikes(proposalId, 1, profileId(2));
    }

    @Test
    @DisplayName("PostgreSQL에서 한 학생의 공감 취소는 다른 학생의 공감 기록을 지우지 않는다")
    void keepsLikesOfOtherStudents() {
        Long proposalId = saveProposal(null);
        String first = givenStudent(2, null);
        String second = givenStudent(3, null);
        proposalFacade.likeProposal(first, proposalId);
        proposalFacade.likeProposal(second, proposalId);
        assertLikes(proposalId, 2, profileId(2), profileId(3));

        ProposalLikeResult result = proposalFacade.unlikeProposal(first, proposalId);

        assertThat(result.getLikeCount()).isEqualTo(1);
        assertLikes(proposalId, 1, profileId(3));
        // 공감하지 않은 학생의 취소 요청도 남의 기록을 건드리지 않는다
        assertThat(proposalFacade.unlikeProposal(first, proposalId).getLikeCount()).isEqualTo(1);
        assertLikes(proposalId, 1, profileId(3));
    }

    @Test
    @DisplayName("PostgreSQL에서 공감을 바꾸면 상세와 탐색의 공감 수와 본인 공감 여부가 따라 바뀌고 사장님과 프로필 없는 학생에게는 false다")
    void reflectsLikeInDetailAndExplore() {
        Long proposalId = saveProposal(DEMO_SESSION);
        String liker = givenStudent(2, DEMO_SESSION);
        String other = givenStudent(3, DEMO_SESSION);
        String withoutProfile = givenStudentWithoutProfile(4, DEMO_SESSION);
        givenOwner(DEMO_SESSION);
        givenDetailReferences();

        assertDetail(liker, proposalId, 0, false);
        assertExploreCard(liker, proposalId, 0, false);

        proposalFacade.likeProposal(liker, proposalId);

        assertDetail(liker, proposalId, 1, true);
        assertExploreCard(liker, proposalId, 1, true);
        // 공감 수는 모두에게 같고 본인 공감 여부는 공감한 학생에게만 true다
        assertDetail(other, proposalId, 1, false);
        assertExploreCard(other, proposalId, 1, false);
        assertDetail(OWNER_USERNAME, proposalId, 1, false);
        assertExploreCard(OWNER_USERNAME, proposalId, 1, false);
        assertDetail(withoutProfile, proposalId, 1, false);
        assertExploreCard(withoutProfile, proposalId, 1, false);

        proposalFacade.unlikeProposal(liker, proposalId);

        assertDetail(liker, proposalId, 0, false);
        assertExploreCard(liker, proposalId, 0, false);
    }

    @Test
    @DisplayName("PostgreSQL에서 없거나 격리 범위가 다른 제안의 공감 추가·취소는 PROPOSAL_404로 거부하고 기존 공감 기록과 공감 수를 그대로 둔다")
    void rejectsMissingOrIsolatedProposal() {
        Long demoProposalId = saveProposal(DEMO_SESSION);
        // 격리 범위 밖 학생과 프로필 ID가 같은 공감 기록이 남아 있어도 취소 재요청으로 지워지지 않는다
        jdbcTemplate.update("insert into proposal_likes (proposal_id, student_profile_id) values (?, ?)",
                demoProposalId, profileId(2));
        jdbcTemplate.update("update proposals set like_count = 1 where id = ?", demoProposalId);
        String realStudent = givenStudent(2, null);
        String otherDemoStudent = givenStudent(3, OTHER_DEMO_SESSION);
        long missingProposalId = maxProposalId() + 1_000_000L;

        for (String username : List.of(realStudent, otherDemoStudent)) {
            assertNotFound(() -> proposalFacade.likeProposal(username, demoProposalId));
            assertNotFound(() -> proposalFacade.unlikeProposal(username, demoProposalId));
        }
        assertNotFound(() -> proposalFacade.likeProposal(realStudent, missingProposalId));
        assertNotFound(() -> proposalFacade.unlikeProposal(realStudent, missingProposalId));

        assertLikes(demoProposalId, 1, profileId(2));
        assertThat(count("select count(*) from proposal_likes where proposal_id = ?", missingProposalId)).isZero();
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 학생이 동시에 공감해도 공감 기록은 하나만 저장되고 모든 요청이 공감 수 1로 성공한다")
    void storesOneLikeForConcurrentLikes() throws Exception {
        Long proposalId = saveProposal(null);
        String student = givenStudent(2, null);

        List<ProposalLikeResult> results = runConcurrently(repeat(6,
                () -> proposalFacade.likeProposal(student, proposalId)));

        assertThat(results).extracting(ProposalLikeResult::getLikeCount).containsOnly(1);
        assertThat(results).extracting(ProposalLikeResult::isLikedByMe).containsOnly(true);
        assertLikes(proposalId, 1, profileId(2));
    }

    @Test
    @DisplayName("PostgreSQL에서 같은 학생이 동시에 공감을 취소해도 공감 수는 한 번만 줄고 다른 학생의 공감은 남는다")
    void removesOneLikeForConcurrentUnlikes() throws Exception {
        Long proposalId = saveProposal(null);
        String student = givenStudent(2, null);
        String other = givenStudent(3, null);
        proposalFacade.likeProposal(student, proposalId);
        proposalFacade.likeProposal(other, proposalId);

        List<ProposalLikeResult> results = runConcurrently(repeat(6,
                () -> proposalFacade.unlikeProposal(student, proposalId)));

        assertThat(results).extracting(ProposalLikeResult::getLikeCount).containsOnly(1);
        assertThat(results).extracting(ProposalLikeResult::isLikedByMe).containsOnly(false);
        assertLikes(proposalId, 1, profileId(3));
    }

    @Test
    @DisplayName("PostgreSQL에서 여러 학생이 동시에 공감해도 공감 수 갱신이 유실되지 않고 학생 수만큼 오른다")
    void countsEveryLikeOfConcurrentStudents() throws Exception {
        Long proposalId = saveProposal(null);
        List<Callable<ProposalLikeResult>> requests = new ArrayList<>();
        for (int number = 1; number <= 6; number++) {
            String student = givenStudent(number, null);
            requests.add(() -> proposalFacade.likeProposal(student, proposalId));
        }

        List<ProposalLikeResult> results = runConcurrently(requests);

        // 요청이 순서대로 처리되어 각 요청이 서로 다른 공감 수를 본다
        assertThat(results).extracting(ProposalLikeResult::getLikeCount).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        assertLikes(proposalId, 6, profileId(1), profileId(2), profileId(3), profileId(4), profileId(5),
                profileId(6));
    }

    @Test
    @DisplayName("PostgreSQL에서 공감 추가와 취소를 섞어 동시에 요청해도 최종 공감 수는 공감 기록 수와 같고 음수가 되지 않는다")
    void keepsCountEqualToRecordsForMixedRequests() throws Exception {
        Long proposalId = saveProposal(null);
        String liking = givenStudent(1, null);
        String alsoLiking = givenStudent(2, null);
        String unliking = givenStudent(3, null);
        String toggling = givenStudent(4, null);
        String neverLiked = givenStudent(5, null);
        proposalFacade.likeProposal(unliking, proposalId);
        proposalFacade.likeProposal(toggling, proposalId);

        List<ProposalLikeResult> results = runConcurrently(List.of(
                () -> proposalFacade.likeProposal(liking, proposalId),
                () -> proposalFacade.likeProposal(liking, proposalId),
                () -> proposalFacade.likeProposal(alsoLiking, proposalId),
                () -> proposalFacade.unlikeProposal(unliking, proposalId),
                () -> proposalFacade.unlikeProposal(unliking, proposalId),
                () -> proposalFacade.unlikeProposal(toggling, proposalId),
                () -> proposalFacade.likeProposal(toggling, proposalId),
                () -> proposalFacade.unlikeProposal(neverLiked, proposalId)));

        assertThat(results).extracting(ProposalLikeResult::getLikeCount).allMatch(likeCount -> likeCount >= 0);
        int likeCount = likeCount(proposalId);
        assertThat(likeCount).isEqualTo(count("select count(*) from proposal_likes where proposal_id = ?", proposalId));
        // 4번 학생의 취소와 재공감은 순서에 따라 결과가 달라진다
        assertThat(likeCount).isBetween(2, 3);
        assertThat(likedProfileIds(proposalId)).contains(profileId(1), profileId(2))
                .doesNotContain(profileId(3), profileId(5));
    }

    @Test
    @DisplayName("PostgreSQL에서 공감 기록 저장과 공감 수 갱신 뒤에 실패하면 둘 다 롤백되고 재요청이 한 번만 반영한다")
    void rollsBackLikeWhenFailingAfterWrites() {
        Long proposalId = saveProposal(null);
        String student = givenStudent(2, null);
        String other = givenStudent(3, null);
        proposalFacade.likeProposal(other, proposalId);
        // 공감 기록 저장과 공감 수 갱신을 DB에 보낸 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            proposalRepository.flush();
            throw new IllegalStateException("공감 저장 실패");
        }).when(proposalService).likeProposal(anyLong(), anyLong(), any());

        assertThatThrownBy(() -> proposalFacade.likeProposal(student, proposalId))
                .isInstanceOf(IllegalStateException.class);

        assertLikes(proposalId, 1, profileId(3));

        reset(proposalService);
        assertThat(proposalFacade.likeProposal(student, proposalId).getLikeCount()).isEqualTo(2);
        assertLikes(proposalId, 2, profileId(2), profileId(3));
    }

    @Test
    @DisplayName("PostgreSQL에서 공감 기록 삭제와 공감 수 갱신 뒤에 실패하면 기존 기록과 공감 수가 함께 복구되고 재요청이 한 번만 반영한다")
    void rollsBackUnlikeWhenFailingAfterWrites() {
        Long proposalId = saveProposal(null);
        String student = givenStudent(2, null);
        String other = givenStudent(3, null);
        proposalFacade.likeProposal(student, proposalId);
        proposalFacade.likeProposal(other, proposalId);
        // 공감 기록 삭제와 공감 수 갱신을 DB에 보낸 뒤 같은 트랜잭션에서 실패하게 한다
        doAnswer(invocation -> {
            invocation.callRealMethod();
            proposalRepository.flush();
            throw new IllegalStateException("공감 삭제 실패");
        }).when(proposalService).unlikeProposal(anyLong(), anyLong(), any());

        assertThatThrownBy(() -> proposalFacade.unlikeProposal(student, proposalId))
                .isInstanceOf(IllegalStateException.class);

        assertLikes(proposalId, 2, profileId(2), profileId(3));

        reset(proposalService);
        assertThat(proposalFacade.unlikeProposal(student, proposalId).getLikeCount()).isEqualTo(1);
        assertLikes(proposalId, 1, profileId(3));
    }

    private static long profileId(int number) {
        return 987_200L + number;
    }

    private static String userId(int number) {
        return "01K58M6PJV8VAJMXHBHJ2LIKS" + number;
    }

    // 학생 프로필이 있는 활성 학생으로 로그인한 상태를 만든다. 학생 프로필 ID는 profileId(number)다
    private String givenStudent(int number, String demoSessionId) {
        String username = givenStudentWithoutProfile(number, demoSessionId);
        when(studentService.findStudentProfileByUserId(userId(number))).thenReturn(Optional.of(
                Student.builder().id(profileId(number)).userId(userId(number)).build()));
        return username;
    }

    private String givenStudentWithoutProfile(int number, String demoSessionId) {
        String username = "TEST_LIKE_STUDENT_" + number;
        when(userService.getActiveUser(username)).thenReturn(User.builder()
                .id(userId(number)).username(username).role(UserRole.STUDENT).demoSessionId(demoSessionId).build());
        return username;
    }

    private void givenOwner(String demoSessionId) {
        when(userService.getActiveUser(OWNER_USERNAME)).thenReturn(User.builder()
                .id(OWNER_USER_ID).username(OWNER_USERNAME).role(UserRole.OWNER).demoSessionId(demoSessionId)
                .build());
    }

    // 상세 조회가 참조하는 매장과 작성 학생. 공감과 무관한 값이라 고정한다
    private void givenDetailReferences() {
        when(ownerService.getOwnerProfileById(OWNER_PROFILE_ID)).thenReturn(
                Owner.builder().id(OWNER_PROFILE_ID).userId(OWNER_USER_ID).storeName("가꿈 카페").build());
        when(studentService.getStudentProfile(AUTHOR_PROFILE_ID)).thenReturn(
                Student.builder().id(AUTHOR_PROFILE_ID).userId(userId(1)).build());
        when(userService.getUser(userId(1))).thenReturn(User.builder().id(userId(1)).name("김학생").build());
    }

    private Long saveProposal(String demoSessionId) {
        Proposal proposal = proposalRepository.saveAndFlush(Proposal.create(
                AUTHOR_PROFILE_ID, OWNER_PROFILE_ID, "메뉴판 개선 제안", "문제", "해결", "계획",
                50_000L, 3, 7, List.of(), demoSessionId));
        proposalIds.add(proposal.getId());
        return proposal.getId();
    }

    private void assertDetail(String username, Long proposalId, int likeCount, boolean likedByMe) {
        ProposalDetailResult detail = proposalFacade.getProposalDetail(username, proposalId);
        assertThat(detail.getLikeCount()).isEqualTo(likeCount);
        assertThat(detail.isLikedByMe()).isEqualTo(likedByMe);
    }

    // 탐색은 조회자의 데모 세션에 속한 제안만 내리므로 이 테스트가 만든 제안 한 건만 나온다
    private void assertExploreCard(String username, Long proposalId, int likeCount, boolean likedByMe) {
        assertThat(exploreFacade.explore(ExploreCommand.of(username, null, ExploreType.PROPOSAL, ExploreSort.LIKES,
                20, null)).getItems())
                .singleElement()
                .isInstanceOfSatisfying(ProposalCardResult.class, card -> {
                    assertThat(card.getProposalId()).isEqualTo(proposalId);
                    assertThat(card.getLikeCount()).isEqualTo(likeCount);
                    assertThat(card.isLikedByMe()).isEqualTo(likedByMe);
                });
    }

    // 공감 수와 공감 기록이 함께 기대한 상태인지 확인한다
    private void assertLikes(Long proposalId, int likeCount, Long... studentProfileIds) {
        assertThat(likeCount(proposalId)).isEqualTo(likeCount);
        assertThat(likedProfileIds(proposalId)).containsExactlyInAnyOrder(studentProfileIds);
    }

    private int likeCount(Long proposalId) {
        return count("select like_count from proposals where id = ?", proposalId);
    }

    private List<Long> likedProfileIds(Long proposalId) {
        return jdbcTemplate.queryForList(
                "select student_profile_id from proposal_likes where proposal_id = ?", Long.class, proposalId);
    }

    private long maxProposalId() {
        Long max = jdbcTemplate.queryForObject("select coalesce(max(id), 0) from proposals", Long.class);
        return max == null ? 0 : max;
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROPOSAL_NOT_FOUND));
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static <T> List<Callable<T>> repeat(int requests, Callable<T> action) {
        List<Callable<T>> actions = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            actions.add(action);
        }
        return actions;
    }

    // 모든 요청을 각자의 스레드와 트랜잭션에서 같은 순간에 시작한다
    private <T> List<T> runConcurrently(List<Callable<T>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
