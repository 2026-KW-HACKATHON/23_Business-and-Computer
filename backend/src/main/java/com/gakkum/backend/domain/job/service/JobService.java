package com.gakkum.backend.domain.job.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobSpecialtyRepository jobSpecialtyRepository;
    private final SpecialtyService specialtyService;

    @Transactional
    public Job createJob(CreateJobCommand command) {
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());

        Job job = Job.create(
                command.getOwnerProfileId(),
                command.getTitle(),
                command.getDescription(),
                command.getBudget(),
                command.getDraftDeadline(),
                command.getFinalDeadline(),
                command.getRevisionCount());

        Job savedJob = jobRepository.save(job);

        List<JobSpecialty> jobSpecialties = command.getSpecialtyIds().stream()
                .map(specialtyId -> JobSpecialty.create(savedJob.getId(), specialtyId))
                .toList();
        jobSpecialtyRepository.saveAll(jobSpecialties);

        return savedJob;
    }
}
