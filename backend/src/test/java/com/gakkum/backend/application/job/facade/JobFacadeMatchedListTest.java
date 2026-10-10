package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.context.ApplicationEventPublisher;

import com.gakkum.backend.application.job.dto.JobListResponse;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
import com.gakkum.backend.global.response.ApiResponse;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

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
            userService, ownerService, jobService, specialtyCategoryService,
            mock(SpecialtyService.class), studentService,
            mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class),
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));

    @Test
    @DisplayName("사업주의 MATCHED 의뢰를 학생 정보와 특기, 제출물 정보로 조립한다")
    void assemblesMatchedJobs() {
        givenOwner();
        when(jobService.getMatchedJobs(any(GetMatchedJobsCommand.class))).thenReturn(List.of(
                MatchedJobData.of(job(42L, 7L), List.of(12L, 11L),
                        submission(81L, JobSubmissionType.DRAFT, 0, LocalDateTime.of(2026, 10, 9, 14, 5, 30)),
                        JobProgressStage.DRAFT),
                MatchedJobData.of(job(43L, 8L), List.of(21L),
                        submission(87L, JobSubmissionType.REVISION, 2, LocalDateTime.of(2026, 10, 12, 9, 0)),
                        JobProgressStage.REVISION),
                MatchedJobData.of(job(44L, 9L), List.of(22L), null, JobProgressStage.STARTED),
                // 같은 학생이 맡은 두 번째 의뢰. 학생·사용자는 중복 없이 조회한다
                MatchedJobData.of(job(45L, 7L), List.of(), null, JobProgressStage.STARTED)));
        when(studentService.getStudentProfilesByIds(List.of(7L, 8L, 9L))).thenReturn(Map.of(
                7L, student(7L, "2023123456", "컴퓨터정보공학부"),
                8L, student(8L, "2024123456", "시각디자인학부"),
                9L, student(9L, "2022123456", "미디어학부")));
        // 학생 맵의 순회 순서에 의존하지 않도록 중복 없는 사용자 ID 집합으로만 맞춘다
        when(userService.getUsersByIds(argThat(ids -> ids.size() == 3
                && Set.copyOf(ids).equals(Set.of("user-7", "user-8", "user-9"))))).thenReturn(Map.of(
                "user-7", User.builder().id("user-7").name("홍길동").build(),
                "user-8", User.builder().id("user-8").name("김철수").build(),
                "user-9", User.builder().id("user-9").name("이영희").build()));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(11L, 12L, 21L, 22L)))
                .thenReturn(Map.of(
                        11L, SpecialtyDetail.of(11L, "백엔드", 1L, "개발"),
                        12L, SpecialtyDetail.of(12L, "프론트엔드", 1L, "개발"),
                        21L, SpecialtyDetail.of(21L, "그래픽 디자인", 2L, "디자인"),
                        22L, SpecialtyDetail.of(22L, "콘텐츠 디자인", 2L, "디자인")));

        MatchedJobListResult result = jobFacade.getMatchedJobs(USERNAME);
        JobListResponse.MatchedJobList response = JobListResponse.MatchedJobList.from(result);

        assertThat(response.getJobs()).extracting(job -> job.getJobId()).containsExactly(42L, 43L, 44L, 45L);
        assertThat(response.getJobs()).extracting(job -> job.getStudentName())
                .containsExactly("홍길동", "김철수", "이영희", "홍길동");
        assertThat(response.getJobs()).extracting(job -> job.getBudget()).containsOnly(300000L);
        assertThat(response.getJobs()).extracting(job -> job.getRevisionCount()).containsOnly(2);
        // 검토 대기 제출물의 수정 번호와 제출 시각. 초안은 0번이고 검토 대기가 없으면 둘 다 null이다
        assertThat(response.getJobs()).extracting(job -> job.getRevisionNumber()).containsExactly(0, 2, null, null);
        assertThat(response.getJobs()).extracting(job -> job.getSubmittedAt()).containsExactly(
                OffsetDateTime.parse("2026-10-09T23:05:30+09:00"), OffsetDateTime.parse("2026-10-12T18:00:00+09:00"),
                null, null);
        assertThat(response.getJobs().get(0).getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(response.getJobs().get(0).getStudentProfileId()).isEqualTo(7L);
        assertThat(response.getJobs().get(0).getStudentNumber()).isEqualTo("23");
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
                        "\"draftDeadline\":\"2026-10-10\"", "\"studentName\":\"홍길동\"",
                        "\"budget\":300000", "\"revisionNumber\":null", "\"submittedAt\":null");

        ArgumentCaptor<GetMatchedJobsCommand> command = ArgumentCaptor.forClass(GetMatchedJobsCommand.class);
        verify(jobService).getMatchedJobs(command.capture());
        assertThat(command.getValue().getOwnerProfileId()).isEqualTo(5L);
        verify(studentService, times(1)).getStudentProfilesByIds(any());
        verify(userService, times(1)).getUsersByIds(any());
        verify(specialtyCategoryService, times(1)).getSpecialtyDetails(any());
    }

    @Test
    @DisplayName("선정 학생의 사용자 일괄 조회에서 난 참조 누락 오류는 그대로 전달한다")
    void propagatesMissingStudentUser() {
        givenOwner();
        when(jobService.getMatchedJobs(any(GetMatchedJobsCommand.class))).thenReturn(List.of(
                MatchedJobData.of(job(42L, 7L), List.of(), null, JobProgressStage.STARTED)));
        when(studentService.getStudentProfilesByIds(List.of(7L)))
                .thenReturn(Map.of(7L, student(7L, "2023123456", "컴퓨터정보공학부")));
        when(userService.getUsersByIds(List.of("user-7")))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> jobFacade.getMatchedJobs(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        verifyNoInteractions(specialtyCategoryService);
    }

    @Test
    @DisplayName("MATCHED 의뢰가 없으면 다른 데이터를 조회하지 않고 빈 목록을 반환한다")
    void returnsEmptyListWithoutRelatedLookups() {
        givenOwner();
        when(jobService.getMatchedJobs(any(GetMatchedJobsCommand.class))).thenReturn(List.of());

        assertThat(jobFacade.getMatchedJobs(USERNAME).getJobs()).isEmpty();
        verifyNoInteractions(studentService, specialtyCategoryService);
        verify(userService, never()).getUsersByIds(any());
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
                .budget(300000L)
                .revisionCount(2)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .selectedStudentProfileId(studentId)
                .build();
    }

    private Student student(Long id, String number, String major) {
        return Student.builder().id(id).userId("user-" + id).studentNumber(number).major(major).build();
    }

    private JobSubmission submission(Long id, JobSubmissionType type, int revisionNumber, LocalDateTime createdAt) {
        return JobSubmission.builder()
                .id(id).submissionType(type).revisionNumber(revisionNumber).createdAt(createdAt).build();
    }
}
