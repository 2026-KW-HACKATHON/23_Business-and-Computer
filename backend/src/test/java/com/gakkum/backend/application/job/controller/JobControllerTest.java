package com.gakkum.backend.application.job.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("보낸 의뢰 목록 조회 컨트롤러 (GET /me/jobs?status=OPEN)")
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
        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }
}
