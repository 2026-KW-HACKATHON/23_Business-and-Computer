package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.StudentJobCount;
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

    @Test
    @DisplayName("학생별 CLOSED 의뢰 수를 한 번에 조회하고 완료 의뢰가 없는 학생은 0으로 채운다")
    void countsClosedJobsForAllRequestedStudents() {
        StudentJobCount row = mock(StudentJobCount.class);
        when(row.getStudentProfileId()).thenReturn(7L);
        when(row.getJobCount()).thenReturn(3L);
        when(jobRepository.countByStudentProfileIdsAndStatus(List.of(7L, 8L), JobStatus.CLOSED))
                .thenReturn(List.of(row));

        assertThat(jobService.countClosedJobsByStudentProfileIds(List.of(7L, 8L)))
                .containsOnly(Map.entry(7L, 3L), Map.entry(8L, 0L));
    }

    @Test
    @DisplayName("대상 학생이 없으면 완료 의뢰 수를 조회하지 않는다")
    void skipsClosedJobCountQueryWithoutStudents() {
        assertThat(jobService.countClosedJobsByStudentProfileIds(List.of())).isEmpty();
        verifyNoInteractions(jobRepository);
    }
}
