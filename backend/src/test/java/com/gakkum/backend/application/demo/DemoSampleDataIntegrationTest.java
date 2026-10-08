package com.gakkum.backend.application.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.specialty.entity.Specialty;
import com.gakkum.backend.domain.specialty.entity.SpecialtyCategory;
import com.gakkum.backend.domain.specialty.repository.SpecialtyCategoryRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 새 데모 세션의 예시 데이터가 실제 화면 API에서 오류 없이 읽히는지 PostgreSQL로 확인한다.
 * 결제·제출물·제안 사이의 데이터 규칙은 조회할 때 500으로 드러나므로, 방문자 사장님·학생 토큰으로 홈·내 활동·상세 API를 모두 부른다.
 * 공용 DB에 쓰지 않도록 DATABASE_URL이 로컬 PostgreSQL일 때만 실행하고, 테스트 트랜잭션은 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1)[:/].*")
@DisplayName("데모 예시 데이터 화면 API (PostgreSQL)")
class DemoSampleDataIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> SAMPLE_SPECIALTIES = List.of(
            "메뉴판·가격표 디자인", "전단지·포스터 디자인", "간판·현수막 시안", "쿠폰·스티커·명함 디자인", "음식·매장 사진",
            "영상 제작 및 편집", "SNS 게시물", "홍보·이벤트 기획", "온라인 예약·주문서", "리뷰 분석", "소개·공지 글쓰기",
            "영어 번역", "중국어 번역");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SpecialtyCategoryRepository specialtyCategoryRepository;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private ProposalRepository proposalRepository;
    @Autowired
    private OwnerRepository ownerRepository;

    private String sessionId;
    private String ownerToken;
    private String studentToken;
    private Long myStoreId;
    private Map<String, Job> jobs;
    private Map<String, Proposal> proposals;

    @BeforeEach
    void createSession() throws Exception {
        // 운영 서버와 같은 이름의 특기를 두어 의뢰·제안·학생에 특기가 실제로 이어지는지 함께 확인한다
        Long categoryId = specialtyCategoryRepository.saveAndFlush(
                SpecialtyCategory.builder().name("데모 예시 대분류 " + UUID.randomUUID()).build()).getId();
        for (String name : SAMPLE_SPECIALTIES) {
            specialtyRepository.saveAndFlush(Specialty.builder().specialtyCategoryId(categoryId).name(name).build());
        }

        JsonNode owner = login("{\"role\":\"OWNER\"}");
        sessionId = owner.get("demoSessionId").asString();
        ownerToken = owner.get("accessToken").asString();
        studentToken = login("{\"role\":\"STUDENT\",\"demoSessionId\":\"" + sessionId + "\"}")
                .get("accessToken").asString();

        jobs = jobRepository.findAll().stream()
                .filter(job -> sessionId.equals(job.getDemoSessionId()))
                .collect(Collectors.toMap(Job::getTitle, job -> job));
        proposals = proposalRepository.findAll().stream()
                .filter(proposal -> sessionId.equals(proposal.getDemoSessionId()))
                .collect(Collectors.toMap(Proposal::getTitle, proposal -> proposal));
        myStoreId = ownerRepository.findAll().stream()
                .filter(store -> sessionId.equals(store.getDemoSessionId()) && store.getStoreName().startsWith("[데모]"))
                .findFirst().orElseThrow().getId();
    }

    @Test
    @DisplayName("방문자 가게에 모집 중·지원자 있음·작업 중·초안 도착·수정 중·수정안 도착·완료·취소 의뢰와 모든 상태의 받은 제안이 생긴다")
    void seedsEveryOwnerJobState() {
        assertThat(status("가을 신메뉴 전단지")).isEqualTo(JobStatus.OPEN);
        assertThat(status("신메뉴 인스타그램 홍보 게시물")).isEqualTo(JobStatus.OPEN);
        assertThat(status("메뉴판 디자인 변경")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("단골 쿠폰·도장카드 디자인")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("가게 소개 릴스 영상 편집")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("배달앱 리뷰 분석 리포트")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("인스타 게시물 5개 제작")).isEqualTo(JobStatus.CLOSED);
        assertThat(status("가게 앞 입간판 시안")).isEqualTo(JobStatus.CLOSED);
        assertThat(status("포장 스티커 디자인")).isEqualTo(JobStatus.CANCELLED);
        assertThat(jobs.values()).filteredOn(job -> myStoreId.equals(job.getOwnerProfileId())).hasSize(13);

        assertThat(proposals.get("시험 기간 학생 할인 이벤트").getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposals.get("시험 기간 학생 할인 이벤트").getLikeCount()).isEqualTo(3);
        assertThat(proposals.get("점심 세트 메뉴판 정리").getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(proposals.get("가게 소개글 다시 쓰기").getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposals.get("단체 주문 안내문 디자인").getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposals.get("가게 앞 모니터 메뉴 영상").getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposals.get("가게 앞 모니터 메뉴 영상").getRejectedBy()).isEqualTo(ProposalRejectedBy.OWNER);
        assertThat(proposals.get("대표 메뉴 사진 다시 찍기").getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(status("대표 메뉴 사진 다시 찍기")).isEqualTo(JobStatus.CLOSED);
        // 샘플의 학생 거절은 거절 주체와 거절 시각(의뢰 종료 시각과 같은 시점)을 함께 남긴다
        assertThat(proposals.get("단체 주문 안내문 디자인").getRejectedBy())
                .isEqualTo(com.gakkum.backend.domain.proposal.entity.ProposalRejectedBy.STUDENT);
        assertThat(proposals.get("단체 주문 안내문 디자인").getRejectedAt()).isNotNull();
        assertThat(proposals.get("시험 기간 학생 할인 이벤트").getRejectedBy()).isNull();

        // 이름으로 고른 특기가 모든 의뢰·제안과 예시 학생에 이어진다
        List<Long> jobIds = jobs.values().stream().map(Job::getId).toList();
        List<Long> proposalIds = proposals.values().stream().map(Proposal::getId).toList();
        assertThat(countDistinct("job_specialties", "job_id", jobIds)).isEqualTo(jobIds.size());
        assertThat(countDistinct("proposal_specialties", "proposal_id", proposalIds)).isEqualTo(proposalIds.size());
    }

    @Test
    @DisplayName("지난 작업의 작성 · 결제 · 제출 · 완료 · 후기 · 취소 시각이 마감과 순서에 맞는다")
    void seedsConsistentTimeline() {
        Instant now = Instant.now();
        // 완료: 작성 → 결제 → 초안(초안 마감 안) → 완료(최종 마감 안) → 후기
        for (String title : List.of("인스타 게시물 5개 제작", "가게 앞 입간판 시안", "여름 음료 포스터")) {
            Job job = jobs.get(title);
            Instant created = instant("select created_at from jobs where id = ?", job.getId());
            Instant approved = instant("select approved_at from payments where job_id = ?", job.getId());
            Instant firstSubmission = instant("select min(created_at) from job_submissions where job_id = ?", job.getId());
            Instant lastSubmission = instant("select max(created_at) from job_submissions where job_id = ?", job.getId());
            Instant completed = instant("select completed_at from jobs where id = ?", job.getId());
            assertThat(created).as(title).isBefore(approved);
            assertThat(approved).as(title).isBefore(firstSubmission);
            assertThat(koreanDate(firstSubmission)).as(title).isBeforeOrEqualTo(job.getDraftDeadline());
            assertThat(lastSubmission).as(title).isBefore(completed);
            assertThat(koreanDate(completed)).as(title).isBeforeOrEqualTo(job.getFinalDeadline());
            assertThat(completed).as(title).isBefore(now);
        }
        for (String title : List.of("인스타 게시물 5개 제작", "여름 음료 포스터")) {
            Job job = jobs.get(title);
            assertThat(instant("select created_at from reviews where job_id = ?", job.getId())).as(title)
                    .isAfter(instant("select completed_at from jobs where id = ?", job.getId()))
                    .isBefore(now);
        }

        // 진행 중: 결제 뒤 초안을 초안 마감 안에 냈다
        for (String title : List.of("단골 쿠폰·도장카드 디자인", "가게 소개 릴스 영상 편집", "배달앱 리뷰 분석 리포트",
                "쿠폰·스티커 디자인")) {
            Job job = jobs.get(title);
            Instant firstSubmission = instant("select min(created_at) from job_submissions where job_id = ?", job.getId());
            assertThat(instant("select approved_at from payments where job_id = ?", job.getId())).as(title)
                    .isBefore(firstSubmission);
            assertThat(koreanDate(firstSubmission)).as(title).isBeforeOrEqualTo(job.getDraftDeadline());
            assertThat(firstSubmission).as(title).isBefore(now);
        }

        // 취소 · 거절: 결제 뒤 끝났고 환불 시각이 끝난 시각과 같다
        for (String title : List.of("포장 스티커 디자인", "단체 주문 안내문 디자인")) {
            Job job = jobs.get(title);
            Instant closed = instant("select completed_at from jobs where id = ?", job.getId());
            assertThat(instant("select approved_at from payments where job_id = ?", job.getId())).as(title)
                    .isBefore(closed);
            assertThat(instant("select refunded_at from payments where job_id = ?", job.getId())).as(title)
                    .isEqualTo(closed);
            assertThat(closed).as(title).isBefore(now);
        }

        // 받은 제안은 결제보다 먼저 왔다
        Job lunch = jobs.get("점심 세트 메뉴판 정리");
        assertThat(instant("select created_at from proposals where id = ?", proposals.get("점심 세트 메뉴판 정리").getId()))
                .isBefore(instant("select approved_at from payments where job_id = ?", lunch.getId()));
    }

    @Test
    @DisplayName("방문자 사장님·학생 알림이 예시 데이터의 일과 같은 시각으로 쌓이고, 어제 온 알림만 읽지 않은 새 알림이다")
    void seedsVisitorNotifications() throws Exception {
        JsonNode ownerItems = data(read(ownerToken, "/me/notifications?size=100")).get("items");
        JsonNode studentItems = data(read(studentToken, "/me/notifications?size=100")).get("items");

        // 사장님: 지원 9 · 받은 제안 7 · 작업 시작 2 · 초안 6 · 수정안 2 · 후기 요청 3 · 환불 2
        assertThat(ownerItems).hasSize(31);
        assertThat(types(ownerItems)).containsOnly("JOB_APPLICATION_RECEIVED", "PROPOSAL_RECEIVED", "JOB_STARTED",
                "JOB_DRAFT_SUBMITTED", "JOB_REVISION_SUBMITTED", "JOB_REVIEW_REQUESTED", "PAYMENT_REFUNDED");
        // 학생: 선정 4 · 미선정 1 · 제안 수락 3 · 수정 요청 1 · 정산 3 · 후기 3
        assertThat(studentItems).hasSize(15);
        assertThat(types(studentItems)).containsOnly("JOB_APPLICATION_SELECTED", "JOB_APPLICATION_REJECTED",
                "PROPOSAL_ACCEPTED", "JOB_REVISION_REQUESTED", "PAYMENT_SETTLED", "JOB_REVIEW_RECEIVED");

        // 어제 온 알림만 읽지 않았다: 사장님은 지원 2 · 제안 1 · 초안 1 · 수정안 1, 학생은 제안 수락 · 수정 요청
        assertThat(data(read(ownerToken, "/me/notifications/unread-count")).get("unreadCount").asLong()).isEqualTo(5);
        assertThat(data(read(studentToken, "/me/notifications/unread-count")).get("unreadCount").asLong())
                .isEqualTo(2);
        Instant unreadFrom = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toInstant();
        for (JsonNode item : List.of(ownerItems, studentItems).stream().flatMap(items -> items.valueStream()).toList()) {
            Instant created = OffsetDateTime.parse(item.get("createdAt").asString()).toInstant();
            JsonNode readAt = item.get("readAt");
            assertThat(readAt == null || readAt.isNull()).as(item.get("title").asString())
                    .isEqualTo(!created.isBefore(unreadFrom));
        }

        // 알림 시각은 그 일의 시각과 같다
        Long coupon = id("단골 쿠폰·도장카드 디자인");
        assertThat(notifiedAt("JOB_DRAFT_SUBMITTED", coupon))
                .isEqualTo(instant("select min(created_at) from job_submissions where job_id = ?", coupon));
        Long lunch = id("점심 세트 메뉴판 정리");
        assertThat(notifiedAt("PROPOSAL_ACCEPTED", lunch))
                .isEqualTo(instant("select approved_at from payments where job_id = ?", lunch));
        Long posts = id("인스타 게시물 5개 제작");
        assertThat(notifiedAt("JOB_REVIEW_RECEIVED", posts))
                .isEqualTo(instant("select created_at from reviews where job_id = ?", posts));

        // 알림이 가리키는 의뢰 · 제안 · 채팅방 · 결제가 모두 있다
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from notifications n join users u on u.user_id = n.recipient_user_id
                where u.demo_session_id = ? and not (
                    (n.target_type = 'JOB' and exists (select 1 from jobs j where j.id::text = n.target_id))
                    or (n.target_type = 'PROPOSAL' and exists (select 1 from proposals p where p.id::text = n.target_id))
                    or (n.target_type = 'CHAT_ROOM' and exists (select 1 from chat_rooms c where c.id = n.target_id))
                    or (n.target_type = 'PAYMENT' and exists (select 1 from payments p where p.id::text = n.target_id)))
                """, Long.class, sessionId)).isZero();
    }

    private Instant notifiedAt(String type, Long jobId) {
        return jdbcTemplate.queryForObject("select created_at from notifications where type = ? and target_id = ?",
                Timestamp.class, type, String.valueOf(jobId)).toInstant();
    }

    private static List<String> types(JsonNode items) {
        return items.valueStream().map(item -> item.get("type").asString()).toList();
    }

    private static JsonNode data(String body) {
        JsonNode root = JSON.readTree(body);
        return root.has("data") ? root.get("data") : root;
    }

    @Test
    @DisplayName("데모 학생 학번은 20 + 입학 연도 두 자리(21~25) + 0 으로 시작해 화면에 「24학번」처럼 보인다")
    void seedsAdmissionYearStudentNumbers() {
        List<String> numbers = jdbcTemplate.queryForList("""
                select s.student_number from student_profiles s join users u on u.user_id = s.user_id
                where u.demo_session_id = ?""", String.class, sessionId);

        assertThat(numbers).hasSize(6).allMatch(number -> number.matches("^20(2[1-5])0\\d{5}$"));
        assertThat(numbers).anyMatch(number -> number.startsWith("20240"));
    }

    private Instant instant(String sql, Long id) {
        return jdbcTemplate.queryForObject(sql, Timestamp.class, id).toInstant();
    }

    private static LocalDate koreanDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneId.of("Asia/Seoul"));
    }

    private long countDistinct(String table, String column, List<Long> ids) {
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return jdbcTemplate.queryForObject(
                "select count(distinct " + column + ") from " + table + " where " + column + " in (" + in + ")", Long.class);
    }

    @Test
    @DisplayName("사장님 홈·내 활동·결제 내역·채팅·탐색 API가 예시 데이터를 오류 없이 돌려준다")
    void ownerScreensReadSeededData() throws Exception {
        assertThat(read(ownerToken, "/me/jobs?status=OPEN")).contains("가을 신메뉴 전단지", "신메뉴 인스타그램 홍보 게시물");
        assertThat(read(ownerToken, "/me/jobs?status=MATCHED"))
                .contains("메뉴판 디자인 변경", "단골 쿠폰·도장카드 디자인", "가게 소개 릴스 영상 편집", "배달앱 리뷰 분석 리포트",
                        "가게 소개글 다시 쓰기");
        assertThat(read(ownerToken, "/me/jobs?status=CLOSED"))
                .contains("인스타 게시물 5개 제작", "가게 앞 입간판 시안", "포장 스티커 디자인");
        assertThat(read(ownerToken, "/me/received-proposals"))
                .contains("시험 기간 학생 할인 이벤트", "영어 메뉴판 번역", "점심 세트 메뉴판 정리", "가게 소개글 다시 쓰기",
                        "단체 주문 안내문 디자인");
        assertThat(read(ownerToken, "/payments")).contains("포장 스티커 디자인", "인스타 게시물 5개 제작");
        read(ownerToken, "/owners/me");
        assertThat(read(ownerToken, "/me/chat-rooms")).contains("수정 요청 남겼어요");
        assertThat(read(ownerToken, "/explore?type=ALL&sort=LATEST&size=20")).contains("공룡카페");

        read(ownerToken, "/jobs/" + id("신메뉴 인스타그램 홍보 게시물") + "/applications?sort=LATEST");
        assertThat(read(ownerToken, "/jobs/" + id("단골 쿠폰·도장카드 디자인") + "/submission")).contains("도장카드 시안");
        assertThat(read(ownerToken, "/jobs/" + id("배달앱 리뷰 분석 리포트") + "/submission")).contains("포장 의견을 한 장으로");
        read(ownerToken, "/jobs/" + id("인스타 게시물 5개 제작") + "/result");
        read(ownerToken, "/jobs/" + id("가게 앞 입간판 시안") + "/result");
        for (Job job : jobs.values()) {
            if (myStoreId.equals(job.getOwnerProfileId())) {
                read(ownerToken, "/jobs/" + job.getId());
            }
        }
        for (Proposal proposal : proposals.values()) {
            if (myStoreId.equals(proposal.getOwnerProfileId())) {
                read(ownerToken, "/proposals/" + proposal.getId());
            }
        }
    }

    @Test
    @DisplayName("학생 홈·내 활동·정산·채팅·공감 API가 예시 데이터를 오류 없이 돌려준다")
    void studentScreensReadSeededData() throws Exception {
        assertThat(read(studentToken, "/me/proposals"))
                .contains("시험 기간 학생 할인 이벤트", "점심 세트 메뉴판 정리", "가게 소개글 다시 쓰기");
        assertThat(read(studentToken, "/me/job-applications")).contains("영어·중국어 메뉴판 번역", "배달 리뷰 이벤트 배너");
        assertThat(read(studentToken, "/me/jobs?status=MATCHED"))
                .contains("가게 소개 릴스 영상 편집", "쿠폰·스티커 디자인", "가게 소개글 다시 쓰기");
        assertThat(read(studentToken, "/explore?type=PROPOSAL&sort=LIKES&size=5"))
                .contains("인기 메뉴를 강조한 메뉴판 시안", "시험 기간 광운대생 이벤트 기획");
        assertThat(read(studentToken, "/explore/stores?sort=OLDEST&size=100")).contains("치킨플러스");
        read(studentToken, "/students/me");
        read(studentToken, "/settlements");
        assertThat(read(studentToken, "/me/chat-rooms")).contains("공룡카페");

        assertThat(read(studentToken, "/jobs/" + id("가게 소개 릴스 영상 편집") + "/submissions/latest"))
                .contains("자막이 조금 작아서 한 단계 키워 주세요");
        read(studentToken, "/jobs/" + id("쿠폰·스티커 디자인") + "/submissions/latest");
        assertThat(read(studentToken, "/proposals/" + proposals.get("점심 세트 메뉴판 정리").getId()))
                .contains("점심 손님이 많아서 기대돼요!");
        read(studentToken, "/jobs/" + id("영어·중국어 메뉴판 번역"));
        read(studentToken, "/jobs/" + id("배달 리뷰 이벤트 배너"));
    }

    @Test
    @DisplayName("지원자 프로필·학생 내 정보·채팅 목록에 지난 작업 후기·자격증·대화가 채워진다")
    void fillsProfilesAndChats() throws Exception {
        String applications = read(ownerToken, "/jobs/" + id("신메뉴 인스타그램 홍보 게시물") + "/applications?sort=LATEST");
        Matcher matcher = Pattern.compile("\"(?:jobApplicationId|applicationId)\":(\\d+)").matcher(applications);
        StringBuilder profiles = new StringBuilder();
        while (matcher.find()) {
            profiles.append(read(ownerToken, "/jobs/" + id("신메뉴 인스타그램 홍보 게시물") + "/applications/"
                    + matcher.group(1) + "/profile"));
        }
        assertThat(profiles.toString())
                .contains("중식 메뉴판 가격표 정리", "메뉴가 한눈에 들어와서 주문이 빨라졌어요.", "GTQ 포토샵 1급")
                .contains("치킨 신메뉴 릴스", "GTQ 포토샵 2급");

        assertThat(read(studentToken, "/students/me")).contains("GTQ 포토샵 1급");
        assertThat(read(studentToken, "/settlements")).contains("치킨 세트 메뉴 카드뉴스", "인스타 게시물 5개 제작");
        assertThat(read(ownerToken, "/me/chat-rooms"))
                .contains("튀김은 세트 칸으로 따로 빼 볼게요.", "두 시안 중에 골라 주세요.");
        assertThat(read(studentToken, "/me/chat-rooms"))
                .contains("네! 내일 오후 3시 괜찮으세요?", "세트 주문이 많이 들어왔으면 좋겠어요.");
    }

    @Test
    @DisplayName("채팅방은 마지막 메시지를 받는 쪽이 아직 읽지 않은 방에만 안 읽은 메시지가 하나 남는다")
    void seedsUnreadChatMessages() throws Exception {
        assertThat(unreadRooms(ownerToken)).isEqualTo(Map.of("메뉴판 디자인 변경", 1L, "단골 쿠폰·도장카드 디자인", 1L,
                "배달앱 리뷰 분석 리포트", 1L, "가게 소개글 다시 쓰기", 1L));
        assertThat(unreadRooms(studentToken)).isEqualTo(Map.of("가게 소개 릴스 영상 편집", 1L));
    }

    /** 안 읽은 메시지가 있는 채팅방의 의뢰 제목 → 안 읽은 메시지 수 */
    private Map<String, Long> unreadRooms(String token) throws Exception {
        Map<String, Long> unread = new HashMap<>();
        for (JsonNode room : data(read(token, "/me/chat-rooms")).get("rooms")) {
            if (room.get("unreadCount").asLong() > 0) {
                unread.put(room.get("jobTitle").asString(), room.get("unreadCount").asLong());
            }
        }
        return unread;
    }

    private JobStatus status(String title) {
        return jobs.get(title).getStatus();
    }

    private Long id(String title) {
        return jobs.get(title).getId();
    }

    private JsonNode login(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/demo/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as("POST /demo/login").isEqualTo(200);
        JsonNode root = JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.has("data") ? root.get("data") : root;
    }

    /** 200으로 읽히는지 확인하고 응답 본문을 돌려준다. */
    private String read(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as("GET %s → %s", path, body).isEqualTo(200);
        return body;
    }
}
