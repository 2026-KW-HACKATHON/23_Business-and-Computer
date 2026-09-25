package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.job.dto.ClosedJobListResponse;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;

import tools.jackson.databind.ObjectMapper;

class JobFacadeClosedListTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final String WORKER_USER_ID_1 = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String WORKER_USER_ID_2 = "01K58M6PJV8VAJMXHBHJ2PNB7E";

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final JobService jobService = mock(JobService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final JobFacade jobFacade = new JobFacade(
            userService, ownerService, jobService, specialtyCategoryService, studentService);

    @Test
    @DisplayName("CLOSED 의뢰를 작업자 이름과 카테고리, 완료 날짜가 있는 배열 응답으로 조립한다")
    void assemblesClosedJobs() {
        givenOwner();
        when(jobService.getClosedJobs(any(GetClosedJobsCommand.class))).thenReturn(List.of(
                ClosedJobData.of(job(44L, 32L, LocalDateTime.of(2026, 9, 27, 9, 30)), List.of(21L)),
                ClosedJobData.of(job(42L, 21L, LocalDateTime.of(2026, 9, 25, 18, 0)), List.of(12L, 11L, 21L))));
        when(studentService.getStudentProfilesByIds(List.of(32L, 21L))).thenReturn(Map.of(
                21L, Student.builder().id(21L).userId(WORKER_USER_ID_1).build(),
                32L, Student.builder().id(32L).userId(WORKER_USER_ID_2).build()));
        when(userService.getUsersByIds(any())).thenReturn(Map.of(
                WORKER_USER_ID_1, User.builder().id(WORKER_USER_ID_1).name("김람가").build(),
                WORKER_USER_ID_2, User.builder().id(WORKER_USER_ID_2).name("김가꿈").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(11L, 12L, 21L)))
                .thenReturn(Map.of(
                        11L, SpecialtyDetail.of(11L, "그래픽", 3L, "디자인"),
                        12L, SpecialtyDetail.of(12L, "편집", 3L, "디자인"),
                        21L, SpecialtyDetail.of(21L, "영상", 5L, "사진·영상")));

        ClosedJobListResult result = jobFacade.getClosedJobs(USERNAME);
        ClosedJobListResponse response = ClosedJobListResponse.from(result);
        String json = new ObjectMapper().writeValueAsString(ApiResponse.success(response));

        assertThat(response.getJobs()).extracting(item -> item.getJobId()).containsExactly(44L, 42L);
        assertThat(json).contains("\"success\":true", "\"data\":[", "\"studentProfileId\":21",
                "\"name\":\"김람가\"", "\"completedAt\":\"2026-09-25\"");
        assertThat(json).contains("\"specialtyCategories\":[{\"id\":3,\"name\":\"디자인\"}",
                "{\"id\":5,\"name\":\"사진·영상\"}]");
        assertThat(json).doesNotContain("\"jobs\"", "\"specialties\"", "\"studentNumber\"");

        ArgumentCaptor<GetClosedJobsCommand> command = ArgumentCaptor.forClass(GetClosedJobsCommand.class);
        verify(jobService).getClosedJobs(command.capture());
        assertThat(command.getValue().getOwnerProfileId()).isEqualTo(5L);
        verify(userService).getUsersByIds(any());
    }

    @Test
    @DisplayName("CLOSED 의뢰가 없으면 연관 데이터를 조회하지 않고 빈 배열을 만든다")
    void returnsEmptyList() {
        givenOwner();
        when(jobService.getClosedJobs(any(GetClosedJobsCommand.class))).thenReturn(List.of());

        ClosedJobListResponse response = ClosedJobListResponse.from(jobFacade.getClosedJobs(USERNAME));

        assertThat(new ObjectMapper().writeValueAsString(ApiResponse.success(response)))
                .contains("\"data\":[]");
        verifyNoInteractions(studentService, specialtyCategoryService);
    }

    @Test
    @DisplayName("연결된 학생 프로필이 없으면 CLOSED 의뢰 조립을 실패시킨다")
    void rejectsMissingStudent() {
        givenOwner();
        when(jobService.getClosedJobs(any(GetClosedJobsCommand.class))).thenReturn(List.of(
                ClosedJobData.of(job(42L, 21L, LocalDateTime.of(2026, 9, 25, 18, 0)), List.of())));
        when(studentService.getStudentProfilesByIds(List.of(21L)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> jobFacade.getClosedJobs(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        verifyNoInteractions(specialtyCategoryService);
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(OWNER_USER_ID).build());
        when(ownerService.getOwnerProfile(OWNER_USER_ID)).thenReturn(Owner.builder().id(5L).build());
    }

    private Job job(Long id, Long studentId, LocalDateTime completedAt) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .selectedStudentProfileId(studentId)
                .completedAt(completedAt)
                .build();
    }
}
