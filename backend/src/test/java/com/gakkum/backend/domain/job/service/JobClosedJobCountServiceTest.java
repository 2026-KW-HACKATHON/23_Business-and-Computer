package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;

class JobClosedJobCountServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, mock(JobSpecialtyRepository.class), mock(JobApplicationRepository.class),
            mock(JobSubmissionRepository.class), Clock.systemUTC());

    @Test
    @DisplayName("학생이 담당한 CLOSED 의뢰 수를 CLOSED 조건으로 조회한다")
    void countsClosedJobs() {
        when(jobRepository.countBySelectedStudentProfileIdAndStatus(7L, JobStatus.CLOSED)).thenReturn(3L);

        assertThat(jobService.countClosedJobs(7L)).isEqualTo(3L);
        verify(jobRepository).countBySelectedStudentProfileIdAndStatus(7L, JobStatus.CLOSED);
    }

    @Test
    @DisplayName("실적이 없으면 0이다")
    void returnsZeroWithoutClosedJobs() {
        assertThat(jobService.countClosedJobs(7L)).isZero();
    }
}
