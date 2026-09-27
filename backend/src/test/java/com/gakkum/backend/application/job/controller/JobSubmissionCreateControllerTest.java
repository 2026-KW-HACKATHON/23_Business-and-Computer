package com.gakkum.backend.application.job.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.PrepareSubmissionFileUploadCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;
import com.gakkum.backend.domain.job.dto.JobSubmissionFileType;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

@DisplayName("학생 초안 제출 컨트롤러 (POST /jobs/{jobId}/submission, /uploads)")
class JobSubmissionCreateControllerTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String FILE_URL =
            "https://bucket.s3.ap-northeast-2.amazonaws.com/job-submissions/42/7/f/draft.pdf";
    private static final String UPLOAD_BODY =
            "{\"type\":\"FILE\",\"fileName\":\"draft.pdf\",\"contentType\":\"application/pdf\",\"size\":1048576}";

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
    @DisplayName("업로드 준비는 201과 업로드 URL, 필수 헤더, 만료 시각, 공개 파일 URL을 반환한다")
    void returnsCreatedUpload() throws Exception {
        when(jobFacade.prepareSubmissionFileUpload(any())).thenReturn(PrepareSubmissionFileUploadResult.of(
                FILE_URL + "?X-Amz-Signature=abc",
                Map.of("content-type", "application/pdf"),
                LocalDateTime.of(2026, 9, 27, 21, 10),
                FILE_URL));

        mockMvc.perform(post("/jobs/42/submission/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPLOAD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value(FILE_URL + "?X-Amz-Signature=abc"))
                .andExpect(jsonPath("$.data.uploadHeaders.content-type").value("application/pdf"))
                .andExpect(jsonPath("$.data.uploadUrlExpiresAt").value("2026-09-27T21:10:00"))
                .andExpect(jsonPath("$.data.fileUrl").value(FILE_URL));

        ArgumentCaptor<PrepareSubmissionFileUploadCommand> captor =
                ArgumentCaptor.forClass(PrepareSubmissionFileUploadCommand.class);
        verify(jobFacade).prepareSubmissionFileUpload(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getJobId()).isEqualTo(42L);
        assertThat(captor.getValue().getType()).isEqualTo(JobSubmissionFileType.FILE);
        assertThat(captor.getValue().getFileName()).isEqualTo("draft.pdf");
        assertThat(captor.getValue().getSize()).isEqualTo(1048576L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"TEXT\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":1}",
            "{\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":1}",
            "{\"type\":\"FILE\",\"fileName\":\"../a.pdf\",\"contentType\":\"application/pdf\",\"size\":1}",
            "{\"type\":\"FILE\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"size\":0}"})
    @DisplayName("업로드 준비 요청 형식이 잘못되면 COMMON_400으로 거부한다")
    void rejectsInvalidUploadRequest(String body) throws Exception {
        mockMvc.perform(post("/jobs/42/submission/uploads").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }

    @Test
    @DisplayName("초안 제출은 201과 제출 ID, 의뢰 ID, DRAFT, 수정 번호 0, PENDING을 반환한다")
    void returnsCreatedDraft() throws Exception {
        when(jobFacade.submitDraft(any())).thenReturn(JobSubmissionCreateResult.from(JobSubmission.builder()
                .id(81L)
                .jobId(42L)
                .submissionType(JobSubmissionType.DRAFT)
                .revisionNumber(0)
                .reviewStatus(JobSubmissionReviewStatus.PENDING)
                .build()));

        mockMvc.perform(post("/jobs/42/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileUrls\":[\"" + FILE_URL + "\"],\"message\":\"  초안 작업물을 전달드립니다.  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.submissionId").value(81))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.revisionNumber").value(0))
                .andExpect(jsonPath("$.data.reviewStatus").value("PENDING"));

        ArgumentCaptor<CreateJobSubmissionCommand> captor = ArgumentCaptor.forClass(CreateJobSubmissionCommand.class);
        verify(jobFacade).submitDraft(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(captor.getValue().getJobId()).isEqualTo(42L);
        assertThat(captor.getValue().getFileUrls()).containsExactly(FILE_URL);
        assertThat(captor.getValue().getMessage()).isEqualTo("초안 작업물을 전달드립니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fileUrls\":[],\"message\":\"초안\"}",
            "{\"message\":\"초안\"}",
            "{\"fileUrls\":[\"" + FILE_URL + "\"],\"message\":\"   \"}",
            "{\"fileUrls\":[\"" + FILE_URL + "\"]}",
            "{\"fileUrls\":[\" \"],\"message\":\"초안\"}",
            "{\"fileUrls\":[\"" + FILE_URL + "\",\"" + FILE_URL + "\"],\"message\":\"초안\"}",
            "{\"fileUrls\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"],\"message\":\"초안\"}"})
    @DisplayName("파일 목록이 비었거나 메시지가 공백뿐이거나 URL이 중복·초과되면 COMMON_400으로 거부한다")
    void rejectsInvalidDraftRequest(String body) throws Exception {
        mockMvc.perform(post("/jobs/42/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }

    @Test
    @DisplayName("jobId가 양수가 아니면 COMMON_400으로 거부한다")
    void rejectsNonPositiveJobId() throws Exception {
        mockMvc.perform(post("/jobs/0/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileUrls\":[\"" + FILE_URL + "\"],\"message\":\"초안\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(jobFacade);
    }

    @Test
    @DisplayName("비즈니스 거부는 정의된 상태 코드와 에러 코드로 응답한다")
    void mapsBusinessErrors() throws Exception {
        when(jobFacade.submitDraft(any()))
                .thenThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN))
                .thenThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS))
                .thenThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID));
        String body = "{\"fileUrls\":[\"" + FILE_URL + "\"],\"message\":\"초안\"}";

        mockMvc.perform(post("/jobs/42/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_403"));
        mockMvc.perform(post("/jobs/42/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_409_DUPLICATE"));
        mockMvc.perform(post("/jobs/42/submission").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("JOB_SUBMISSION_400_FILE_URL"));
    }
}
