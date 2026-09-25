package com.gakkum.backend.application.job.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("보낸 의뢰 목록 조회 컨트롤러 (GET /me/jobs)")
class JobControllerTest {

    private static final String USERNAME = "KAKAO_12345";

    private final JobFacade jobFacade = mock(JobFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(jobFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("OPEN 상태이면 정해진 목록 응답 형식과 필드를 반환한다")
    void returnsOpenJobList() throws Exception {
        Job job = Job.builder()
                .id(42L)
                .title("가게 홍보 웹사이트 제작")
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .build();
        OpenJobResult result = OpenJobResult.of(
                OpenJobData.of(job, List.of(11L, 12L), 3),
                List.of(SpecialtyCategoryResult.of(1L, "개발", List.of(
                        SpecialtyResult.of(11L, "백엔드"),
                        SpecialtyResult.of(12L, "프론트엔드")))));
        when(jobFacade.getOpenJobs(USERNAME)).thenReturn(OpenJobListResult.of(List.of(result)));

        mockMvc.perform(get("/me/jobs").param("status", "OPEN").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.jobs[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[0].revisionCount").value(2))
                .andExpect(jsonPath("$.data.jobs[0].applicantCount").value(3));
        verify(jobFacade).getOpenJobs(USERNAME);
    }

    @Test
    @DisplayName("OPEN 의뢰가 없으면 jobs 빈 배열을 반환한다")
    void returnsEmptyJobs() throws Exception {
        when(jobFacade.getOpenJobs(USERNAME)).thenReturn(OpenJobListResult.of(List.of()));

        mockMvc.perform(get("/me/jobs").param("status", "OPEN").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
    }

    @Test
    @DisplayName("MATCHED 상태이면 학생 정보와 대기 중 제출물을 응답한다")
    void returnsMatchedJobList() throws Exception {
        Job job = Job.builder()
                .id(42L)
                .title("가게 홍보 웹사이트 제작")
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .selectedStudentProfileId(7L)
                .build();
        MatchedJobResult result = MatchedJobResult.of(
                MatchedJobData.of(
                        job, List.of(12L),
                        JobSubmission.builder()
                                .id(81L)
                                .submissionType(JobSubmissionType.DRAFT)
                                .build()),
                Student.builder()
                        .id(7L)
                        .studentNumber("2023123456")
                        .major("컴퓨터정보공학부")
                        .build(),
                List.of(SpecialtyCategoryResult.of(1L, "개발", List.of(
                        SpecialtyResult.of(12L, "프론트엔드")))));
        when(jobFacade.getMatchedJobs(USERNAME)).thenReturn(MatchedJobListResult.of(List.of(result)));

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].title").value("가게 홍보 웹사이트 제작"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.jobs[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[0].studentProfileId").value(7))
                .andExpect(jsonPath("$.data.jobs[0].studentNumber").value("2023123456"))
                .andExpect(jsonPath("$.data.jobs[0].major").value("컴퓨터정보공학부"))
                .andExpect(jsonPath("$.data.jobs[0].submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.jobs[0].pendingSubmissionId").value(81));
        verify(jobFacade).getMatchedJobs(USERNAME);
    }

    @Test
    @DisplayName("MATCHED 의뢰가 없으면 jobs 빈 배열을 반환한다")
    void returnsEmptyMatchedJobs() throws Exception {
        when(jobFacade.getMatchedJobs(USERNAME)).thenReturn(MatchedJobListResult.of(List.of()));

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
    }

    @Test
    @DisplayName("대기 중 제출물이 없는 MATCHED 의뢰는 제출물 필드를 null로 응답한다")
    void returnsNullSubmissionFields() throws Exception {
        Job job = Job.builder()
                .id(44L)
                .title("SNS 홍보 콘텐츠 제작")
                .draftDeadline(LocalDate.of(2026, 10, 15))
                .finalDeadline(LocalDate.of(2026, 10, 30))
                .selectedStudentProfileId(9L)
                .build();
        Student student = Student.builder()
                .id(9L)
                .studentNumber("2022123456")
                .major("미디어학부")
                .build();
        MatchedJobResult result = MatchedJobResult.of(
                MatchedJobData.of(job, List.of(), null), student, List.of());
        when(jobFacade.getMatchedJobs(USERNAME)).thenReturn(MatchedJobListResult.of(List.of(result)));

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].submissionType").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].pendingSubmissionId").value(nullValue()));
    }

    @Test
    @DisplayName("CLOSED 상태이면 완료 의뢰를 작업자와 날짜가 포함된 배열로 반환한다")
    void returnsClosedJobList() throws Exception {
        Job job = Job.builder()
                .id(42L)
                .title("가게 메뉴판 디자인")
                .selectedStudentProfileId(21L)
                .completedAt(LocalDateTime.of(2026, 9, 25, 18, 30))
                .build();
        ClosedJobResult result = ClosedJobResult.of(
                ClosedJobData.of(job, List.of(11L)),
                Student.builder().id(21L).build(),
                User.builder().name("김람가").build(),
                List.of(SpecialtyCategoryResult.of(3L, "디자인", List.of())));
        when(jobFacade.getClosedJobs(USERNAME)).thenReturn(ClosedJobListResult.of(List.of(result)));

        mockMvc.perform(get("/me/jobs").param("status", "CLOSED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].jobId").value(42))
                .andExpect(jsonPath("$.data[0].title").value("가게 메뉴판 디자인"))
                .andExpect(jsonPath("$.data[0].specialtyCategories[0].id").value(3))
                .andExpect(jsonPath("$.data[0].specialtyCategories[0].name").value("디자인"))
                .andExpect(jsonPath("$.data[0].specialtyCategories[0].specialties").doesNotExist())
                .andExpect(jsonPath("$.data[0].matchedWorker.studentProfileId").value(21))
                .andExpect(jsonPath("$.data[0].matchedWorker.name").value("김람가"))
                .andExpect(jsonPath("$.data[0].completedAt").value("2026-09-25"));
        verify(jobFacade).getClosedJobs(USERNAME);
    }

    @Test
    @DisplayName("CLOSED 의뢰가 없으면 data 빈 배열을 반환한다")
    void returnsEmptyClosedJobs() throws Exception {
        when(jobFacade.getClosedJobs(USERNAME)).thenReturn(ClosedJobListResult.of(List.of()));

        mockMvc.perform(get("/me/jobs").param("status", "CLOSED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(jobFacade).getClosedJobs(USERNAME);
    }

    @Test
    @DisplayName("상태값이 없으면 400을 반환하고 조회하지 않는다")
    void rejectsMissingStatus() throws Exception {
        mockMvc.perform(get("/me/jobs").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }

    @Test
    @DisplayName("지원하지 않는 상태값이면 400을 반환하고 조회하지 않는다")
    void rejectsUnsupportedStatus() throws Exception {
        mockMvc.perform(get("/me/jobs").param("status", "INVALID").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }
}
