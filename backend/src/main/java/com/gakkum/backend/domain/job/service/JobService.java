package com.gakkum.backend.domain.job.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetExploreJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobResultCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.CancelledJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ExploreJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ReviewedJobData;
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
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
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

    @Transactional
    public Job createJob(CreateJobCommand command) {
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
     * @return 저장된 수정안(revisionNumber = 최신 번호 + 1, PENDING)
     */
    @Transactional
    public JobSubmission submitRevision(CreateJobSubmissionCommand command, Long studentProfileId) {
        Job job = jobRepository.findLockedById(command.getJobId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        validateSubmittable(job, studentProfileId);
        int revisionNumber = nextRevisionNumber(job);

        try {
            return jobSubmissionRepository.saveAndFlush(JobSubmission.create(
                    job.getId(), JobSubmissionType.REVISION, revisionNumber, command.getFileUrls(),
                    command.getMessage()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_NOT_REQUESTED);
        }
    }

    /**
     * 사장님이 검토 대기(PENDING) 제출물에 수정을 요청한다.
     * 의뢰 행을 잠가 같은 의뢰의 수정 요청·수정안 제출을 순서대로 처리한다.
     * 요청 후 학생이 낼 수정안 번호(현재 번호 + 1)가 수정 가능 횟수를 넘으면 거부한다.
     * @param command
     */
    @Transactional
    public void requestRevision(RequestJobSubmissionRevisionCommand command) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), command.getOwnerProfileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        JobSubmission submission = jobSubmissionRepository.findById(command.getSubmissionId())
                .filter(found -> found.getJobId().equals(job.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_NOT_FOUND));
        if (job.getStatus() != JobStatus.MATCHED) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVIEW_NOT_AVAILABLE);
        }
        if (submission.getReviewStatus() != JobSubmissionReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_ALREADY_REVIEWED);
        }
        if (submission.getRevisionNumber() >= job.getRevisionCount()) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_REVISION_LIMIT_EXCEEDED);
        }
        submission.requestRevision(now());
    }

    /**
     * 사장님이 검토 대기(PENDING) 초안 또는 수정안을 최종 결과로 수락하고 의뢰를 즉시 종료한다.
     * 의뢰 행을 잠가 같은 의뢰의 수정 요청·수정안 제출과 순서대로 처리한다.
     * @param command
     */
    @Transactional
    public void completeSubmission(CompleteJobSubmissionCommand command) {
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
    }

    /**
     * 사장님 본인의 모집 중(OPEN) 또는 진행 중(MATCHED) 의뢰를 취소한다.
     * 의뢰 행을 잠가 같은 의뢰의 결제 승인·제출물 검토와 순서대로 처리한다.
     * @param command
     * @param ownerProfileId
     * @return 취소된 의뢰와 취소 전 결제 완료(MATCHED) 여부
     */
    @Transactional
    public CancelledJobData cancelJob(CancelJobCommand command, Long ownerProfileId) {
        Job job = jobRepository.findByIdAndOwnerProfileId(command.getJobId(), ownerProfileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        boolean paid = job.getStatus() == JobStatus.MATCHED;
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
        Long categoryId = command.getSpecialtyCategoryId();
        LocalDateTime createdAt = command.getCreatedAtBound();
        Long idBound = command.getIdBound();
        if (categoryId != null) {
            Limit limit = Limit.of(command.getLimit());
            return command.isOldestFirst()
                    ? jobRepository.findExploreOldestInCategory(
                            JobStatus.CANCELLED, categoryId, createdAt, idBound, limit)
                    : jobRepository.findExploreLatestInCategory(
                            JobStatus.CANCELLED, categoryId, createdAt, idBound, limit);
        }
        if (command.isOldestFirst()) {
            return readInSegments(command.getLimit(),
                    limit -> jobRepository.findByStatusNotAndCreatedAtAndIdGreaterThanOrderByIdAsc(
                            JobStatus.CANCELLED, createdAt, idBound, limit),
                    limit -> jobRepository.findByStatusNotAndCreatedAtGreaterThanOrderByCreatedAtAscIdAsc(
                            JobStatus.CANCELLED, createdAt, limit));
        }
        return readInSegments(command.getLimit(),
                limit -> jobRepository.findByStatusNotAndCreatedAtAndIdLessThanOrderByIdDesc(
                        JobStatus.CANCELLED, createdAt, idBound, limit),
                limit -> jobRepository.findByStatusNotAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                        JobStatus.CANCELLED, createdAt, limit));
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

    // createdAt과 같은 JVM 기본 시간대로 완료 시각을 기록한다
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
