package com.gakkum.backend.domain.job.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobApplicationsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationListData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.specialty.entity.StudentSpecialty;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;

/** 저장한 행만 직접 정리한다. 학생·특기 ID는 실제 데이터와 겹치지 않는 값을 쓴다. */
@SpringBootTest
@ActiveProfiles("local")
class JobApplicationListQueryIntegrationTest {

    private static final long OWNER_PROFILE_ID = 986_005L;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private StudentSpecialtyRepository studentSpecialtyRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> jobIds = new ArrayList<>();
    private final List<Long> applicationIds = new ArrayList<>();
    private final List<Long> studentSpecialtyIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jobApplicationRepository.deleteAllById(applicationIds);
            studentSpecialtyRepository.deleteAllById(studentSpecialtyIds);
            jobRepository.deleteAllById(jobIds);
        });
    }

    @Test
    @DisplayName("PostgreSQL에서 해당 의뢰의 PENDING 지원서만 지원 시각과 함께 조회한다")
    void findsOnlyPendingApplicationsOfJob() {
        Job job = saveOpenJob();
        Job otherJob = saveOpenJob();
        JobApplication pending = saveApplication(job.getId(), 986_101L, JobApplicationStatus.PENDING);
        JobApplication otherPending = saveApplication(job.getId(), 986_102L, JobApplicationStatus.PENDING);
        saveApplication(job.getId(), 986_103L, JobApplicationStatus.ACCEPTED);
        saveApplication(job.getId(), 986_104L, JobApplicationStatus.REJECTED);
        saveApplication(otherJob.getId(), 986_101L, JobApplicationStatus.PENDING);

        JobApplicationListData data = jobService.getJobApplications(
                GetJobApplicationsCommand.of(job.getId(), OWNER_PROFILE_ID));

        assertThat(data.getJob().getId()).isEqualTo(job.getId());
        assertThat(data.getSpecialtyIds()).isEmpty();
        assertThat(data.getApplications())
                .extracting(JobApplication::getId)
                .containsExactlyInAnyOrder(pending.getId(), otherPending.getId());
        assertThat(data.getApplications()).allSatisfy(application ->
                assertThat(application.getCreatedAt()).isNotNull());
        assertThat(data.getApplications())
                .extracting(JobApplication::getSummary, JobApplication::getWorkPlan,
                        JobApplication::getDeliveryMethod)
                .containsOnly(tuple("한 줄 요약", "작업계획서", "결과물 전달 방법"));
    }

    @Test
    @DisplayName("PostgreSQL에서 요청한 학생이 등록한 특기만 한 번에 조회한다")
    void findsSpecialtiesOfRequestedStudents() {
        saveStudentSpecialty(986_201L, 986_901L);
        saveStudentSpecialty(986_201L, 986_902L);
        saveStudentSpecialty(986_202L, 986_901L);
        saveStudentSpecialty(986_204L, 986_903L);

        assertThat(studentSpecialtyRepository.findByStudentProfileIdIn(List.of(986_201L, 986_202L, 986_203L)))
                .extracting(StudentSpecialty::getStudentProfileId, StudentSpecialty::getSpecialtyId)
                .containsExactlyInAnyOrder(
                        tuple(986_201L, 986_901L),
                        tuple(986_201L, 986_902L),
                        tuple(986_202L, 986_901L));
    }

    private Job saveOpenJob() {
        Job saved = jobRepository.saveAndFlush(Job.create(OWNER_PROFILE_ID, "지원자 목록 테스트 의뢰", "설명", 50000L,
                LocalDate.now(), LocalDate.now().plusDays(3), 2));
        jobIds.add(saved.getId());
        return saved;
    }

    private JobApplication saveApplication(Long jobId, Long studentProfileId, JobApplicationStatus status) {
        JobApplication saved = jobApplicationRepository.saveAndFlush(JobApplication.builder()
                .jobId(jobId)
                .studentProfileId(studentProfileId)
                .status(status)
                .summary("한 줄 요약")
                .workPlan("작업계획서")
                .deliveryMethod("결과물 전달 방법")
                .build());
        applicationIds.add(saved.getId());
        return saved;
    }

    private void saveStudentSpecialty(Long studentProfileId, Long specialtyId) {
        StudentSpecialty saved = studentSpecialtyRepository.saveAndFlush(
                StudentSpecialty.create(studentProfileId, specialtyId));
        studentSpecialtyIds.add(saved.getId());
    }
}
