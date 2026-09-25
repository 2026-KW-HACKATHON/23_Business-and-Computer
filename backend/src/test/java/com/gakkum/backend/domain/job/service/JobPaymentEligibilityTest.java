package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobPaymentEligibilityTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobApplicationRepository applicationRepository = mock(JobApplicationRepository.class);
    private final JobService service = new JobService(jobRepository, mock(JobSpecialtyRepository.class),
            applicationRepository, mock(JobSubmissionRepository.class), mock(SpecialtyService.class));

    @Test
    @DisplayName("소유한 OPEN 의뢰와 해당 의뢰의 PENDING 지원서만 결제할 수 있다")
    void acceptsEligibleJobAndApplication() {
        Job job = job(7L, JobStatus.OPEN);
        JobApplication application = application(11L, JobApplicationStatus.PENDING);
        when(jobRepository.findByIdAndOwnerProfileId(11L, 7L)).thenReturn(Optional.of(job));
        when(applicationRepository.findById(21L)).thenReturn(Optional.of(application));

        assertThat(service.getPayableJobForUpdate(11L, 7L)).isSameAs(job);
        assertThat(service.getPayableApplication(11L, 21L)).isSameAs(application);
    }

    @Test
    @DisplayName("타인 의뢰는 없는 의뢰로 처리한다")
    void rejectsOtherOwnersJob() {
        when(jobRepository.findByIdAndOwnerProfileId(11L, 7L)).thenReturn(Optional.empty());

        assertError(() -> service.getPayableJobForUpdate(11L, 7L), ErrorCode.JOB_NOT_FOUND);
    }

    @Test
    @DisplayName("OPEN 상태가 아닌 의뢰는 결제할 수 없다")
    void rejectsNonOpenJob() {
        when(jobRepository.findByIdAndOwnerProfileId(11L, 7L)).thenReturn(Optional.of(job(7L, JobStatus.MATCHED)));

        assertError(() -> service.getPayableJobForUpdate(11L, 7L), ErrorCode.PAYMENT_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("다른 의뢰의 지원서는 결제할 수 없다")
    void rejectsApplicationFromOtherJob() {
        when(applicationRepository.findById(21L)).thenReturn(Optional.of(application(12L, JobApplicationStatus.PENDING)));

        assertError(() -> service.getPayableApplication(11L, 21L), ErrorCode.JOB_APPLICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 지원서는 결제할 수 없다")
    void rejectsNonPendingApplication() {
        when(applicationRepository.findById(21L)).thenReturn(Optional.of(application(11L, JobApplicationStatus.ACCEPTED)));

        assertError(() -> service.getPayableApplication(11L, 21L), ErrorCode.PAYMENT_NOT_AVAILABLE);
    }

    private Job job(Long ownerProfileId, JobStatus status) {
        return Job.builder().id(11L).ownerProfileId(ownerProfileId).status(status).build();
    }

    private JobApplication application(Long jobId, JobApplicationStatus status) {
        return JobApplication.builder().id(21L).jobId(jobId).status(status).build();
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
