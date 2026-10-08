package com.gakkum.backend.application.job;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
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
import com.gakkum.backend.domain.student.repository.StudentRepository;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;

@DisplayName("학생 진행중 의뢰 목록 전체 흐름 (GET /me/jobs?status=MATCHED)")
class JobStudentMatchedListFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
                mock(JobApplicationRepository.class), jobSubmissionRepository,
                Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                specialtyCategoryService, mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class),
                new ImmediateTransactionTemplate(), mock(ChatRoomService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("학생에게 본인과 매칭된 MATCHED 의뢰를 매장 이름, 최신 제출물 유형·검토 상태·제출 시각과 함께 반환한다")
    void returnsStudentMatchedJobs() throws Exception {
        givenStudent();
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(43L, 5L), job(42L, 5L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(43L, 42L)))
                .thenReturn(List.of(JobSpecialty.create(42L, 12L)));
        when(jobSubmissionRepository.findByJobIdIn(List.of(43L, 42L))).thenReturn(List.of(
                submission(42L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 10, 9, 14, 5, 30))));
        when(ownerRepository.findAllById(Set.of(5L))).thenReturn(List.of(owner(5L, "가꿈 카페")));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(12L)))
                .thenReturn(Map.of(12L, SpecialtyDetail.of(12L, "프론트엔드", 1L, "개발")));

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs.length()").value(2))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(43))
                .andExpect(jsonPath("$.data.jobs[0].submissionType").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].reviewStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].submittedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.jobs[1].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[1].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.jobs[1].submittedAt").value("2026-10-09T23:05:30+09:00"))
                .andExpect(jsonPath("$.data.jobs[1].title").value("의뢰 42"))
                .andExpect(jsonPath("$.data.jobs[1].budget").value(300000))
                .andExpect(jsonPath("$.data.jobs[1].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[1].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[1].revisionCount").value(2))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories[0].specialties[0].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.jobs[1].submissionType").value("DRAFT"))
                .andExpect(jsonPath("$.data.jobs[1].reviewStatus").value("REVISION_REQUESTED"))
                .andExpect(jsonPath("$.data.jobs[1].studentProfileId").doesNotExist())
                .andExpect(jsonPath("$.data.jobs[1].pendingSubmissionId").doesNotExist());
        verify(ownerRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("제출물이 여러 개면 수정 번호가 가장 큰 최신 제출물의 유형·검토 상태·제출 시각을 반환한다")
    void returnsLatestSubmissionAmongMany() throws Exception {
        givenStudent();
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(42L, 5L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
        // 조회 결과 순서는 제출 순서와 다르다
        when(jobSubmissionRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of(
                submission(42L, 1, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 10, 11, 10, 0)),
                submission(42L, 2, JobSubmissionReviewStatus.PENDING, LocalDateTime.of(2026, 10, 13, 18, 45, 10)),
                submission(42L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED,
                        LocalDateTime.of(2026, 10, 9, 14, 5, 30))));
        when(ownerRepository.findAllById(Set.of(5L))).thenReturn(List.of(owner(5L, "가꿈 카페")));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of())).thenReturn(Map.of());

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].submissionType").value("REVISION"))
                .andExpect(jsonPath("$.data.jobs[0].reviewStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.jobs[0].submittedAt").value("2026-10-14T03:45:10+09:00"));
    }

    @Test
    @DisplayName("의뢰 수가 늘어도 매장 이름·사진과 제출물은 사장님 중복 없이 한 번씩만 조회하고, 사진이 없는 매장은 사진을 null로 내린다")
    void readsStoreNamesOnce() throws Exception {
        givenStudent();
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(44L, 5L), job(43L, 6L), job(42L, 5L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of());
        when(jobSubmissionRepository.findByJobIdIn(List.of(44L, 43L, 42L))).thenReturn(List.of());
        when(ownerRepository.findAllById(Set.of(5L, 6L))).thenReturn(List.of(
                owner(6L, "동네 빵집", "https://cdn.example.com/owners/6/profile.png"), owner(5L, "가꿈 카페")));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of())).thenReturn(Map.of());

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.jobs[0].storeProfileImageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[1].storeName").value("동네 빵집"))
                .andExpect(jsonPath("$.data.jobs[1].storeProfileImageUrl")
                        .value("https://cdn.example.com/owners/6/profile.png"))
                .andExpect(jsonPath("$.data.jobs[2].storeName").value("가꿈 카페"));
        verify(ownerRepository, times(1)).findAllById(any());
        verify(jobSubmissionRepository, times(1)).findByJobIdIn(any());
    }

    @Test
    @DisplayName("의뢰한 사장님 프로필이 없으면 500 COMMON_500으로 거부한다")
    void rejectsMissingOwnerProfile() throws Exception {
        givenStudent();
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(42L, 5L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
        when(jobSubmissionRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
        when(ownerRepository.findAllById(Set.of(5L))).thenReturn(List.of());
        when(specialtyCategoryService.getSpecialtyDetails(Set.of())).thenReturn(Map.of());

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("매칭된 진행 중 의뢰가 없는 학생에게 jobs 빈 배열을 반환하고 매장을 조회하지 않는다")
    void returnsEmptyJobsForStudent() throws Exception {
        givenStudent();
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verifyNoInteractions(ownerRepository);
    }

    @Test
    @DisplayName("사업주는 기존 사업주용 MATCHED 목록 조회를 그대로 사용한다")
    void keepsOwnerMatchedList() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.OWNER).build()));
        when(ownerRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(5L).build()));
        when(jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(5L, JobStatus.MATCHED))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/jobs").param("status", "MATCHED").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verify(jobRepository, never()).findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(any(), any());
    }

    private void givenStudent() {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
    }

    private Job job(Long id, Long ownerProfileId) {
        return Job.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("의뢰 " + id)
                .budget(300000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(JobStatus.MATCHED)
                .selectedStudentProfileId(7L)
                .build();
    }

    private Owner owner(Long id, String storeName) {
        return owner(id, storeName, null);
    }

    private Owner owner(Long id, String storeName, String profileImageUrl) {
        return Owner.builder().id(id).storeName(storeName).profileImageUrl(profileImageUrl).build();
    }

    private JobSubmission submission(
            Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus, LocalDateTime createdAt) {
        return JobSubmission.builder()
                .jobId(jobId)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .reviewStatus(reviewStatus)
                .createdAt(createdAt)
                .build();
    }
}
