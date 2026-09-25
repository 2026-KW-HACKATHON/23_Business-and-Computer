package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.job.dto.MatchedJobListResponse;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
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

class JobFacadeMatchedListTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final OwnerService ownerService = new OwnerService(ownerRepository);
    private final JobService jobService = mock(JobService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final JobFacade jobFacade = new JobFacade(
            userService, ownerService, jobService, specialtyCategoryService, studentService);

    @Test
    @DisplayName("사업주의 MATCHED 의뢰를 학생 정보와 특기, 제출물 정보로 조립한다")
    void assemblesMatchedJobs() {
        givenOwner();
        when(jobService.getMatchedJobs(any(GetMatchedJobsCommand.class))).thenReturn(List.of(
                MatchedJobData.of(job(42L, 7L), List.of(12L, 11L), submission(81L, JobSubmissionType.DRAFT)),
                MatchedJobData.of(job(43L, 8L), List.of(21L), submission(87L, JobSubmissionType.REVISION)),
                MatchedJobData.of(job(44L, 9L), List.of(22L), null)));
        when(studentService.getStudentProfilesByIds(List.of(7L, 8L, 9L))).thenReturn(Map.of(
                7L, student(7L, "2023123456", "컴퓨터정보공학부"),
                8L, student(8L, "2024123456", "시각디자인학부"),
                9L, student(9L, "2022123456", "미디어학부")));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(11L, 12L, 21L, 22L)))
                .thenReturn(Map.of(
                        11L, SpecialtyDetail.of(11L, "백엔드", 1L, "개발"),
                        12L, SpecialtyDetail.of(12L, "프론트엔드", 1L, "개발"),
                        21L, SpecialtyDetail.of(21L, "그래픽 디자인", 2L, "디자인"),
                        22L, SpecialtyDetail.of(22L, "콘텐츠 디자인", 2L, "디자인")));

        MatchedJobListResult result = jobFacade.getMatchedJobs(USERNAME);
        MatchedJobListResponse response = MatchedJobListResponse.from(result);

        assertThat(response.getJobs()).extracting(job -> job.getJobId()).containsExactly(42L, 43L, 44L);
        assertThat(response.getJobs().get(0).getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(response.getJobs().get(0).getStudentProfileId()).isEqualTo(7L);
        assertThat(response.getJobs().get(0).getStudentNumber()).isEqualTo("2023123456");
        assertThat(response.getJobs().get(0).getMajor()).isEqualTo("컴퓨터정보공학부");
        assertThat(response.getJobs().get(0).getSubmissionType()).isEqualTo("DRAFT");
        assertThat(response.getJobs().get(0).getPendingSubmissionId()).isEqualTo(81L);
        assertThat(response.getJobs().get(0).getSpecialtyCategories().get(0).getSpecialties())
                .extracting(specialty -> specialty.getName()).containsExactly("백엔드", "프론트엔드");
        assertThat(response.getJobs().get(1).getSubmissionType()).isEqualTo("REVISION");
        assertThat(response.getJobs().get(1).getPendingSubmissionId()).isEqualTo(87L);
        assertThat(response.getJobs().get(2).getSubmissionType()).isNull();
        assertThat(response.getJobs().get(2).getPendingSubmissionId()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(ApiResponse.success(response)))
                .contains("\"success\":true", "\"jobs\":[", "\"studentProfileId\":7",
                        "\"submissionType\":null", "\"pendingSubmissionId\":null",
                        "\"draftDeadline\":\"2026-10-10\"");

        ArgumentCaptor<GetMatchedJobsCommand> command = ArgumentCaptor.forClass(GetMatchedJobsCommand.class);
        verify(jobService).getMatchedJobs(command.capture());
        assertThat(command.getValue().getOwnerProfileId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("MATCHED 의뢰가 없으면 다른 데이터를 조회하지 않고 빈 목록을 반환한다")
    void returnsEmptyListWithoutRelatedLookups() {
        givenOwner();
        when(jobService.getMatchedJobs(any(GetMatchedJobsCommand.class))).thenReturn(List.of());

        assertThat(jobFacade.getMatchedJobs(USERNAME).getJobs()).isEmpty();
        verifyNoInteractions(studentService, specialtyCategoryService);
    }

    @Test
    @DisplayName("사업주 프로필이 없으면 MATCHED 의뢰를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).build());

        assertThatThrownBy(() -> jobFacade.getMatchedJobs(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OWNER_PROFILE_NOT_FOUND));
        verifyNoInteractions(jobService, studentService, specialtyCategoryService);
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).build());
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private Job job(Long id, Long studentId) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .selectedStudentProfileId(studentId)
                .build();
    }

    private Student student(Long id, String number, String major) {
        return Student.builder().id(id).studentNumber(number).major(major).build();
    }

    private JobSubmission submission(Long id, JobSubmissionType type) {
        return JobSubmission.builder().id(id).submissionType(type).build();
    }
}
