package com.gakkum.backend.application.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        String sessionId = owner.get("demoSessionId").asString();
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
        assertThat(status("봄 신메뉴 전단지")).isEqualTo(JobStatus.OPEN);
        assertThat(status("신메뉴 인스타그램 홍보 게시물")).isEqualTo(JobStatus.OPEN);
        assertThat(status("메뉴판 디자인 변경")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("단골 쿠폰·도장카드 디자인")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("가게 소개 릴스 영상 편집")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("배달앱 리뷰 분석 리포트")).isEqualTo(JobStatus.MATCHED);
        assertThat(status("인스타 게시물 5개 제작")).isEqualTo(JobStatus.CLOSED);
        assertThat(status("가게 앞 입간판 시안")).isEqualTo(JobStatus.CLOSED);
        assertThat(status("포장 스티커 디자인")).isEqualTo(JobStatus.CANCELLED);
        assertThat(jobs.values()).filteredOn(job -> myStoreId.equals(job.getOwnerProfileId())).hasSize(12);

        assertThat(proposals.get("시험 기간 학생 할인 이벤트").getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposals.get("시험 기간 학생 할인 이벤트").getLikeCount()).isEqualTo(3);
        assertThat(proposals.get("점심 세트 메뉴판 정리").getStatus()).isEqualTo(ProposalStatus.AWAITING_START);
        assertThat(proposals.get("가게 소개글 다시 쓰기").getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposals.get("단체 주문 안내문 디자인").getStatus()).isEqualTo(ProposalStatus.REJECTED);

        // 이름으로 고른 특기가 모든 의뢰·제안과 예시 학생에 이어진다
        List<Long> jobIds = jobs.values().stream().map(Job::getId).toList();
        List<Long> proposalIds = proposals.values().stream().map(Proposal::getId).toList();
        assertThat(countDistinct("job_specialties", "job_id", jobIds)).isEqualTo(jobIds.size());
        assertThat(countDistinct("proposal_specialties", "proposal_id", proposalIds)).isEqualTo(proposalIds.size());
    }

    private long countDistinct(String table, String column, List<Long> ids) {
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return jdbcTemplate.queryForObject(
                "select count(distinct " + column + ") from " + table + " where " + column + " in (" + in + ")", Long.class);
    }

    @Test
    @DisplayName("사장님 홈·내 활동·결제 내역·채팅·탐색 API가 예시 데이터를 오류 없이 돌려준다")
    void ownerScreensReadSeededData() throws Exception {
        assertThat(read(ownerToken, "/me/jobs?status=OPEN")).contains("봄 신메뉴 전단지", "신메뉴 인스타그램 홍보 게시물");
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
        assertThat(read(ownerToken, "/jobs/" + id("배달앱 리뷰 분석 리포트") + "/submission")).contains("수정안");
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
                .contains("자막을 조금 키우고");
        read(studentToken, "/jobs/" + id("쿠폰·스티커 디자인") + "/submissions/latest");
        assertThat(read(studentToken, "/proposals/" + proposals.get("점심 세트 메뉴판 정리").getId()))
                .contains("점심 손님이 많아서 기대돼요!");
        read(studentToken, "/jobs/" + id("영어·중국어 메뉴판 번역"));
        read(studentToken, "/jobs/" + id("배달 리뷰 이벤트 배너"));
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
