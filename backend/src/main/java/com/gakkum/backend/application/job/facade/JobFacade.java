package com.gakkum.backend.application.job.facade;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient.PresignedFileUpload;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobResultCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.PrepareSubmissionFileUploadCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.CancelledJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobCancelResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobSubmissionFileType;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final JobService jobService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final SpecialtyService specialtyService;
    private final StudentService studentService;
    private final JobSubmissionFileStorageClient jobSubmissionFileStorageClient;
    private final ChatAttachmentPolicy chatAttachmentPolicy;
    private final PaymentService paymentService;

    @Transactional
    public void createJob(String username, JobCreateRequest request) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        CreateJobCommand command = request.toCommand(owner.getId());
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());

        jobService.createJob(command);
    }

    @Transactional(readOnly = true)
    public JobDetailResult getJobDetail(String username, Long jobId) {
        userService.getActiveUser(username);
        JobDetailData data = jobService.getJobDetail(jobId);
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
        return JobDetailResult.of(data, groupSpecialties(data.getSpecialtyIds(), specialtiesById));
    }

    @Transactional(readOnly = true)
    public JobSubmissionDetailResult getPendingSubmission(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        JobSubmissionDetailData data = jobService.getPendingSubmission(
                GetJobSubmissionCommand.of(jobId, owner.getId()));

        Student student = studentService.getStudentProfile(data.getJob().getSelectedStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        return JobSubmissionDetailResult.of(data, studentUser);
    }

    /** 완료된 의뢰의 결과물을 의뢰한 사장님 또는 담당 학생에게 보여준다. 작업 시작일은 결제 승인일이다. */
    @Transactional(readOnly = true)
    public JobResultResult getJobResult(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        JobResultData data = jobService.getJobResult(toJobResultCommand(user, jobId));

        Student student = studentService.getStudentProfile(data.getJob().getSelectedStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        ApprovedPaymentData payment = paymentService.getPaidPayment(jobId);
        return JobResultResult.of(data, studentUser, LocalDate.ofInstant(payment.approvedAt(), ZoneId.systemDefault()));
    }

    /** 사장님·학생 외 사용자와 학생 프로필이 없는 학생은 조회 권한이 없으므로 결과물이 없는 것과 같이 거부한다. */
    private GetJobResultCommand toJobResultCommand(User user, Long jobId) {
        if (user.getRole() == UserRole.OWNER) {
            Owner owner = ownerService.getOwnerProfile(user.getId());
            return GetJobResultCommand.ofOwner(jobId, owner.getId());
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentService.findStudentProfileByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_RESULT_NOT_FOUND));
            return GetJobResultCommand.ofStudent(jobId, student.getId());
        }
        throw new BusinessException(ErrorCode.JOB_RESULT_NOT_FOUND);
    }

    /** 매칭된 학생에게 작업물 파일 업로드 URL과 제출에 쓸 공개 URL을 발급한다. 형식·크기는 채팅 첨부 규칙을 따른다. */
    @Transactional(readOnly = true)
    public PrepareSubmissionFileUploadResult prepareSubmissionFileUpload(PrepareSubmissionFileUploadCommand command) {
        Student student = getSubmittingStudent(command.getUsername());
        jobService.getSubmittableJob(command.getJobId(), student.getId());

        ChatMessageType policyType = command.getType() == JobSubmissionFileType.IMAGE
                ? ChatMessageType.IMAGE
                : ChatMessageType.FILE;
        String contentType = chatAttachmentPolicy.validate(
                policyType, command.getFileName(), command.getContentType(), command.getSize());
        String key = jobSubmissionFileStorageClient.newKey(command.getJobId(), student.getId(), command.getFileName());
        PresignedFileUpload presigned = jobSubmissionFileStorageClient.presignUpload(key, contentType, command.getSize());
        return PrepareSubmissionFileUploadResult.of(presigned.url(), presigned.headers(),
                toLocalDateTime(presigned.expiresAt()), presigned.fileUrl());
    }

    /**
     * 매칭된 학생의 첫 초안 제출.
     * 파일 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않는다.
     */
    public JobSubmissionCreateResult submitDraft(CreateJobSubmissionCommand command) {
        Student student = getSubmittingStudent(command.getUsername());
        jobService.validateDraftSubmittable(command.getJobId(), student.getId());
        validateUploadedFiles(command, student.getId());

        JobSubmission submission = jobService.submitDraft(command, student.getId());
        return JobSubmissionCreateResult.from(submission);
    }

    /**
     * 매칭된 학생의 수정안 제출. 수정 번호는 서버가 정한다.
     * 파일 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않는다.
     */
    public JobSubmissionCreateResult submitRevision(CreateJobSubmissionCommand command) {
        Student student = getSubmittingStudent(command.getUsername());
        jobService.validateRevisionSubmittable(command.getJobId(), student.getId());
        validateUploadedFiles(command, student.getId());

        JobSubmission submission = jobService.submitRevision(command, student.getId());
        return JobSubmissionCreateResult.from(submission);
    }

    /** 사장님 본인 의뢰의 검토 대기 제출물에 수정을 요청한다. */
    @Transactional
    public void requestRevision(String username, Long jobId, Long submissionId) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        jobService.requestRevision(RequestJobSubmissionRevisionCommand.of(jobId, submissionId, owner.getId()));
    }

    /** 사장님 본인 의뢰의 검토 대기 제출물을 최종 결과로 수락하고 의뢰를 종료한다. */
    @Transactional
    public void completeSubmission(String username, Long jobId, Long submissionId) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        jobService.completeSubmission(CompleteJobSubmissionCommand.of(jobId, submissionId, owner.getId()));
    }

    /** 사장님 본인 의뢰를 취소한다. 결제 후 진행 중이던 의뢰는 학생 보상금을 뺀 금액을 환불 처리한다. */
    @Transactional
    public JobCancelResult cancelJob(CancelJobCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Owner owner = ownerService.getOwnerProfile(user.getId());
        CancelledJobData cancelled = jobService.cancelJob(command, owner.getId());
        RefundedPaymentData refund = cancelled.isPaid() ? paymentService.refundOnCancel(command.getJobId()) : null;
        return JobCancelResult.of(cancelled.getJob(), refund);
    }

    @Transactional(readOnly = true)
    public OpenJobListResult getOpenJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<OpenJobData> jobs = jobService.getOpenJobs(GetOpenJobsCommand.of(owner.getId()));

        // 보낸 의뢰가 없으면 그냥 반환
        if (jobs.isEmpty()) {
            return OpenJobListResult.of(List.of());
        }

        // 특기 목록 조회
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return OpenJobListResult.of(jobs.stream()
                .map(job -> OpenJobResult.of(job, groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    @Transactional(readOnly = true)
    public MatchedJobListResult getMatchedJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<MatchedJobData> jobs = jobService.getMatchedJobs(GetMatchedJobsCommand.of(owner.getId()));

        if (jobs.isEmpty()) {
            return MatchedJobListResult.of(List.of());
        }

        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(job -> job.getJob().getSelectedStudentProfileId())
                .distinct()
                .toList());
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return MatchedJobListResult.of(jobs.stream()
                .map(job -> MatchedJobResult.of(
                        job,
                        studentsById.get(job.getJob().getSelectedStudentProfileId()),
                        groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    /** 진행중 의뢰 목록을 학생용 응답으로 내려야 하는 사용자인지 확인한다. */
    @Transactional(readOnly = true)
    public boolean isStudent(String username) {
        return userService.getActiveUser(username).getRole() == UserRole.STUDENT;
    }

    /** 학생 본인과 매칭된 진행 중 의뢰를 최신 제출물 상태와 함께 조회한다. */
    @Transactional(readOnly = true)
    public StudentMatchedJobListResult getStudentMatchedJobs(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        List<StudentMatchedJobData> jobs = jobService.getStudentMatchedJobs(
                GetStudentMatchedJobsCommand.of(student.getId()));

        if (jobs.isEmpty()) {
            return StudentMatchedJobListResult.of(List.of());
        }

        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return StudentMatchedJobListResult.of(jobs.stream()
                .map(job -> StudentMatchedJobResult.of(job, groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    @Transactional(readOnly = true)
    public ClosedJobListResult getClosedJobs(String username) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<ClosedJobData> jobs = jobService.getClosedJobs(GetClosedJobsCommand.of(owner.getId()));

        if (jobs.isEmpty()) {
            return ClosedJobListResult.of(List.of());
        }

        // 모집 중에 취소된 의뢰는 매칭된 학생이 없다
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(jobs.stream()
                .map(job -> job.getJob().getSelectedStudentProfileId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<String, User> workersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return ClosedJobListResult.of(jobs.stream()
                .map(job -> {
                    Long studentProfileId = job.getJob().getSelectedStudentProfileId();
                    Student student = studentProfileId == null ? null : studentsById.get(studentProfileId);
                    return ClosedJobResult.of(
                            job,
                            student,
                            student == null ? null : workersById.get(student.getUserId()),
                            groupSpecialties(job.getSpecialtyIds(), specialtiesById));
                })
                .toList());
    }

    /** 학생 프로필이 없는 사용자(사장님 포함)는 작업물을 제출할 수 없다. */
    private Student getSubmittingStudent(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN));
    }

    /** 모든 URL이 이 의뢰·학생용으로 발급한 경로인지 먼저 확인한 뒤 실제 업로드 여부를 확인한다. */
    private void validateUploadedFiles(CreateJobSubmissionCommand command, Long studentProfileId) {
        List<String> keys = command.getFileUrls().stream()
                .map(fileUrl -> jobSubmissionFileStorageClient.findKey(fileUrl, command.getJobId(), studentProfileId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID)))
                .toList();
        for (String key : keys) {
            if (!jobSubmissionFileStorageClient.exists(key)) {
                throw new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_NOT_UPLOADED);
            }
        }
    }

    // 만료 시각은 다른 API 응답과 같은 JVM 기본 시간대로 내린다
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private List<SpecialtyCategoryResult> groupSpecialties(
            List<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        Map<Long, List<SpecialtyDetail>> byCategory = specialtyIds.stream()
                .map(id -> {
                    SpecialtyDetail detail = specialtiesById.get(id);
                    if (detail == null) {
                        throw new IllegalStateException("Specialty not found: " + id);
                    }
                    return detail;
                })
                .sorted(Comparator.comparing(SpecialtyDetail::getId))
                .collect(Collectors.groupingBy(
                        SpecialtyDetail::getCategoryId,
                        TreeMap::new,
                        Collectors.toList()));

        return byCategory.entrySet().stream()
                .map(entry -> SpecialtyCategoryResult.of(
                        entry.getKey(),
                        entry.getValue().get(0).getCategoryName(),
                        entry.getValue().stream()
                                .map(detail -> SpecialtyResult.of(detail.getId(), detail.getName()))
                                .toList()))
                .toList();
    }
}
