package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.SpecialtyRepository;
import com.gakkum.backend.domain.specialty.repository.StudentSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final SpecialtyRepository specialtyRepository = mock(SpecialtyRepository.class);
    private final StudentSpecialtyRepository studentSpecialtyRepository = mock(StudentSpecialtyRepository.class);
    private final SpecialtyService specialtyService = new SpecialtyService(
            specialtyRepository,
            studentSpecialtyRepository);
    private final JobService jobService = new JobService(
            jobRepository,
            jobSpecialtyRepository,
            specialtyService);

    @Test
    void createsJobWithSpecialties() {
        when(specialtyRepository.countByIdIn(List.of(1L, 2L))).thenReturn(2L);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateJobCommand command = CreateJobCommand.of(
                10L,
                List.of(1L, 2L),
                "의뢰 제목",
                "맡기고 싶은 일",
                500000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                1);

        Job savedJob = jobService.createJob(command);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(savedJob).isSameAs(jobCaptor.getValue());
        assertThat(savedJob.getOwnerProfileId()).isEqualTo(10L);
        assertThat(savedJob.getTitle()).isEqualTo("의뢰 제목");
        assertThat(savedJob.getDescription()).isEqualTo("맡기고 싶은 일");
        assertThat(savedJob.getBudget()).isEqualTo(500000L);
        assertThat(savedJob.getDraftDeadline()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(savedJob.getFinalDeadline()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(savedJob.getRevisionCount()).isEqualTo(1);

        ArgumentCaptor<List<JobSpecialty>> specialtiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobSpecialtyRepository).saveAll(specialtiesCaptor.capture());
        assertThat(specialtiesCaptor.getValue())
                .extracting(JobSpecialty::getSpecialtyId)
                .containsExactly(1L, 2L);
    }

    @Test
    void rejectsWhenSpecialtyDoesNotExist() {
        when(specialtyRepository.countByIdIn(List.of(1L, 99L))).thenReturn(1L);

        CreateJobCommand command = CreateJobCommand.of(
                10L,
                List.of(1L, 99L),
                "의뢰 제목",
                "맡기고 싶은 일",
                500000L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                1);

        assertThatThrownBy(() -> jobService.createJob(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));

        verify(jobRepository, never()).save(any(Job.class));
    }
}
