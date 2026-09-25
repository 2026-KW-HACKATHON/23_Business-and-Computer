package com.gakkum.backend.domain.job.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobSpecialtyRepository jobSpecialtyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobSubmissionRepository jobSubmissionRepository;
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

    /**
     * 보낸 의뢰 목록 조회 메서드
     * @param command
     * @return
     */
    @Transactional(readOnly = true)
    public List<OpenJobData> getOpenJobs(GetOpenJobsCommand command) {

        // 의뢰 목록 조회
        List<Job> jobs = jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                command.getOwnerProfileId(), JobStatus.OPEN);
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        // 의뢰 목록에 대한 Specialty 를 행으로 모두 가져옴
        List<JobSpecialty> jobSpecialties = jobSpecialtyRepository.findByJobIdIn(jobIds);
        // 각 job id 에 Specialty ID 매핑
        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialties.stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));

        Map<Long, Long> applicantCounts = jobApplicationRepository
                .findByJobIdInAndStatus(jobIds, JobApplicationStatus.PENDING).stream()
                .collect(Collectors.groupingBy(JobApplication::getJobId, Collectors.counting()));

        return jobs.stream()
                .map(job -> OpenJobData.of(
                        job,
                        specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList(),
                        Math.toIntExact(applicantCounts.getOrDefault(job.getId(), 0L))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchedJobData> getMatchedJobs(GetMatchedJobsCommand command) {
        List<Job> jobs = jobRepository.findByOwnerProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                command.getOwnerProfileId(), JobStatus.MATCHED);

        // 없다면 빈 리스트 반환
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));
        Map<Long, JobSubmission> pendingSubmissionsByJobId = jobSubmissionRepository
                .findByJobIdInAndReviewStatus(jobIds, JobSubmissionReviewStatus.PENDING).stream()
                .collect(Collectors.toMap(JobSubmission::getJobId, submission -> submission));

        return jobs.stream()
                .map(job -> MatchedJobData.of(
                        job,
                        specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList(),
                        pendingSubmissionsByJobId.get(job.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClosedJobData> getClosedJobs(GetClosedJobsCommand command) {
        List<Job> jobs = jobRepository.findByOwnerProfileIdAndStatusOrderByCompletedAtDescIdDesc(
                command.getOwnerProfileId(), JobStatus.CLOSED);
        if (jobs.isEmpty()) {
            return List.of();
        }

        // CompletedAt 이 없으면 에러
        for (Job job : jobs) {
            if (job.getCompletedAt() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));

        return jobs.stream()
                .map(job -> ClosedJobData.of(
                        job,
                        specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList()))
                .toList();
    }
}
