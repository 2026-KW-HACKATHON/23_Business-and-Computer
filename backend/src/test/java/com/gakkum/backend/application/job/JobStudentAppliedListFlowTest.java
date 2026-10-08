package com.gakkum.backend.application.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

@DisplayName("학생 '내가 지원한 의뢰' 목록 전체 흐름 (GET /me/job-applications)")
class JobStudentAppliedListFlowTest {

    private static final String USERNAME = "KAKAO_67890";
    private static final String STUDENT_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB6D";
    private static final String DEMO_SESSION_ID = "01K58M6PJV8VAJMXHBHJ2DEMO1";
    private static final List<JobApplicationStatus> LISTED_STATUSES =
            List.of(JobApplicationStatus.PENDING, JobApplicationStatus.REJECTED);

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
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("학생에게 본인이 지원한 의뢰를 지원 최신순으로 카드 필드, 매장 이름, 본인 지원서 원문과 함께 반환한다")
    void returnsAppliedJobsInApplicationOrder() throws Exception {
        givenStudent(null);
        // 의뢰 ID 순서와 반대인 지원 최신순
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                7L, LISTED_STATUSES))
                .thenReturn(List.of(
                        application(203L, 42L, LocalDateTime.of(2026, 10, 5, 9, 30)),
                        application(202L, 44L, LocalDateTime.of(2026, 10, 4, 18, 0)),
                        application(201L, 43L, LocalDateTime.of(2026, 10, 3, 12, 0))));
        // 44번은 다른 격리 범위이거나 없는 의뢰라 조회되지 않는다
        when(jobRepository.findByIdInAndDemoSessionId(List.of(42L, 44L, 43L), null))
                .thenReturn(List.of(job(43L, 6L), job(42L, 5L)));
        when(ownerRepository.findAllById(Set.of(5L, 6L)))
                .thenReturn(List.of(owner(5L, "가꿈 카페"), owner(6L, "동네 빵집")));
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
                .andExpect(jsonPath("$.data.jobs[0].length()").value(14))
                .andExpect(jsonPath("$.data.jobs[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.jobs[0].summary").value("요약 203"))
                .andExpect(jsonPath("$.data.jobs[0].workPlan").value("작업계획서 203"))
                .andExpect(jsonPath("$.data.jobs[0].deliveryMethod").value("전달 방법 203"))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value(42))
                .andExpect(jsonPath("$.data.jobs[0].jobApplicationId").value(203))
                .andExpect(jsonPath("$.data.jobs[0].title").value("의뢰 42"))
                .andExpect(jsonPath("$.data.jobs[0].budget").value(300000))
                .andExpect(jsonPath("$.data.jobs[0].draftDeadline").value("2026-10-10"))
                .andExpect(jsonPath("$.data.jobs[0].finalDeadline").value("2026-10-20"))
                .andExpect(jsonPath("$.data.jobs[0].jobStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.jobs[0].applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.jobs[0].appliedAt").value("2026-10-05T18:30:00+09:00"))
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
                .andExpect(jsonPath("$.data.jobs[1].storeName").value("동네 빵집"))
                .andExpect(jsonPath("$.data.jobs[1].summary").value("요약 201"))
                .andExpect(jsonPath("$.data.jobs[1].appliedAt").value("2026-10-03T21:00:00+09:00"))
                .andExpect(jsonPath("$.data.jobs[1].specialtyCategories").isEmpty());
        verify(ownerRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("미선정 지원은 REJECTED로, 모집 중 대기 지원은 PENDING으로 반환하고 선정된 지원·선정 전 취소된 의뢰의 지원은 뺀다")
    void returnsUnselectedHistoryAndExcludesSelected() throws Exception {
        givenStudent(null);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                7L, LISTED_STATUSES))
                .thenReturn(List.of(
                        application(207L, 47L, JobApplicationStatus.PENDING),
                        application(206L, 46L, JobApplicationStatus.PENDING),
                        application(205L, 45L, JobApplicationStatus.REJECTED),
                        application(204L, 44L, JobApplicationStatus.PENDING),
                        application(203L, 43L, JobApplicationStatus.PENDING),
                        application(202L, 42L, JobApplicationStatus.PENDING),
                        application(201L, 41L, JobApplicationStatus.PENDING)));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(47L, 46L, 45L, 44L, 43L, 42L, 41L), null))
                .thenReturn(List.of(
                        job(47L, 5L, JobStatus.OPEN, null),
                        // 다른 학생이 선정돼 진행 중·완료·취소된 의뢰. 지원서는 DB에 PENDING으로 남아 있다
                        job(46L, 5L, JobStatus.MATCHED, 9L),
                        job(45L, 5L, JobStatus.CANCELLED, null),
                        job(44L, 5L, JobStatus.CLOSED, 9L),
                        job(43L, 5L, JobStatus.CANCELLED, 9L),
                        // 학생 선정 없이 취소된 의뢰
                        job(42L, 5L, JobStatus.CANCELLED, null),
                        // 본인이 선정된 의뢰
                        job(41L, 5L, JobStatus.MATCHED, 7L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(47L, 46L, 45L, 44L, 43L))).thenReturn(List.of());
        when(ownerRepository.findAllById(Set.of(5L))).thenReturn(List.of(owner(5L, "가꿈 카페")));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of())).thenReturn(Map.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs.length()").value(5))
                .andExpect(jsonPath("$.data.jobs[0].jobApplicationId").value(207))
                .andExpect(jsonPath("$.data.jobs[0].jobStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.jobs[0].applicationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.jobs[1].jobApplicationId").value(206))
                .andExpect(jsonPath("$.data.jobs[1].jobStatus").value("MATCHED"))
                .andExpect(jsonPath("$.data.jobs[1].applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.jobs[2].jobApplicationId").value(205))
                .andExpect(jsonPath("$.data.jobs[2].jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.jobs[2].applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.jobs[3].jobApplicationId").value(204))
                .andExpect(jsonPath("$.data.jobs[3].jobStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.jobs[3].applicationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.jobs[4].jobApplicationId").value(203))
                .andExpect(jsonPath("$.data.jobs[4].jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.jobs[4].applicationStatus").value("REJECTED"));
        // 항목 수와 무관하게 지원서·의뢰·전문분야·매장을 한 번씩만 조회한다
        verify(jobApplicationRepository, times(1))
                .findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(any(), any());
        verify(jobRepository, times(1)).findByIdInAndDemoSessionId(any(), any());
        verify(jobSpecialtyRepository, times(1)).findByJobIdIn(any());
        verify(ownerRepository, times(1)).findAllById(any());
        verifyNoMoreInteractions(jobApplicationRepository, jobRepository, ownerRepository);
    }

    @Test
    @DisplayName("의뢰한 사장님 프로필이 없으면 500 COMMON_500으로 거부한다")
    void rejectsMissingOwnerProfile() throws Exception {
        givenStudent(null);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                7L, LISTED_STATUSES))
                .thenReturn(List.of(application(203L, 42L, LocalDateTime.of(2026, 10, 5, 9, 30))));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(42L), null)).thenReturn(List.of(job(42L, 5L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(42L))).thenReturn(List.of());
        when(specialtyCategoryService.getSpecialtyDetails(Set.of())).thenReturn(Map.of());
        when(ownerRepository.findAllById(Set.of(5L))).thenReturn(List.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    @Test
    @DisplayName("데모 학생은 본인 데모 세션의 의뢰만 조회한다")
    void readsJobsInViewerDemoSession() throws Exception {
        givenStudent(DEMO_SESSION_ID);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                7L, LISTED_STATUSES))
                .thenReturn(List.of(application(203L, 42L, LocalDateTime.of(2026, 10, 5, 9, 30))));
        when(jobRepository.findByIdInAndDemoSessionId(List.of(42L), DEMO_SESSION_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verify(jobRepository).findByIdInAndDemoSessionId(List.of(42L), DEMO_SESSION_ID);
        verifyNoInteractions(jobSpecialtyRepository, specialtyCategoryService, ownerRepository);
    }

    @Test
    @DisplayName("대기·미선정 지원서가 없는 학생에게 jobs 빈 배열을 반환하고 의뢰·전문분야·매장을 조회하지 않는다")
    void returnsEmptyJobs() throws Exception {
        givenStudent(null);
        when(jobApplicationRepository.findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                7L, LISTED_STATUSES))
                .thenReturn(List.of());

        mockMvc.perform(get("/me/job-applications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs").isArray())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
        verifyNoInteractions(jobRepository, jobSpecialtyRepository, specialtyCategoryService, ownerRepository);
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

    private Job job(Long id, Long ownerProfileId) {
        return job(id, ownerProfileId, JobStatus.OPEN, null);
    }

    private Job job(Long id, Long ownerProfileId, JobStatus status, Long selectedStudentProfileId) {
        return Job.builder()
                .id(id)
                .ownerProfileId(ownerProfileId)
                .title("의뢰 " + id)
                .budget(300000L)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .status(status)
                .selectedStudentProfileId(selectedStudentProfileId)
                .build();
    }

    private Owner owner(Long id, String storeName) {
        return Owner.builder().id(id).storeName(storeName).build();
    }

    private JobApplication application(Long id, Long jobId, LocalDateTime createdAt) {
        return application(id, jobId, JobApplicationStatus.PENDING, createdAt);
    }

    private JobApplication application(Long id, Long jobId, JobApplicationStatus status) {
        return application(id, jobId, status, LocalDateTime.of(2026, 10, 5, 9, 30));
    }

    private JobApplication application(Long id, Long jobId, JobApplicationStatus status, LocalDateTime createdAt) {
        return JobApplication.builder()
                .id(id)
                .jobId(jobId)
                .studentProfileId(7L)
                .status(status)
                .summary("요약 " + id)
                .workPlan("작업계획서 " + id)
                .deliveryMethod("전달 방법 " + id)
                .createdAt(createdAt)
                .build();
    }
}
