package com.gakkum.backend.application.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.job.controller.JobController;
import com.gakkum.backend.application.job.facade.JobFacade;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.service.MediaService;
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

@DisplayName("학생 '내가 지원한 의뢰' 목록 전체 흐름 (GET /me/job-applications)")
class JobStudentAppliedListFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2DEMO1";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository, mock(JwtService.class));
        JobService jobService = new JobService(jobRepository, jobSpecialtyRepository,
                jobApplicationRepository, mock(JobSubmissionRepository.class),
                Clock.systemUTC());
        JobFacade facade = new JobFacade(userService, new OwnerService(ownerRepository), jobService,
                specialtyCategoryService, mock(SpecialtyService.class), new StudentService(studentRepository),
                mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("학생에게 본인이 지원한 모집 중·선정 대기 의뢰를 지원 최신순으로 카드 필드와 함께 반환한다")
    void returnsAppliedJobsInApplicationOrder() throws Exception {
        givenStudent(null);
        // 의뢰 ID 순서와 반대인 지원 최신순
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(
                        application(203L, 42L, LocalDateTime.of(2026, 10, 5, 9, 30)),
                        application(202L, 44L, LocalDateTime.of(2026, 10, 4, 18, 0)),
                        application(201L, 43L, LocalDateTime.of(2026, 10, 3, 12, 0))));
        // 44번은 모집 중이 아니거나 다른 격리 범위라 조회되지 않는다
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(42L, 44L, 43L), JobStatus.OPEN, null))
                .thenReturn(List.of(job(43L), job(42L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L, 43L))).thenReturn(List.of(
                JobSpecialty.create(42L, 21L),
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(42L, 11L)));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(11L, 12L, 21L))).thenReturn(Map.of(
                11L, SpecialtyDetail.of(11L, "백엔드", 1L, "개발"),
                12L, SpecialtyDetail.of(12L, "프론트엔드", 1L, "개발"),
                21L, SpecialtyDetail.of(21L, "로고", 2L, "디자인")));

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs.length()").value(2))
                .andExpect(jsonPath("$.data.jobs[0].length()").value(10))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].jobApplicationId").value(203))
                .andExpect(jsonPath("$.data.jobs[0].title").value("의뢰 42"))
                .andExpect(jsonPath("$.data.jobs[0].budget").value(300000))
                .andExpect(jsonPath("$.data.jobs[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[0].jobStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.jobs[0].applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.jobs[0].appliedAt").value("2026-10-05T09:30:00"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories.length()").value(2))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].id").value(1))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].name").value("개발"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].id").value(11))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[0].name").value("백엔드"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[0].specialties[1].name").value("프론트엔드"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[1].name").value("디자인"))
                .andExpect(jsonPath("$.data.jobs[0].specialtyCategories[1].specialties[0].name").value("로고"))
                .andExpect(jsonPath("$.data.jobs[1].jobId").value(43))
                .andExpect(jsonPath("$.data.jobs[1].jobApplicationId").value(201))
                .andExpect(jsonPath("$.data.jobs[1].appliedAt").value("2026-10-03T12:00:00"))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories").isEmpty());
        verify(ownerRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("데모 학생은 본인 데모 세션의 의뢰만 조회한다")
    void readsJobsInViewerDemoSession() throws Exception {
        givenStudent(DEMO_SESSION_ID);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of(application(203L, 42L, LocalDateTime.of(2026, 10, 5, 9, 30))));
        when(jobRepository.findByIdInAndStatusAndDemoSessionId(List.of(42L), JobStatus.OPEN, DEMO_SESSION_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verify(jobRepository).findByIdInAndStatusAndDemoSessionId(List.of(42L), JobStatus.OPEN, DEMO_SESSION_ID);
        verifyNoInteractions(jobSpecialtyRepository, specialtyCategoryService);
    }

    @Test
    @DisplayName("선정 대기 지원서가 없는 학생에게 jobs 빈 배열을 반환하고 의뢰·전문분야를 조회하지 않는다")
    void returnsEmptyJobs() throws Exception {
        givenStudent(null);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                7L, JobApplicationStatus.PENDING))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verifyNoInteractions(jobRepository, jobSpecialtyRepository, specialtyCategoryService);
    }

    @Test
    @DisplayName("학생이 아닌 사용자는 403 JOB_APPLICATION_403_LIST_STUDENT로 거부하고 지원서를 조회하지 않는다")
    void rejectsNonStudent() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.OWNER).build()));

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JOB_APPLICATION_403_LIST_STUDENT"));
        verifyNoInteractions(studentRepository, jobApplicationRepository, jobRepository);
    }

    @Test
    @DisplayName("학생 계정에 학생 프로필이 없으면 500 COMMON_500으로 거부한다")
    void rejectsStudentWithoutProfile() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
        verifyNoInteractions(jobApplicationRepository, jobRepository);
    }

    private void givenStudent(String demoSessionId) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(STUDENT_USER_ID).role(UserRole.STUDENT).demoSessionId(demoSessionId).build()));
        when(studentRepository.findByUserId(STUDENT_USER_ID)).thenReturn(Optional.of(
                Student.builder().id(7L).userId(STUDENT_USER_ID).build()));
    }

    private Job job(Long id) {
        return Job.builder()
                .id(id)
                .title("의뢰 " + id)
                .budget(300000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(JobStatus.OPEN)
                .build();
    }

    private JobApplication application(Long id, Long jobId, LocalDateTime createdAt) {
        return JobApplication.builder()
                .id(id)
                .jobId(jobId)
                .studentProfileId(7L)
                .status(JobApplicationStatus.PENDING)
                .createdAt(createdAt)
                .build();
    }
}
