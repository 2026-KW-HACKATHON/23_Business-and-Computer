package com.gakkum.backend.domain.job.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.DownloadJobSubmissionFilesCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.DownloadJobSubmissionFilesCommand.JobFiles;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobApplicantProfileCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobApplicationsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobResultCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetLatestJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentAppliedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.CancelledJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationListData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ReviewedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobProgressStage;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobRepository.DeclineTargetProjection;
import com.gakkum.backend.domain.job.repository.JobRepository.RevisionRequestTargetProjection;
import com.gakkum.backend.domain.job.repository.JobRepository.StartTargetProjection;
import com.gakkum.backend.domain.job.repository.JobRepository.StudentJobCount;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository.ReviewTargetProjection;
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
    private final Clock clock;

    /** 학생이 담당한 완료(CLOSED) 의뢰 수. 리뷰 유무와 무관하다. */
    @Transactional(readOnly = true)
    public long countClosedJobs(Long studentProfileId) {
        return jobRepository.countBySelectedStudentProfileIdAndStatus(studentProfileId, JobStatus.CLOSED);
    }

    /** 사장님이 보낸 의뢰 수. 직접 등록한 의뢰와 제안 결제로 생긴 의뢰를 함께 세고 취소(CANCELLED)만 뺀다. */
    @Transactional(readOnly = true)
    public long countOwnerJobsExcludingCancelled(Long ownerProfileId) {
        return jobRepository.countByOwnerProfileIdAndStatusNot(ownerProfileId, JobStatus.CANCELLED);
    }

    /** 사장님의 진행 중(MATCHED) 의뢰 수. 모집 중과 작업 시작 대기는 세지 않는다. */
    @Transactional(readOnly = true)
    public long countOwnerInProgressJobs(Long ownerProfileId) {
        return jobRepository.countByOwnerProfileIdAndStatus(ownerProfileId, JobStatus.MATCHED);
    }

    /** 사장님의 완료(CLOSED) 의뢰 수. */
    @Transactional(readOnly = true)
    public long countOwnerClosedJobs(Long ownerProfileId) {
        return jobRepository.countByOwnerProfileIdAndStatus(ownerProfileId, JobStatus.CLOSED);
    }

    /**
     * 학생별 담당 완료(CLOSED) 의뢰 수를 한 번에 조회한다. 리뷰 유무와 무관하고 취소 건은 세지 않는다.
     * @param studentProfileIds 학생 프로필 ID 목록
     * @return 요청한 모든 학생 프로필 ID별 완료 의뢰 수, 완료 의뢰가 없는 학생은 0
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countClosedJobsByStudentProfileIds(Collection<Long> studentProfileIds) {
        if (studentProfileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = jobRepository
                .countByStudentProfileIdsAndStatus(studentProfileIds, JobStatus.CLOSED).stream()
                .collect(Collectors.toMap(StudentJobCount::getStudentProfileId, StudentJobCount::getJobCount));
        return studentProfileIds.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> counts.getOrDefault(id, 0L)));
    }

    /** demoSessionId는 의뢰한 사장님의 격리 범위다. 실제 사장님은 null이다. */
    @Transactional
    public Job createJob(CreateJobCommand command, String demoSessionId) {
        Job job = Job.create(
                command.getOwnerProfileId(),
                command.getTitle(),
                command.getDescription(),
                command.getBudget(),
                command.getDraftDeadline(),
                command.getFinalDeadline(),
                command.getRevisionCount(),
                command.getReferenceImageUrls(),
                demoSessionId);

        Job savedJob = jobRepository.save(job);

        List<JobSpecialty> jobSpecialties = command.getSpecialtyIds().stream()
                .map(specialtyId -> JobSpecialty.create(savedJob.getId(), specialtyId))
                .toList();
        jobSpecialtyRepository.saveAll(jobSpecialties);

        return savedJob;
    }

    /**
     * 결제가 승인된 제안으로 수락 대기(AWAITING_START) 의뢰를 만들고 제안의 소분류를 복사한다.
     * 지원서와 채팅방은 만들지 않는다. 같은 제안의 승인은 호출하는 쪽이 제안 행을 잠가 순서대로 처리한다.
     * @param command 제안과 승인된 결제에서 정한 의뢰 값
     * @return 저장된 수락 대기 의뢰
     */
    @Transactional
    public Job createAwaitingStartJob(CreateProposalJobCommand command) {
        Job job = jobRepository.saveAndFlush(Job.createAwaitingStart(
                command.getOwnerProfileId(),
                command.getStudentProfileId(),
                command.getProposalId(),
                command.getTitle(),
                command.getDescription(),
                command.getBudget(),
                command.getDraftDeadline(),
                command.getFinalDeadline(),
                command.getRevisionCount(),
                command.getAcceptanceMessage(),
                command.getDemoSessionId()));

        jobSpecialtyRepository.saveAll(command.getSpecialtyIds().stream()
                .map(specialtyId -> JobSpecialty.create(job.getId(), specialtyId))
                .toList());
        return job;
    }

    /** 제안으로 만든 의뢰를 잠가 반환한다. 결제 전이라 의뢰가 없으면 비어 있다. */
    @Transactional
    public Optional<Job> findJobByProposalIdForUpdate(Long proposalId) {
        return jobRepository.findLockedByProposalId(proposalId);
    }

    /** 제안으로 만든 의뢰. 결제 전이라 의뢰가 없으면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Job> findJobByProposalId(Long proposalId) {
        return jobRepository.findByProposalId(proposalId);
    }

    /** 제안 ID별 연결된 의뢰. 결제 전이라 의뢰가 없는 제안은 키가 없다. */
    @Transactional(readOnly = true)
    public Map<Long, Job> getJobsByProposalIds(Collection<Long> proposalIds) {
        if (proposalIds.isEmpty()) {
            return Map.of();
        }
        return jobRepository.findByProposalIdIn(proposalIds).stream()
                .collect(Collectors.toMap(Job::getProposalId, job -> job));
    }

    /**
     * 학생이 작업을 시작하려는 의뢰의 제안 ID. 제안 행을 먼저 잠그기 위해 잠금 없이 읽는다.
     * 잠금 후 조회가 최신 상태를 읽도록 의뢰를 엔티티로 올리지 않는다.
     * 없는 의뢰는 404, 제안으로 만들지 않은 일반 의뢰는 409, 담당 학생이 아니면 403으로 거부한다.
     * @param jobId
     * @param studentProfileId
     * @return 의뢰를 만든 제안 ID
     */
    @Transactional(readOnly = true)
    public Long getStartableProposalId(Long jobId, Long studentProfileId) {
        StartTargetProjection job = jobRepository.findProjectedById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getProposalId() == null) {
            throw new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE);
        }
        if (!studentProfileId.equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.JOB_START_FORBIDDEN);
        }
        return job.getProposalId();
    }

    /**
     * 담당 학생이 수락 대기(AWAITING_START) 제안 의뢰의 작업을 시작한다. 마감일이 지나도 시작할 수 있고 마감일은 바꾸지 않는다.
     * 제안 행을 잠근 뒤 호출하며, 의뢰 행을 잠근 상태에서 제안 연결과 담당 학생을 다시 확인한다.
     * 이미 시작한 의뢰의 재요청은 기존 시작 시각을 그대로 반환한다.
     * @param jobId
     * @param proposalId 잠금 전에 읽은 제안 ID
     * @param studentProfileId
     * @return 진행 중(MATCHED) 의뢰
     */
    @Transactional
    public Job startJob(Long jobId, Long proposalId, Long studentProfileId) {
        Job job = jobRepository.findLockedById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (!proposalId.equals(job.getProposalId())) {
            throw new BusinessException(ErrorCode.JOB_START_NOT_AVAILABLE);
        }
        if (!studentProfileId.equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.JOB_START_FORBIDDEN);
        }
        job.start(now());
        return job;
    }

    /**
     * 학생이 거절하려는 의뢰의 제안 ID. 제안 행을 먼저 잠그기 위해 잠금 없이 읽고, 의뢰를 엔티티로 올리지 않는다.
     * 없거나 격리 범위가 다른 의뢰는 404, 제안으로 만들지 않은 일반 의뢰는 409, 담당 학생이 아니면 403으로 거부한다.
     * @param jobId
     * @param studentProfileId
     * @param demoSessionId 거절하는 학생의 격리 범위. 실제 학생은 null
     * @return 의뢰를 만든 제안 ID
     */
    @Transactional(readOnly = true)
    public Long getDeclinableProposalId(Long jobId, Long studentProfileId, String demoSessionId) {
        DeclineTargetProjection job = jobRepository.findDeclineTargetById(jobId)
                .filter(found -> Objects.equals(found.getDemoSessionId(), demoSessionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getProposalId() == null) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        }
        if (!studentProfileId.equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_FORBIDDEN);
        }
        return job.getProposalId();
    }

    /**
     * 담당 학생이 수락 대기(AWAITING_START) 제안 의뢰를 거절해 취소한다. 작업 조건과 담당 학생은 그대로 둔다.
     * 제안 행을 잠근 뒤 호출하며, 의뢰 행을 잠근 상태에서 제안 연결과 담당 학생을 다시 확인한다.
     * 이미 시작·거절·종료된 의뢰는 409로 거부한다.
     * @param jobId
     * @param proposalId 잠금 전에 읽은 제안 ID
     * @param studentProfileId
     * @return 취소된(CANCELLED) 의뢰
     */
    @Transactional
    public Job declineJob(Long jobId, Long proposalId, Long studentProfileId) {
        Job job = jobRepository.findLockedById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (!proposalId.equals(job.getProposalId())) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_NOT_AVAILABLE);
        }
        if (!studentProfileId.equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_FORBIDDEN);
        }
        job.decline(now());
        return job;
    }

    /**
     * 학생이 담당하는 모든 상태의 의뢰 조회
     * @param studentProfileId
     * @return 의뢰 ID별 의뢰, 없으면 빈 맵
     */
    @Transactional(readOnly = true)
    public Map<Long, Job> getJobsBySelectedStudentProfileId(Long studentProfileId) {
        return jobRepository.findBySelectedStudentProfileId(studentProfileId).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
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

    /**
     * 의뢰 ID 목록으로 의뢰 일괄 조회
     * @param jobIds
     * @return 의뢰 ID별 의뢰, 하나라도 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Map<Long, Job> getJobsByIds(Collection<Long> jobIds) {
        Map<Long, Job> jobsById = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        if (!jobsById.keySet().containsAll(jobIds)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return jobsById;
    }

    /**
     * 의뢰별 연결된 소분류 ID를 한 번에 조회한다.
     * @param jobIds
     * @return 의뢰 ID별 소분류 ID 목록, 소분류가 없는 의뢰는 키가 없다
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> getSpecialtyIdsByJobIds(Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(
                        JobSpecialty::getJobId,
                        Collectors.mapping(JobSpecialty::getSpecialtyId, Collectors.toList())));
    }

    /**
     * 지원서 ID 목록으로 지원서 일괄 조회
     * @param applicationIds
     * @return 지원서 ID별 지원서, 하나라도 없으면 참조 무결성 오류(500)
     */
    @Transactional(readOnly = true)
    public Map<Long, JobApplication> getJobApplicationsByIds(Collection<Long> applicationIds) {
        Map<Long, JobApplication> applicationsById = jobApplicationRepository.findAllById(applicationIds).stream()
                .collect(Collectors.toMap(JobApplication::getId, Function.identity()));
        if (!applicationsById.keySet().containsAll(applicationIds)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return applicationsById;
    }

    /**
     * 학생 본인이 낸 지원서 전체 조회
     * @param studentProfileId
     * @return 지원서 ID별 지원서, 없으면 빈 맵
     */
    @Transactional(readOnly = true)
    public Map<Long, JobApplication> getJobApplicationsByStudentProfileId(Long studentProfileId) {
        return jobApplicationRepository.findByStudentProfileId(studentProfileId).stream()
                .collect(Collectors.toMap(JobApplication::getId, Function.identity()));
    }

    /**
     * 주어진 의뢰 중 학생 본인이 지원한 의뢰의 지원서 상태 조회
     * @param studentProfileId
     * @param jobIds
     * @return 의뢰 ID별 지원서 상태, 지원하지 않은 의뢰는 키가 없다
     */
    @Transactional(readOnly = true)
    public Map<Long, JobApplicationStatus> getApplicationStatuses(Long studentProfileId, Collection<Long> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return jobApplicationRepository.findByStudentProfileIdAndJobIdIn(studentProfileId, jobIds).stream()
                .collect(Collectors.toMap(JobApplication::getJobId, JobApplication::getStatus));
    }

    @Transactional(readOnly = true)
    public JobDetailData getJobDetail(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        List<Long> specialtyIds = jobSpecialtyRepository.findByJobIdIn(List.of(jobId)).stream()
                .map(JobSpecialty::getSpecialtyId)
                .toList();
        JobSubmission latest = job.getStatus() == JobStatus.MATCHED
                ? jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(jobId).orElse(null)
                : null;
        return JobDetailData.of(job, specialtyIds, calculateProgressStage(job, latest));
    }

    /**
     * 학생이 모집 중(OPEN) 의뢰에 지원한다. 작업 마감일이 지나도 모집 중이면 지원할 수 있다.
     * 의뢰 행을 잠가 같은 의뢰의 결제 선정·취소·다른 지원과 순서대로 처리하고, 의뢰 상태와 선정 학생은 바꾸지 않는다.
     * 같은 학생의 재지원은 기존 지원서 상태와 무관하게 거부하며, 유니크 제약 충돌도 중복 지원으로 본다.
     * 격리 범위(demoSessionId)가 학생과 다른 의뢰는 없는 의뢰와 같은 404로 거부한다.
     * @param command
     * @param studentProfileId
     * @param demoSessionId 지원하는 학생의 격리 범위. 실제 학생은 null
     * @return 저장된 대기 중(PENDING) 지원서
     */
    @Transactional
    public JobApplication createJobApplication(CreateJobApplicationCommand command, Long studentProfileId,
            String demoSessionId) {
        Job job = jobRepository.findLockedById(command.getJobId())
                .filter(found -> Objects.equals(found.getDemoSessionId(), demoSessionId))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_NOT_AVAILABLE);
        }
        if (jobApplicationRepository.existsByJobIdAndStudentProfileId(job.getId(), studentProfileId)) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_ALREADY_EXISTS);
        }

        try {
            return jobApplicationRepository.saveAndFlush(JobApplication.create(
                    studentProfileId, job.getId(), command.getSummary(), command.getWorkPlan(),
                    command.getDeliveryMethod()));
        } catch (DataIntegrityViolationException exception) {
            // 다른 무결성 오류(길이·NOT NULL 등)는 중복 지원이 아니므로 그대로 올린다
            String message = exception.getMessage();
            if (message != null && message.contains(JobApplication.JOB_STUDENT_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.JOB_APPLICATION_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    /**
     * 사장님 본인의 모집 중(OPEN) 의뢰와 대기 중(PENDING) 지원서 전체를 조회한다.
     * 조회만 하므로 의뢰 행을 잠그지 않는다.
     * 존재하지 않거나 다른 사장님의 의뢰는 같은 404, 모집 중이 아닌 본인 의뢰는 409로 거부한다.
     * @param command
     * @return 의뢰, 의뢰의 특기 ID, 정렬되지 않은 대기 중 지원서
     */
    @Transactional(readOnly = true)
    public JobApplicationListData getJobApplications(GetJobApplicationsCommand command) {
        Job job = jobRepository.findById(command.getJobId())
                .filter(found -> found.getOwnerProfileId().equals(command.getOwnerProfileId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_LIST_NOT_AVAILABLE);
        }

        List<Long> jobIds = List.of(job.getId());
        List<Long> specialtyIds = jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .map(JobSpecialty::getSpecialtyId)
                .toList();
        // 모집 중 의뢰 목록의 지원자 수와 같은 기준으로 센다
        List<JobApplication> applications =
                jobApplicationRepository.findByJobIdInAndStatus(jobIds, JobApplicationStatus.PENDING);
        return JobApplicationListData.of(job, specialtyIds, applications);
    }

    /**
     * 의뢰의 대기 중(PENDING) 지원서 전체 조회. 미선정·모집 취소 알림의 수신자를 찾는 데 쓰고 지원서 상태는 바꾸지 않는다.
     * @param jobId
     * @return 대기 중 지원서, 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<JobApplication> getPendingApplications(Long jobId) {
        return jobApplicationRepository.findByJobIdInAndStatus(List.of(jobId), JobApplicationStatus.PENDING);
    }

    /**
     * 사장님이 학생 프로필을 볼 수 있는 본인 의뢰의 지원서를 조회한다. 조회만 하므로 의뢰 행을 잠그지 않는다.
     * 모집 중(OPEN)에는 대기 중(PENDING) 지원서, 매칭·완료(MATCHED·CLOSED) 후에는 선정된 학생의 지원서만 허용한다.
     * 존재하지 않거나 다른 사장님의 의뢰는 같은 404, 취소된 본인 의뢰는 지원서를 확인하기 전에 409로 거부한다.
     * 지원서가 없거나, 다른 의뢰의 지원서이거나, 조회 대상이 아닌 학생의 지원서면 모두 같은 404로 거부한다.
     * @param command
     * @return 프로필을 조회할 수 있는 학생의 지원서
     */
    @Transactional(readOnly = true)
    public JobApplication getProfileViewableApplication(GetJobApplicantProfileCommand command) {
        Job job = jobRepository.findById(command.getJobId())
                .filter(found -> found.getOwnerProfileId().equals(command.getOwnerProfileId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getStatus() == JobStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_PROFILE_NOT_AVAILABLE);
        }

        return jobApplicationRepository.findById(command.getJobApplicationId())
                .filter(application -> application.getJobId().equals(job.getId()))
                .filter(application -> isProfileViewable(job, application))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_APPLICATION_NOT_FOUND));
    }

    private boolean isProfileViewable(Job job, JobApplication application) {
        if (job.getStatus() == JobStatus.OPEN) {
            return application.getStatus() == JobApplicationStatus.PENDING;
        }
        return application.getStudentProfileId().equals(job.getSelectedStudentProfileId());
    }

    /**
     * 사장님 본인 의뢰의 검토 대기(PENDING) 제출물 조회
     * @param command
     * @return 의뢰와 현재 검토 대기 중인 초안 또는 수정안
     */
    @Transactional(readOnly = true)
    public JobSubmissionDetailData getPendingSubmission(GetJobSubmissionCommand command) {
        Job job = jobRepository.findJobByIdAndOwnerProfileId(command.getJobId(), command.getOwnerProfileId())
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
     * 의뢰한 사장님 또는 담당 학생 본인 의뢰의 최신 제출물(수정 번호가 가장 큰 초안 또는 수정안) 조회. 완료·취소된 의뢰도 조회할 수 있다.
     * 존재하지 않거나 당사자가 아닌 의뢰는 같은 404, 제출물이 없는 본인 의뢰는 JOB_SUBMISSION_404_LATEST로 거부한다.
     * @param command
     * @return 최신 제출물
     */
    @Transactional(readOnly = true)
    public JobSubmission getLatestSubmission(GetLatestJobSubmissionCommand command) {
        Job job = jobRepository.findById(command.getJobId())
                .filter(found -> isSubmissionViewer(found, command.getOwnerProfileId(), command.getStudentProfileId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        return jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(job.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_LATEST_NOT_FOUND));
    }

    /**
     * 의뢰한 사장님 또는 담당 학생 본인 의뢰의 모든 제출물(초안·수정안)을 수정 번호 오름차순으로 조회. 작업 상태를 제한하지 않는다.
     * 존재하지 않거나 당사자가 아닌 의뢰는 같은 404로 거부하고, 제출물이 없는 본인 의뢰는 빈 목록을 반환한다.
     * @param command
     * @return 수정 번호 오름차순 제출물 목록
     */
    @Transactional(readOnly = true)
    public List<JobSubmission> getSubmissions(GetJobSubmissionsCommand command) {
        Job job = jobRepository.findById(command.getJobId())
                .filter(found -> isSubmissionViewer(found, command.getOwnerProfileId(), command.getStudentProfileId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        return jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(job.getId());
    }

    /**
     * ZIP으로 내려받을 제출 파일을 고른 의뢰들을 조회. 의뢰마다 제출물 조회와 같은 당사자 확인을 하고 작업 상태는 제한하지 않는다.
     * 하나라도 존재하지 않거나 당사자가 아니면 같은 404, 고른 URL이 그 의뢰의 제출물에 등록된 파일이 아니면 JOB_SUBMISSION_400_FILE_URL로 전체를 거부한다.
     * @param command
     * @param ownerProfileId 사장님으로 조회할 때의 프로필 ID(학생이면 null)
     * @param studentProfileId 학생으로 조회할 때의 프로필 ID(사장님이면 null)
     * @return 의뢰 ID별 의뢰
     */
    @Transactional(readOnly = true)
    public Map<Long, Job> getSubmissionDownloadJobs(
            DownloadJobSubmissionFilesCommand command, Long ownerProfileId, Long studentProfileId) {
        List<Long> jobIds = command.getJobs().stream().map(JobFiles::getJobId).toList();
        Map<Long, Job> jobsById = jobRepository.findAllById(jobIds).stream()
                .filter(job -> isSubmissionViewer(job, ownerProfileId, studentProfileId))
                .collect(Collectors.toMap(Job::getId, job -> job));
        if (!jobsById.keySet().containsAll(jobIds)) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }

        Map<Long, Set<String>> submittedFileUrlsByJobId = jobSubmissionRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(
                        JobSubmission::getJobId,
                        Collectors.flatMapping(submission -> submission.getFileUrls().stream(), Collectors.toSet())));
        for (JobFiles files : command.getJobs()) {
            if (!submittedFileUrlsByJobId.getOrDefault(files.getJobId(), Set.of()).containsAll(files.getFileUrls())) {
                throw new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID);
            }
        }
        return jobsById;
    }

    private boolean isSubmissionViewer(Job job, Long ownerProfileId, Long studentProfileId) {
        if (ownerProfileId != null) {
            return ownerProfileId.equals(job.getOwnerProfileId());
        }
        return studentProfileId != null && studentProfileId.equals(job.getSelectedStudentProfileId());
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
     * @param fileSizes 파일 URL별 바이트 크기
     * @return 저장된 초안(revisionNumber 0, PENDING)
     */
    @Transactional
    public JobSubmission submitDraft(
            CreateJobSubmissionCommand command, Long studentProfileId, Map<String, Long> fileSizes) {
        Job job = jobRepository.findLockedById(command.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        validateSubmittable(job, studentProfileId);
        if (jobSubmissionRepository.existsByJobId(job.getId())) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        }

        try {
            return jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                    job.getId(), JobSubmissionType.DRAFT, 0, command.getFileUrls(), fileSizes, command.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_EXISTS);
        }
    }

    /**
     * 수정안을 제출할 수 있는지 확인. 파일 저장소 확인 전에 빠르게 거절하기 위해 사용한다.
     * @param jobId
     * @param studentProfileId
     */
    @Transactional(readOnly = true)
    public void validateRevisionSubmittable(Long jobId, Long studentProfileId) {
        nextRevisionNumber(getSubmittableJob(jobId, studentProfileId));
    }

    /**
     * 수정안 제출. 최신 제출물이 수정 요청(REVISION_REQUESTED) 상태일 때만 다음 번호로 저장한다.
     * 최종 마감일이 지나도 제출할 수 있다.
     * 의뢰 행을 잠가 같은 의뢰의 동시 제출을 순서대로 처리하고, 유니크 제약 충돌도 수정 요청 없음으로 본다.
     * @param command
     * @param studentProfileId
     * @param fileSizes 파일 URL별 바이트 크기
     * @return 저장된 수정안(revisionNumber = 최신 번호 + 1, PENDING)
     */
    @Transactional
    public JobSubmission submitRevision(
            CreateJobSubmissionCommand command, Long studentProfileId, Map<String, Long> fileSizes) {
        Job job = jobRepository.findLockedById(command.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        validateSubmittable(job, studentProfileId);
        int revisionNumber = nextRevisionNumber(job);

        try {
            return jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                    job.getId(), JobSubmissionType.REVISION, revisionNumber, command.getFileUrls(),
                    fileSizes, command.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
        }
    }

    /**
     * 수정을 요청할 수 있는지 잠금 없이 확인. 사진 저장소 확인 전에 빠르게 거절하기 위해 사용한다.
     * 거부 조건과 순서는 requestRevision과 같다.
     * @param jobId
     * @param submissionId
     * @param ownerProfileId
     */
    @Transactional(readOnly = true)
    public void validateRevisionRequestable(Long jobId, Long submissionId, Long ownerProfileId) {
        RevisionRequestTargetProjection job = jobRepository
                .findRevisionRequestTargetByIdAndOwnerProfileId(jobId, ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        ReviewTargetProjection submission = jobSubmissionRepository.findReviewTargetById(submissionId)
                .filter(found -> found.getJobId().equals(jobId))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_FOUND));
        validateRevisionRequestable(
                job.getStatus(), job.getRevisionCount(), submission.getReviewStatus(), submission.getRevisionNumber());
    }

    /**
     * 사장님이 검토 대기(PENDING) 제출물에 수정을 요청하고 요청 내용·참고 사진·요청 시각을 상태와 함께 저장한다.
     * 의뢰 행을 잠가 같은 의뢰의 수정 요청·수정안 제출을 순서대로 처리하고, 잠근 뒤 조건을 다시 확인한다.
     * 요청 후 학생이 낼 수정안 번호(현재 번호 + 1)가 수정 가능 횟수를 넘으면 거부한다.
     * @param command
     * @param ownerProfileId
     * @return 수정을 요청한 진행 중(MATCHED) 의뢰
     */
    @Transactional
    public Job requestRevision(RequestJobSubmissionRevisionCommand command, Long ownerProfileId) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        JobSubmission submission = jobSubmissionRepository.findById(command.getSubmissionId())
                .filter(found -> found.getJobId().equals(job.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_FOUND));
        validateRevisionRequestable(
                job.getStatus(), job.getRevisionCount(), submission.getReviewStatus(), submission.getRevisionNumber());
        submission.requestRevision(now(), command.getMessage(), command.getReferenceImageUrls());
        return job;
    }

    private void validateRevisionRequestable(JobStatus jobStatus, Integer revisionCount,
            JobSubmissionReviewStatus reviewStatus, Integer revisionNumber) {
        if (jobStatus != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        }
        if (reviewStatus != JobSubmissionReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
        }
        if (revisionNumber >= revisionCount) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        }
    }

    /**
     * 사장님이 검토 대기(PENDING) 초안 또는 수정안을 최종 결과로 수락하고 의뢰를 즉시 종료한다.
     * 의뢰 행을 잠가 같은 의뢰의 수정 요청·수정안 제출과 순서대로 처리한다.
     * @param command
     * @return 종료된(CLOSED) 의뢰
     */
    @Transactional
    public Job completeSubmission(CompleteJobSubmissionCommand command) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), command.getOwnerProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        JobSubmission submission = jobSubmissionRepository.findById(command.getSubmissionId())
                .filter(found -> found.getJobId().equals(job.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_FOUND));
        if (job.getStatus() != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        }
        submission.approve();
        job.complete(now());
        return job;
    }

    /**
     * 사장님 본인의 모집 중(OPEN) 또는 진행 중(MATCHED) 의뢰를 취소한다.
     * 의뢰 행을 잠가 같은 의뢰의 결제 승인·작업물 제출·제출물 검토와 순서대로 처리한다.
     * 학생이 한 번이라도 작업물을 제출한 진행 중 의뢰는 제출물의 검토 상태와 관계없이 취소할 수 없다.
     * @param command
     * @param ownerProfileId
     * @return 취소된 의뢰와 취소 전 결제 완료(MATCHED) 여부
     */
    @Transactional
    public CancelledJobData cancelJob(CancelJobCommand command, Long ownerProfileId) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        boolean paid = job.getStatus() == JobStatus.MATCHED;
        if (paid && jobSubmissionRepository.existsByJobId(job.getId())) {
            throw new BusinessException(ErrorCode.JOB_CANCEL_SUBMITTED);
        }
        job.cancel(now(), command.getCancelReason(), command.getMessageToStudent());
        return CancelledJobData.of(job, paid);
    }

    /**
     * 완료된(CLOSED) 의뢰의 결과물과 전체 제출 이력을 조회한다.
     * 존재하지 않는 의뢰, 요청자가 의뢰한 사장님·담당 학생이 아닌 의뢰, 완료되지 않은 의뢰는 모두 같은 404로 거부한다.
     * @param command 사장님 또는 학생 프로필 ID 중 하나만 채워진 요청
     * @return 수정 번호 오름차순 제출물과 최종 승인된 제출물
     */
    @Transactional(readOnly = true)
    public JobResultData getJobResult(GetJobResultCommand command) {
        Job job = jobRepository.findById(command.getJobId())
                .filter(found -> found.getStatus() == JobStatus.CLOSED)
                .filter(found -> isResultViewer(found, command))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_RESULT_NOT_FOUND));
        if (job.getCompletedAt() == null || job.getSelectedStudentProfileId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 완료된 의뢰는 마지막 제출물이 사장님이 최종 승인한 제출물이어야 함
        List<JobSubmission> submissions = jobSubmissionRepository.findByJobIdOrderByRevisionNumberAsc(job.getId());
        if (submissions.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        JobSubmission approved = submissions.get(submissions.size() - 1);
        if (approved.getReviewStatus() != JobSubmissionReviewStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return JobResultData.of(job, submissions, approved);
    }

    /**
     * 사장님 본인의 완료된(CLOSED) 의뢰를 잠가 반환한다. 같은 의뢰의 리뷰 작성을 순서대로 처리하기 위해 사용한다.
     * 존재하지 않거나 다른 사장님의 의뢰는 404, 완료되지 않은 의뢰는 409로 거부한다.
     * @param jobId
     * @param ownerProfileId
     * @return 담당 학생이 정해진 완료 의뢰
     */
    @Transactional
    public Job getReviewableJobForUpdate(Long jobId, Long ownerProfileId) {
        Job job = jobRepository.findByIdAndOwnerProfileId(jobId, ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        if (job.getStatus() != JobStatus.CLOSED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AVAILABLE);
        }

        // 완료된 의뢰는 담당 학생이 반드시 있어야 함
        if (job.getSelectedStudentProfileId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return job;
    }

    /**
     * 리뷰가 작성된 의뢰와 최종 승인된 제출물을 조회한다. 리뷰 권한 확인이 끝난 뒤에만 호출한다.
     * 리뷰가 있는데 의뢰나 승인 제출물이 없으면 데이터 이상으로 보고 500으로 거부한다.
     * @param jobId
     * @return 의뢰와 최종 승인(APPROVED)된 초안 또는 수정안
     */
    @Transactional(readOnly = true)
    public ReviewedJobData getReviewedJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        JobSubmission approved = jobSubmissionRepository
                .findByJobIdAndReviewStatus(job.getId(), JobSubmissionReviewStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        return ReviewedJobData.of(job, approved);
    }

    private boolean isResultViewer(Job job, GetJobResultCommand command) {
        if (command.getOwnerProfileId() != null) {
            return command.getOwnerProfileId().equals(job.getOwnerProfileId());
        }
        return command.getStudentProfileId() != null
                && command.getStudentProfileId().equals(job.getSelectedStudentProfileId());
    }

    private int nextRevisionNumber(Job job) {
        JobSubmission latest = jobSubmissionRepository.findFirstByJobIdOrderByRevisionNumberDesc(job.getId())
                .filter(submission -> submission.getReviewStatus() == JobSubmissionReviewStatus.REVISION_REQUESTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED));
        int revisionNumber = latest.getRevisionNumber() + 1;
        if (revisionNumber > job.getRevisionCount()) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        }
        return revisionNumber;
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
                        Math.toIntExact(applicantCounts.getOrDefault(job.getId(), 0L)),
                        calculateProgressStage(job, null)))
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
        Map<Long, JobSubmission> latestSubmissionsByJobId = latestSubmissionsByJobId(jobIds);

        return jobs.stream()
                .map(job -> {
                    JobSubmission latest = latestSubmissionsByJobId.get(job.getId());
                    // 검토 대기 제출물은 항상 최신 제출물이다
                    JobSubmission pending = latest != null
                            && latest.getReviewStatus() == JobSubmissionReviewStatus.PENDING ? latest : null;
                    return MatchedJobData.of(
                            job,
                            specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                    .map(JobSpecialty::getSpecialtyId)
                                    .toList(),
                            pending,
                            calculateProgressStage(job, latest));
                })
                .toList();
    }

    /**
     * 학생 본인과 매칭된 진행 중(MATCHED) 의뢰를 생성 최신순으로 조회하고 의뢰별 최신 제출물을 연결한다.
     * @param command
     * @return 제출물이 없는 의뢰는 latestSubmission이 null
     */
    @Transactional(readOnly = true)
    public List<StudentMatchedJobData> getStudentMatchedJobs(GetStudentMatchedJobsCommand command) {
        List<Job> jobs = jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(
                command.getStudentProfileId(), JobStatus.MATCHED);
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));
        Map<Long, JobSubmission> latestSubmissionsByJobId = latestSubmissionsByJobId(jobIds);

        return jobs.stream()
                .map(job -> StudentMatchedJobData.of(
                        job,
                        specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList(),
                        latestSubmissionsByJobId.get(job.getId()),
                        calculateProgressStage(job, latestSubmissionsByJobId.get(job.getId()))))
                .toList();
    }

    /**
     * 학생 본인이 지원한 의뢰 중 모집 중 대기 지원과 미선정 지원 이력을 최신 지원순으로 조회한다. 선정된 지원은 뺀다.
     * 다른 학생이 선정됐지만 대기 중(PENDING)으로 남은 지원서는 이 목록에서만 탈락(REJECTED)으로 계산하고 저장하지 않는다.
     * 작업 마감일이 지나도 모집 중이면 포함한다. 격리 범위(demoSessionId)가 학생과 다르거나 없는 의뢰의 지원서는 뺀다.
     * 지원서·의뢰·특기는 항목 수와 무관하게 한 번씩만 조회한다.
     * @param command
     * @return 지원 시각 내림차순 → 지원서 ID 내림차순
     */
    @Transactional(readOnly = true)
    public List<StudentAppliedJobData> getStudentAppliedJobs(GetStudentAppliedJobsCommand command) {
        List<JobApplication> applications = jobApplicationRepository
                .findByStudentProfileIdAndStatusInOrderByCreatedAtDescIdDesc(
                        command.getStudentProfileId(),
                        List.of(JobApplicationStatus.PENDING, JobApplicationStatus.REJECTED));
        if (applications.isEmpty()) {
            return List.of();
        }

        Map<Long, Job> jobsById = jobRepository.findByIdInAndDemoSessionId(
                        applications.stream().map(JobApplication::getJobId).toList(),
                        command.getDemoSessionId()).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, JobApplicationStatus> listedStatuses = new HashMap<>();
        for (JobApplication application : applications) {
            Job job = jobsById.get(application.getJobId());
            JobApplicationStatus listedStatus = job == null ? null : listedApplicationStatus(application, job);
            if (listedStatus != null) {
                listedStatuses.put(application.getId(), listedStatus);
            }
        }
        List<JobApplication> listed = applications.stream()
                .filter(application -> listedStatuses.containsKey(application.getId()))
                .toList();
        if (listed.isEmpty()) {
            return List.of();
        }

        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialtyRepository
                .findByJobIdIn(listed.stream().map(JobApplication::getJobId).toList()).stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));

        return listed.stream()
                .map(application -> StudentAppliedJobData.of(
                        jobsById.get(application.getJobId()),
                        application,
                        listedStatuses.get(application.getId()),
                        specialtiesByJobId.getOrDefault(application.getJobId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList()))
                .toList();
    }

    /**
     * '내가 지원한 의뢰' 목록에 내릴 지원 상태. 목록에서 빼는 지원서는 null이다.
     * 저장된 탈락은 의뢰 상태와 무관하게 탈락이다. 대기 중 지원서는 다른 학생이 선정됐으면 탈락, 선정 없이 모집 중이면 대기이고,
     * 본인이 선정됐거나 선정 없이 모집이 끝난(취소 등) 의뢰의 지원서는 뺀다.
     */
    private static JobApplicationStatus listedApplicationStatus(JobApplication application, Job job) {
        if (application.getStatus() == JobApplicationStatus.REJECTED) {
            return JobApplicationStatus.REJECTED;
        }
        Long selectedStudentProfileId = job.getSelectedStudentProfileId();
        if (selectedStudentProfileId != null) {
            return selectedStudentProfileId.equals(application.getStudentProfileId())
                    ? null
                    : JobApplicationStatus.REJECTED;
        }
        return job.getStatus() == JobStatus.OPEN ? JobApplicationStatus.PENDING : null;
    }

    @Transactional(readOnly = true)
    public List<ClosedJobData> getClosedJobs(GetClosedJobsCommand command) {
        List<Job> jobs = jobRepository.findByOwnerProfileIdAndStatusInOrderByCompletedAtDescIdDesc(
                command.getOwnerProfileId(), List.of(JobStatus.CLOSED, JobStatus.CANCELLED));
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
                                .toList(),
                        calculateProgressStage(job, null)))
                .toList();
    }

    /**
     * 탐색 목록용으로 취소되지 않은 의뢰를 커서 경계 뒤부터 정렬 순서대로 limit개까지 읽는다.
     * 진행 단계 계산에 필요한 최신 제출물은 진행 중(MATCHED) 의뢰만 한 번에 조회한다.
     */
    @Transactional(readOnly = true)
    public List<ExploreJobData> getExploreJobs(GetExploreJobsCommand command) {
        List<Job> jobs = findExploreJobs(command);
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<JobSpecialty>> specialtiesByJobId = jobSpecialtyRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(JobSpecialty::getJobId));
        List<Long> matchedJobIds = jobs.stream()
                .filter(job -> job.getStatus() == JobStatus.MATCHED)
                .map(Job::getId)
                .toList();
        Map<Long, JobSubmission> latestSubmissionsByJobId =
                matchedJobIds.isEmpty() ? Map.of() : latestSubmissionsByJobId(matchedJobIds);

        return jobs.stream()
                .map(job -> ExploreJobData.of(
                        job,
                        specialtiesByJobId.getOrDefault(job.getId(), List.of()).stream()
                                .map(JobSpecialty::getSpecialtyId)
                                .toList(),
                        calculateProgressStage(job, latestSubmissionsByJobId.get(job.getId()))))
                .toList();
    }

    private List<Job> findExploreJobs(GetExploreJobsCommand command) {
        String demoSessionId = command.getDemoSessionId();
        Long categoryId = command.getSpecialtyCategoryId();
        LocalDateTime createdAt = command.getCreatedAtBound();
        Long idBound = command.getIdBound();
        if (categoryId != null) {
            Limit limit = Limit.of(command.getLimit());
            return command.isOldestFirst()
                    ? jobRepository.findExploreOldestInCategory(
                            demoSessionId, JobStatus.CANCELLED, categoryId, createdAt, idBound, limit)
                    : jobRepository.findExploreLatestInCategory(
                            demoSessionId, JobStatus.CANCELLED, categoryId, createdAt, idBound, limit);
        }
        if (command.isOldestFirst()) {
            return readInSegments(command.getLimit(),
                    limit -> jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                            demoSessionId, JobStatus.CANCELLED, createdAt, idBound, limit),
                    limit -> jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                            demoSessionId, JobStatus.CANCELLED, createdAt, limit));
        }
        return readInSegments(command.getLimit(),
                limit -> jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtAndIdLessThanOrderByIdDesc(
                        demoSessionId, JobStatus.CANCELLED, createdAt, idBound, limit),
                limit -> jobRepository.findByDemoSessionIdAndStatusNotAndProposalIdIsNullAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                        demoSessionId, JobStatus.CANCELLED, createdAt, limit));
    }

    /** 커서 경계 뒤를 정렬 순서상 앞 구간부터 읽어 limit개를 채운다. 채워지면 남은 구간은 조회하지 않는다. */
    @SafeVarargs
    private static List<Job> readInSegments(int limit, Function<Limit, List<Job>>... segments) {
        List<Job> jobs = new ArrayList<>();
        for (Function<Limit, List<Job>> segment : segments) {
            if (jobs.size() >= limit) {
                break;
            }
            jobs.addAll(segment.apply(Limit.of(limit - jobs.size())));
        }
        return jobs;
    }

    private Map<Long, JobSubmission> latestSubmissionsByJobId(List<Long> jobIds) {
        return jobSubmissionRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.toMap(
                        JobSubmission::getJobId,
                        submission -> submission,
                        (left, right) -> left.getRevisionNumber() >= right.getRevisionNumber() ? left : right));
    }

    /**
     * 의뢰 상태와 최신 제출물로 현재 진행 단계를 계산한다. 저장하지 않는다.
     * 진행 중(MATCHED)에서는 제출물이 없으면 시작, 수정 요청이 있었거나 수정안이 제출됐으면 수정, 그 외에는 초안이다.
     * @param job
     * @param latestSubmission MATCHED 의뢰의 최신 제출물(없으면 null). 다른 상태에서는 사용하지 않는다.
     */
    private JobProgressStage calculateProgressStage(Job job, JobSubmission latestSubmission) {
        return switch (job.getStatus()) {
            case OPEN -> JobProgressStage.REQUESTED;
            case AWAITING_START -> JobProgressStage.AWAITING_START;
            case CLOSED -> JobProgressStage.COMPLETED;
            case CANCELLED -> JobProgressStage.CANCELLED;
            case MATCHED -> {
                if (latestSubmission == null) {
                    yield JobProgressStage.STARTED;
                }
                boolean inRevision = latestSubmission.getSubmissionType() == JobSubmissionType.REVISION
                        || latestSubmission.getReviewStatus() == JobSubmissionReviewStatus.REVISION_REQUESTED;
                yield inRevision ? JobProgressStage.REVISION : JobProgressStage.DRAFT;
            }
        };
    }

    // createdAt과 같은 JVM 기본 시간대로 완료 시각을 기록한다.
    // PostgreSQL timestamp 정밀도(마이크로초)에 맞춰 반환값과 저장값이 어긋나지 않게 한다
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
