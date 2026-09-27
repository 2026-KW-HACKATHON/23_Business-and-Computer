package com.gakkum.backend.domain.job.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
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
     * 결제를 위한 의뢰 정보 반환
     * @param jobId
     * @param ownerProfileId
     * @return OPEN(시작 전) 상태인 특정 의뢰를 반환
     */
    public Job getPayableJobForUpdate(Long jobId, Long ownerProfileId) {
        Job job = jobRepository.findByIdAndOwnerProfileId(jobId, ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        return job;
    }

    /**
     * 결제를 위한 지원 정보 반환
     * @param jobId
     * @param applicationId
     * @return 대기중인 지원 정보를 반환
     */
    public JobApplication getPayableApplication(Long jobId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_APPLICATION_NOT_FOUND));
        if (!application.getJobId().equals(jobId)) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_NOT_FOUND);
        }
        if (application.getStatus() != JobApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_AVAILABLE);
        }
        return application;
    }

    @Transactional(readOnly = true)
    public JobDetailData getJobDetail(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        List<Long> specialtyIds = jobSpecialtyRepository.findByJobIdIn(List.of(jobId)).stream()
                .map(JobSpecialty::getSpecialtyId)
                .toList();
        return JobDetailData.of(job, specialtyIds);
    }

    /**
     * 사장님 본인 의뢰의 검토 대기(PENDING) 제출물 조회
     * @param command
     * @return 의뢰와 현재 검토 대기 중인 초안 또는 수정안
     */
    @Transactional(readOnly = true)
    public JobSubmissionDetailData getPendingSubmission(GetJobSubmissionCommand command) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), command.getOwnerProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        JobSubmission submission = jobSubmissionRepository
                .findByJobIdAndReviewStatus(job.getId(), JobSubmissionReviewStatus.PENDING)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_FOUND));

        // 제출물이 있는 의뢰는 선택된 학생이 반드시 있어야 함
        if (job.getSelectedStudentProfileId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return JobSubmissionDetailData.of(job, submission);
    }

    /**
     * 학생이 작업물 파일을 올릴 수 있는 의뢰인지 확인
     * @param jobId
     * @param studentProfileId
     * @return 요청한 학생이 매칭된 진행 중(MATCHED) 의뢰
     */
    @Transactional(readOnly = true)
    public Job getSubmittableJob(Long jobId, Long studentProfileId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        validateSubmittable(job, studentProfileId);
        return job;
    }

    /**
     * 첫 초안을 제출할 수 있는지 확인. 파일 저장소 확인 전에 빠르게 거절하기 위해 사용한다.
     * @param jobId
     * @param studentProfileId
     */
    @Transactional(readOnly = true)
    public void validateDraftSubmittable(Long jobId, Long studentProfileId) {
        getSubmittableJob(jobId, studentProfileId);
        if (jobSubmissionRepository.existsByJobId(jobId)) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        }
    }

    /**
     * 첫 초안 제출. 마감일이 지나도 제출할 수 있다.
     * 의뢰 행을 잠가 같은 의뢰의 동시 제출을 순서대로 처리하고, 유니크 제약 충돌도 중복 제출로 본다.
     * @param command
     * @param studentProfileId
     * @return 저장된 초안(revisionNumber 0, PENDING)
     */
    @Transactional
    public JobSubmission submitDraft(CreateJobSubmissionCommand command, Long studentProfileId) {
        Job job = jobRepository.findLockedById(command.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        validateSubmittable(job, studentProfileId);
        if (jobSubmissionRepository.existsByJobId(job.getId())) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        }

        try {
            return jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                    job.getId(), JobSubmissionType.DRAFT, 0, command.getFileUrls(), command.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        }
    }

    private void validateSubmittable(Job job, Long studentProfileId) {
        if (!studentProfileId.equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        }
        if (job.getStatus() != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_AVAILABLE);
        }
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
