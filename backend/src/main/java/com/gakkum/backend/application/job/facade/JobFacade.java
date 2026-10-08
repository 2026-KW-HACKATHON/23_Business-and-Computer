package com.gakkum.backend.application.job.facade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.entity.ChatMessageType;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.client.JobSubmissionArchive;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient.PresignedFileUpload;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.DownloadJobSubmissionFilesCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.DownloadJobSubmissionFilesCommand.JobFiles;
import com.gakkum.backend.domain.job.dto.JobCommandDto.GetClosedJobsCommand;
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
import com.gakkum.backend.domain.job.dto.JobCommandDto.PrepareSubmissionFileUploadCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.CancelledJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ClosedJobResult;
import com.gakkum.backend.domain.job.dto.JobApplicationSort;
import com.gakkum.backend.domain.job.dto.JobQueryDto.ApplicantReviewResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicantProfileResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicantResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationListData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobApplicationListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobCancelResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobLatestSubmissionResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobResultResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionCreateResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionDetailResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.JobSubmissionHistoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.MatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.PrepareSubmissionFileUploadResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentAppliedJobResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobListResult;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobResult;
import com.gakkum.backend.domain.job.dto.JobSubmissionFileType;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobApplicationStatus;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedPaymentData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.service.ReviewService;
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
    private final ReviewService reviewService;
    private final CertificateService certificateService;
    private final ProposalService proposalService;
    private final MediaService mediaService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ChatRoomService chatRoomService;

    // 사진 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 저장 트랜잭션은 JobService에 둔다
    public void createJob(String username, JobCreateRequest request) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        CreateJobCommand command = request.toCommand(owner.getId());
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());
        validateUploadedImages(command.getReferenceImageUrls(), user.getId());

        jobService.createJob(command, owner.getDemoSessionId());
    }

    private void validateUploadedImages(List<String> imageUrls, String userId) {
        List<String> keys = imageUrls.stream()
                .map(imageUrl -> mediaService.findImageKey(userId, ImagePurpose.JOB, imageUrl)
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_IMAGE_URL_INVALID)))
                .toList();
        for (String key : keys) {
            if (!mediaService.isImageUploaded(key)) {
                throw new BusinessException(ErrorCode.JOB_IMAGE_NOT_UPLOADED);
            }
        }
    }

    /**
     * 의뢰 상세는 모든 활성 사용자가 조회한다. 매장명·주소는 의뢰한 사장님의 현재 프로필에서 가져와 항상 내리고,
     * 학생에게는 본인 지원서 상태를 더한다. 취소된 의뢰의 취소 정보는 의뢰한 사장님과 선정 학생에게만 더한다.
     * 격리 범위(demoSessionId)가 조회자와 다른 의뢰는 없는 의뢰와 같은 404로 거부한다.
     * 결제 후(진행 중) 취소된 의뢰에는 선정 학생이 있고 환불 주문이 반드시 있어야 한다. 모집 중 취소는 결제가 없다.
     */
    @Transactional(readOnly = true)
    public JobDetailResult getJobDetail(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        JobDetailData data = jobService.getJobDetail(jobId);
        if (!Objects.equals(data.getJob().getDemoSessionId(), user.getDemoSessionId())) {
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
        List<SpecialtyCategoryResult> specialtyCategories = groupSpecialties(data.getSpecialtyIds(), specialtiesById);

        Job job = data.getJob();
        Owner owner = ownerService.getOwnerProfileById(job.getOwnerProfileId());
        // 지원 상태와 취소 당사자 판정이 로그인 학생의 프로필 조회 한 번을 함께 쓴다
        Long viewerStudentProfileId = user.getRole() == UserRole.STUDENT
                ? studentService.findStudentProfileByUserId(user.getId()).map(Student::getId).orElse(null)
                : null;
        JobApplicationStatus applied = viewerStudentProfileId == null
                ? null
                : jobService.getApplicationStatuses(viewerStudentProfileId, List.of(jobId)).get(jobId);

        if (job.getStatus() != JobStatus.CANCELLED || !isCancellationParty(user, job, viewerStudentProfileId)) {
            return JobDetailResult.of(data, specialtyCategories, owner, applied);
        }
        RefundedPaymentData refund = job.getSelectedStudentProfileId() == null
                ? null
                : paymentService.getRefundedPayment(jobId);
        return JobDetailResult.ofCancelled(data, specialtyCategories, owner, applied, refund);
    }

    /** 의뢰한 사장님 또는 선정 학생인지 확인한다. 해당 역할의 프로필이 없는 사용자는 당사자가 아니다. */
    private boolean isCancellationParty(User user, Job job, Long viewerStudentProfileId) {
        if (user.getRole() == UserRole.OWNER) {
            return ownerService.findOwnerProfileByUserId(user.getId())
                    .filter(owner -> owner.getId().equals(job.getOwnerProfileId()))
                    .isPresent();
        }
        return viewerStudentProfileId != null && viewerStudentProfileId.equals(job.getSelectedStudentProfileId());
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

    /**
     * 의뢰한 사장님 또는 담당 학생 본인 의뢰의 최신 제출물을 수정 요청 내용과 함께 조회한다.
     * 사장님·학생이 아니거나 해당 역할의 프로필이 없으면 의뢰를 조회하기 전에 거부한다.
     */
    @Transactional(readOnly = true)
    public JobLatestSubmissionResult getLatestSubmission(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        return JobLatestSubmissionResult.from(
                jobService.getLatestSubmission(toLatestSubmissionCommand(user, jobId)));
    }

    /**
     * 의뢰한 사장님 또는 담당 학생 본인 의뢰의 모든 제출물을 각각의 수정 요청 내용과 함께 조회한다.
     * 사장님·학생이 아니거나 해당 역할의 프로필이 없으면 의뢰를 조회하기 전에 거부한다.
     */
    @Transactional(readOnly = true)
    public JobSubmissionHistoryResult getSubmissions(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        return JobSubmissionHistoryResult.from(jobService.getSubmissions(toSubmissionsCommand(user, jobId)));
    }

    /** 로그인 역할에 해당하는 프로필 ID만 전달해, 다른 역할의 프로필 ID와 숫자가 같아도 당사자로 보지 않게 한다. */
    private GetJobSubmissionsCommand toSubmissionsCommand(User user, Long jobId) {
        if (user.getRole() == UserRole.OWNER) {
            Owner owner = ownerService.findOwnerProfileByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
            return GetJobSubmissionsCommand.ofOwner(jobId, owner.getId());
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentService.findStudentProfileByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
            return GetJobSubmissionsCommand.ofStudent(jobId, student.getId());
        }
        throw new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN);
    }

    /** 로그인 역할에 해당하는 프로필 ID만 전달해, 다른 역할의 프로필 ID와 숫자가 같아도 당사자로 보지 않게 한다. */
    private GetLatestJobSubmissionCommand toLatestSubmissionCommand(User user, Long jobId) {
        if (user.getRole() == UserRole.OWNER) {
            Owner owner = ownerService.findOwnerProfileByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
            return GetLatestJobSubmissionCommand.ofOwner(jobId, owner.getId());
        }
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentService.findStudentProfileByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
            return GetLatestJobSubmissionCommand.of(jobId, student.getId());
        }
        throw new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN);
    }

    /**
     * 여러 의뢰에서 고른 제출 파일의 ZIP 다운로드를 준비한다. 의뢰마다 제출물 조회와 같은 당사자 확인을 하고,
     * 고른 URL이 그 의뢰의 제출물에 등록된 저장소 파일인지와 파일이 실제로 있는지를 모두 확인한 뒤에만 다운로드를 돌려준다.
     * 저장소 확인과 전송이 DB 트랜잭션과 커넥션을 붙잡지 않도록 트랜잭션은 JobService의 조회에만 둔다.
     * 돌려준 다운로드는 처리 슬롯을 쥐고 있으므로 호출한 쪽이 전송하거나 close 해야 한다.
     */
    public JobSubmissionArchive prepareSubmissionDownload(DownloadJobSubmissionFilesCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Long ownerProfileId = null;
        Long studentProfileId = null;
        if (user.getRole() == UserRole.OWNER) {
            ownerProfileId = ownerService.findOwnerProfileByUserId(user.getId()).map(Owner::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
        } else if (user.getRole() == UserRole.STUDENT) {
            studentProfileId = studentService.findStudentProfileByUserId(user.getId()).map(Student::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN));
        } else {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_VIEW_FORBIDDEN);
        }
        Map<Long, Job> jobsById = jobService.getSubmissionDownloadJobs(command, ownerProfileId, studentProfileId);

        List<JobSubmissionArchive.File> files = new ArrayList<>();
        for (JobFiles jobFiles : command.getJobs()) {
            Job job = jobsById.get(jobFiles.getJobId());
            for (String fileUrl : jobFiles.getFileUrls()) {
                String key = jobSubmissionFileStorageClient
                        .findKey(fileUrl, job.getId(), job.getSelectedStudentProfileId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID));
                files.add(new JobSubmissionArchive.File(job.getId(), key));
            }
        }

        // 파일 수만큼 나가는 저장소 확인도 동시 처리 한도 안에서 하도록 슬롯부터 잡는다
        JobSubmissionArchive archive = jobSubmissionFileStorageClient.startArchive(files);
        try {
            for (JobSubmissionArchive.File file : files) {
                jobSubmissionFileStorageClient.findSize(file.key())
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_NOT_FOUND));
            }
            return archive;
        } catch (RuntimeException exception) {
            archive.close();
            throw exception;
        }
    }

    /**
     * 완료된 의뢰의 결과물을 의뢰한 사장님 또는 담당 학생에게 보여준다.
     * 작업 시작일은 일반 의뢰는 결제 승인일, 제안 의뢰는 학생이 실제로 작업을 시작한 날이다.
     */
    @Transactional(readOnly = true)
    public JobResultResult getJobResult(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        JobResultData data = jobService.getJobResult(toJobResultCommand(user, jobId));

        Student student = studentService.getStudentProfile(data.getJob().getSelectedStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        ApprovedPaymentData payment = paymentService.getPaidPayment(jobId);
        LocalDateTime startedAt = data.getJob().getStartedAt();
        return JobResultResult.of(data, studentUser, startedAt != null
                ? startedAt.toLocalDate()
                : LocalDate.ofInstant(payment.approvedAt(), ZoneId.systemDefault()));
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
        Student student = getSubmittingStudent(userService.getActiveUser(command.getUsername()));
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
     * 매칭된 학생의 첫 초안 제출. 저장에 성공하면 사장님에게 초안 도착 알림을 발행한다.
     * 파일 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 저장과 알림 준비만 트랜잭션으로 묶는다.
     */
    public JobSubmissionCreateResult submitDraft(CreateJobSubmissionCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Student student = getSubmittingStudent(user);
        jobService.validateDraftSubmittable(command.getJobId(), student.getId());
        Map<String, Long> fileSizes = findUploadedFileSizes(command, student.getId());

        return transactionTemplate.execute(status -> {
            JobSubmission submission = jobService.submitDraft(command, student.getId(), fileSizes);
            Job job = getJob(submission.getJobId());
            Owner owner = ownerService.getOwnerProfileById(job.getOwnerProfileId());
            eventPublisher.publishEvent(NotificationEventFactory.jobDraftSubmitted(
                    owner.getUserId(), submission.getId(), job.getId(), job.getTitle(), user.getName()));
            return JobSubmissionCreateResult.from(submission);
        });
    }

    /**
     * 매칭된 학생의 수정안 제출. 수정 번호는 서버가 정한다. 저장한 수정안마다 사장님에게 수정안 도착 알림을 발행한다.
     * 파일 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 저장과 알림 준비만 트랜잭션으로 묶는다.
     */
    public JobSubmissionCreateResult submitRevision(CreateJobSubmissionCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Student student = getSubmittingStudent(user);
        jobService.validateRevisionSubmittable(command.getJobId(), student.getId());
        Map<String, Long> fileSizes = findUploadedFileSizes(command, student.getId());

        return transactionTemplate.execute(status -> {
            JobSubmission submission = jobService.submitRevision(command, student.getId(), fileSizes);
            Job job = getJob(submission.getJobId());
            Owner owner = ownerService.getOwnerProfileById(job.getOwnerProfileId());
            eventPublisher.publishEvent(NotificationEventFactory.jobRevisionSubmitted(
                    owner.getUserId(), submission.getId(), job.getId(), job.getTitle(), user.getName()));
            return JobSubmissionCreateResult.from(submission);
        });
    }

    /**
     * 사장님 본인 의뢰의 검토 대기 제출물에 수정 요청 내용과 참고 사진을 남기고 담당 학생에게 수정 요청 알림을 발행한다.
     * 사진 저장소 확인이 의뢰 행 잠금과 DB 커넥션을 붙잡지 않도록 저장과 알림 준비만 트랜잭션으로 묶는다.
     */
    public void requestRevision(RequestJobSubmissionRevisionCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Owner owner = ownerService.getOwnerProfile(user.getId());
        jobService.validateRevisionRequestable(command.getJobId(), command.getSubmissionId(), owner.getId());
        validateUploadedImages(command.getReferenceImageUrls(), user.getId());

        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobService.requestRevision(command, owner.getId());
            Student student = studentService.getStudentProfile(job.getSelectedStudentProfileId());
            eventPublisher.publishEvent(NotificationEventFactory.jobRevisionRequested(
                    student.getUserId(), command.getSubmissionId(), job.getId(), job.getTitle(),
                    owner.getStoreName()));
        });
    }

    /**
     * 사장님 본인 의뢰의 검토 대기 제출물을 최종 결과로 수락하고 의뢰를 종료한다.
     * 사장님에게 후기 요청 알림을, 결제 완료 기록이 있으면 담당 학생에게 정산 내역 알림을 발행한다.
     */
    @Transactional
    public void completeSubmission(String username, Long jobId, Long submissionId) {
        User user = userService.getActiveUser(username);
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Job job = jobService.completeSubmission(CompleteJobSubmissionCommand.of(jobId, submissionId, owner.getId()));

        Student student = studentService.getStudentProfile(job.getSelectedStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        eventPublisher.publishEvent(NotificationEventFactory.jobReviewRequested(
                owner.getUserId(), job.getId(), job.getTitle(), studentUser.getName()));
        paymentService.findPaidPayment(job.getId()).ifPresent(payment -> eventPublisher.publishEvent(
                NotificationEventFactory.paymentSettled(
                        student.getUserId(), payment.paymentId(), job.getId(), job.getTitle(), payment.amount())));
    }

    /**
     * 사장님 본인 의뢰를 취소한다. 결제 후 진행 중이던 의뢰는 학생 보상금을 뺀 금액을 환불 처리한다.
     * 모집 중 취소는 대기 중 지원자 전체에게, 진행 중 취소는 담당 학생과 환불 기록을 받는 사장님에게 알림을 발행한다.
     */
    @Transactional
    public JobCancelResult cancelJob(CancelJobCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Owner owner = ownerService.getOwnerProfile(user.getId());
        CancelledJobData cancelled = jobService.cancelJob(command, owner.getId());
        Job job = cancelled.getJob();
        if (!cancelled.isPaid()) {
            publishRecruitmentCancelled(job, owner);
            return JobCancelResult.of(job, null);
        }

        RefundedPaymentData refund = paymentService.refundOnCancel(command.getJobId());
        Student student = studentService.getStudentProfile(job.getSelectedStudentProfileId());
        eventPublisher.publishEvent(NotificationEventFactory.jobCancelledByOwner(
                student.getUserId(), job.getId(), chatRoomService.getOrCreate(job.getId()).getId(), job.getTitle(),
                owner.getStoreName()));
        eventPublisher.publishEvent(NotificationEventFactory.paymentRefunded(
                owner.getUserId(), refund.paymentId(), job.getTitle(), refund.refundAmount()));
        return JobCancelResult.of(job, refund);
    }

    /** 대기 중(PENDING) 지원자 전체에게 모집 취소를 알린다. 지원서 상태는 바꾸지 않는다. */
    private void publishRecruitmentCancelled(Job job, Owner owner) {
        List<Long> studentProfileIds = jobService.getPendingApplications(job.getId()).stream()
                .map(JobApplication::getStudentProfileId)
                .distinct()
                .toList();
        if (studentProfileIds.isEmpty()) {
            return;
        }
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(studentProfileIds);
        for (Long studentProfileId : studentProfileIds) {
            eventPublisher.publishEvent(NotificationEventFactory.jobRecruitmentCancelled(
                    studentsById.get(studentProfileId).getUserId(), job.getId(), job.getTitle(),
                    owner.getStoreName()));
        }
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

    /**
     * 학생 본인이 모집 중 의뢰에 지원한다. 학생이 아니거나 학생 프로필이 없으면 의뢰를 조회하기 전에 거부한다.
     * 저장에 성공하면 의뢰한 사장님에게 새 지원 알림을 발행한다.
     * 사용자 확인이 의뢰 행 잠금을 붙잡지 않도록 저장과 알림 준비만 트랜잭션으로 묶는다.
     */
    public JobApplicationCreateResult createJobApplication(CreateJobApplicationCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_STUDENT_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_APPLICATION_STUDENT_REQUIRED));
        return transactionTemplate.execute(status -> {
            JobApplication application = jobService.createJobApplication(
                    command, student.getId(), user.getDemoSessionId());
            Job job = getJob(application.getJobId());
            Owner owner = ownerService.getOwnerProfileById(job.getOwnerProfileId());
            eventPublisher.publishEvent(NotificationEventFactory.jobApplicationReceived(
                    owner.getUserId(), application.getId(), job.getId(), job.getTitle(), user.getName()));
            return JobApplicationCreateResult.from(application);
        });
    }

    /**
     * 사장님 본인의 모집 중 의뢰에 지원한 대기 중 지원자 전체를 조회한다.
     * 학생·사용자·특기·평균 별점·완료 의뢰 수는 지원자 수와 무관하게 한 번씩만 조회하고, 지원자가 없으면 생략한다.
     * 지원자가 참조하는 학생·사용자·특기가 없으면 해당 지원자를 빼지 않고 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public JobApplicationListResult getJobApplications(String username, Long jobId, JobApplicationSort sort) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_LIST_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        JobApplicationListData data = jobService.getJobApplications(
                GetJobApplicationsCommand.of(jobId, owner.getId()));

        List<JobApplication> applications = data.getApplications();
        if (applications.isEmpty()) {
            Map<Long, SpecialtyDetail> specialtiesById =
                    specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
            return JobApplicationListResult.of(
                    JobApplicationJobResult.of(data.getJob(), groupSpecialties(data.getSpecialtyIds(), specialtiesById)),
                    List.of());
        }

        List<Long> studentProfileIds = applications.stream()
                .map(JobApplication::getStudentProfileId)
                .distinct()
                .toList();
        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(studentProfileIds);
        Map<String, User> usersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());
        Map<Long, List<Long>> specialtyIdsByStudent =
                specialtyService.getSpecialtyIdsByStudentProfileIds(studentProfileIds);
        Map<Long, BigDecimal> averageRatings = reviewService.getAverageRatings(studentProfileIds);
        Map<Long, Long> completedJobCounts = jobService.countClosedJobsByStudentProfileIds(studentProfileIds);

        Set<Long> specialtyIds = Stream.concat(
                        data.getSpecialtyIds().stream(),
                        specialtyIdsByStudent.values().stream().flatMap(List::stream))
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        List<JobApplicantResult> applicants = applications.stream()
                .map(application -> {
                    Student student = studentsById.get(application.getStudentProfileId());
                    return JobApplicantResult.of(
                            application,
                            student,
                            usersById.get(student.getUserId()),
                            averageRatings.get(student.getId()),
                            completedJobCounts.get(student.getId()),
                            groupSpecialties(
                                    specialtyIdsByStudent.getOrDefault(student.getId(), List.of()), specialtiesById));
                })
                .sorted(applicantOrder(sort))
                .toList();
        return JobApplicationListResult.of(
                JobApplicationJobResult.of(data.getJob(), groupSpecialties(data.getSpecialtyIds(), specialtiesById)),
                applicants);
    }

    /** 모든 정렬의 마지막 기준은 최신 지원순(지원 시각 내림차순 → 지원서 ID 내림차순)이고 지원 시각이 없으면 뒤에 둔다. */
    private static Comparator<JobApplicantResult> applicantOrder(JobApplicationSort sort) {
        Comparator<JobApplicantResult> latest = Comparator
                .comparing(JobApplicantResult::getAppliedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(JobApplicantResult::getJobApplicationId, Comparator.reverseOrder());
        return switch (sort) {
            case LATEST -> latest;
            case RATING -> Comparator
                    .comparing(JobApplicantResult::getAverageRating, Comparator.reverseOrder())
                    .thenComparing(latest);
            case COMPLETED -> Comparator
                    .comparing(JobApplicantResult::getCompletedJobCount, Comparator.reverseOrder())
                    .thenComparing(latest);
        };
    }

    /**
     * 사장님 본인 의뢰의 지원자(모집 중) 또는 선정 학생(매칭·완료 후)의 학생 정보와 활동 이력을 조회한다.
     * 의뢰·지원서 검증을 통과한 뒤에만 학생 정보를 조회한다. 리뷰는 학생이 모든 사장님에게 받은 전체를 내린다.
     * 리뷰의 의뢰·매장은 리뷰 수와 무관하게 한 번씩만 조회하고, 참조하는 데이터가 없으면 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public JobApplicantProfileResult getJobApplicantProfile(String username, Long jobId, Long jobApplicationId) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_PROFILE_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        JobApplication application = jobService.getProfileViewableApplication(
                GetJobApplicantProfileCommand.of(jobId, jobApplicationId, owner.getId()));

        Student student = studentService.getStudentProfile(application.getStudentProfileId());
        User studentUser = userService.getUser(student.getUserId());
        List<Long> specialtyIds = specialtyService.getSpecialtyIdsByStudentProfileIds(List.of(student.getId()))
                .getOrDefault(student.getId(), List.of());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        List<Review> reviews = reviewService.getStudentReviews(student.getId());
        Map<Long, Job> jobsById = jobService.getJobsByIds(reviews.stream()
                .map(Review::getJobId)
                .toList());
        Map<Long, String> storeNames = ownerService.getStoreNames(jobsById.values().stream()
                .map(Job::getOwnerProfileId)
                .collect(Collectors.toSet()));

        return JobApplicantProfileResult.of(
                student,
                studentUser,
                proposalService.countProposals(student.getId()),
                jobService.countClosedJobs(student.getId()),
                groupSpecialties(specialtyIds, specialtiesById),
                certificateService.getStudentCertificates(student.getId()),
                reviews.stream()
                        .map(review -> {
                            Job job = jobsById.get(review.getJobId());
                            return ApplicantReviewResult.of(
                                    review, job.getTitle(), storeNames.get(job.getOwnerProfileId()));
                        })
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
        Map<String, User> studentUsersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());
        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);

        return MatchedJobListResult.of(jobs.stream()
                .map(job -> {
                    Student student = studentsById.get(job.getJob().getSelectedStudentProfileId());
                    return MatchedJobResult.of(
                            job,
                            student,
                            studentUsersById.get(student.getUserId()),
                            groupSpecialties(job.getSpecialtyIds(), specialtiesById));
                })
                .toList());
    }

    /** 진행중 의뢰 목록을 학생용 응답으로 내려야 하는 사용자인지 확인한다. */
    @Transactional(readOnly = true)
    public boolean isStudent(String username) {
        return userService.getActiveUser(username).getRole() == UserRole.STUDENT;
    }

    /** 학생 본인과 매칭된 진행 중 의뢰를 최신 제출물 상태, 의뢰한 사장님의 현재 매장 이름과 함께 조회한다. */
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
        Map<Long, String> storeNames = ownerService.getStoreNames(jobs.stream()
                .map(job -> job.getJob().getOwnerProfileId())
                .collect(Collectors.toSet()));

        return StudentMatchedJobListResult.of(jobs.stream()
                .map(job -> StudentMatchedJobResult.of(
                        job,
                        storeNames.get(job.getJob().getOwnerProfileId()),
                        groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
                .toList());
    }

    /**
     * 학생 본인의 모집 중 대기 지원과 미선정 지원 이력을 본인 지원서 원문, 현재 매장 이름과 함께 최신 지원순으로 조회한다.
     * 학생이 아니면 지원서를 조회하기 전에 거부하고, 학생 계정에 학생 프로필이 없으면 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public StudentAppliedJobListResult getStudentAppliedJobs(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_APPLICATION_LIST_STUDENT_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        List<StudentAppliedJobData> jobs = jobService.getStudentAppliedJobs(
                GetStudentAppliedJobsCommand.of(student.getId(), user.getDemoSessionId()));

        if (jobs.isEmpty()) {
            return StudentAppliedJobListResult.of(List.of());
        }

        Set<Long> specialtyIds = jobs.stream()
                .flatMap(job -> job.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);
        Map<Long, String> storeNames = ownerService.getStoreNames(jobs.stream()
                .map(job -> job.getJob().getOwnerProfileId())
                .collect(Collectors.toSet()));

        return StudentAppliedJobListResult.of(jobs.stream()
                .map(job -> StudentAppliedJobResult.of(
                        job,
                        storeNames.get(job.getJob().getOwnerProfileId()),
                        groupSpecialties(job.getSpecialtyIds(), specialtiesById)))
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
        Set<Long> reviewedJobIds = reviewService.getReviewedJobIds(jobs.stream()
                .map(job -> job.getJob().getId())
                .toList());

        return ClosedJobListResult.of(jobs.stream()
                .map(job -> {
                    Long studentProfileId = job.getJob().getSelectedStudentProfileId();
                    Student student = studentProfileId == null ? null : studentsById.get(studentProfileId);
                    return ClosedJobResult.of(
                            job,
                            student,
                            student == null ? null : workersById.get(student.getUserId()),
                            groupSpecialties(job.getSpecialtyIds(), specialtiesById),
                            reviewedJobIds.contains(job.getJob().getId()));
                })
                .toList());
    }

    /** 학생 프로필이 없는 사용자(사장님 포함)는 작업물을 제출할 수 없다. */
    private Student getSubmittingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FORBIDDEN));
    }

    private Job getJob(Long jobId) {
        return jobService.getJobsByIds(List.of(jobId)).get(jobId);
    }

    /**
     * 모든 URL이 이 의뢰·학생용으로 발급한 경로인지 먼저 확인한 뒤 실제 업로드 여부를 확인한다.
     * @return 파일 URL별 바이트 크기
     */
    private Map<String, Long> findUploadedFileSizes(CreateJobSubmissionCommand command, Long studentProfileId) {
        Map<String, String> keysByFileUrl = new LinkedHashMap<>();
        for (String fileUrl : command.getFileUrls()) {
            keysByFileUrl.put(fileUrl,
                    jobSubmissionFileStorageClient.findKey(fileUrl, command.getJobId(), studentProfileId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_URL_INVALID)));
        }
        Map<String, Long> fileSizes = new LinkedHashMap<>();
        keysByFileUrl.forEach((fileUrl, key) -> fileSizes.put(fileUrl,
                jobSubmissionFileStorageClient.findSize(key)
                        .orElseThrow(() -> new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_NOT_UPLOADED))));
        return fileSizes;
    }

    // 응답 DTO가 UTC로 해석해 한국 시각으로 바꾸므로 JVM 기본 시간대와 무관하게 UTC로 내린다
    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
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
