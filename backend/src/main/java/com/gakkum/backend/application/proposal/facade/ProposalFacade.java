package com.gakkum.backend.application.proposal.facade;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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

import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetMyProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.GetReceivedProposalsCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.StartProposalJobCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalAgreementResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCancelResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobDeclineResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalJobStartResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ExploreProposalData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.MyProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalCreateResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailData;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalDetailResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalListResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ReceivedProposalResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyCategoryResult;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.SpecialtyResult;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.entity.ProposalStatus;
import com.gakkum.backend.domain.proposal.service.ProposalService;
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
public class ProposalFacade {

    // 결제 전 예상 마감일을 계산하는 기준 시간대. 결제 승인 시 마감일을 확정하는 시간대와 같다
    private static final ZoneId DEADLINE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserService userService;
    private final StudentService studentService;
    private final OwnerService ownerService;
    private final SpecialtyService specialtyService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final MediaService mediaService;
    private final ProposalService proposalService;
    private final ReviewService reviewService;
    private final JobService jobService;
    private final PaymentService paymentService;
    private final ChatRoomService chatRoomService;
    private final Clock clock;

    /**
     * 학생의 제안 전송. 역할·대상 사장님·소분류·사진을 검증한 뒤 저장한다.
     * 사진 저장소 확인이 DB 트랜잭션과 커넥션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않는다.
     */
    public ProposalCreateResult createProposal(CreateProposalCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Student student = getProposingStudent(user);
        ownerService.validateOwnerProfileExists(command.getOwnerProfileId(), user.getDemoSessionId());
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());
        validateUploadedImages(command.getReferenceImageUrls(), user.getId());

        Proposal proposal = proposalService.createProposal(command, student.getId(), user.getDemoSessionId());
        return ProposalCreateResult.from(proposal);
    }

    /**
     * 제안 상세. 활성 사용자라면 역할과 무관하게 취소되지 않은 모든 제안을 볼 수 있고 없는 제안은 404다.
     * 격리 범위(demoSessionId)가 조회자와 다른 제안도 없는 제안과 같은 404로 거부한다.
     * 취소된 제안은 제안한 학생 본인에게만 내리고, 받은 사장님을 포함한 다른 사용자에게는 404로 거부한다.
     * 결제 전에는 한국 날짜 기준 오늘에 제안 기간을 더한 예상 마감일을 함께 내린다.
     * 제안을 찾은 뒤에만 매장(현재 이름·주소)·학생 정보·학생 통계(평균 별점·완료 의뢰 수)·특기·본인 공감 여부를 조회한다.
     */
    @Transactional(readOnly = true)
    public ProposalDetailResult getProposalDetail(String username, Long proposalId) {
        User viewer = userService.getActiveUser(username);
        ProposalDetailData data = proposalService.getProposalDetail(proposalId);
        if (!Objects.equals(data.getProposal().getDemoSessionId(), viewer.getDemoSessionId())) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }

        Student student = studentService.getStudentProfile(data.getProposal().getStudentProfileId());
        if (data.getProposal().getStatus() == ProposalStatus.CANCELLED
                && !viewer.getId().equals(student.getUserId())) {
            throw new BusinessException(ErrorCode.PROPOSAL_NOT_FOUND);
        }
        Owner owner = ownerService.getOwnerProfileById(data.getProposal().getOwnerProfileId());
        User studentUser = userService.getUser(student.getUserId());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(data.getSpecialtyIds());
        BigDecimal averageRating = reviewService.getAverageRating(student.getId());
        long completedJobCount = jobService.countClosedJobs(student.getId());

        // 결제로 확정된 작업 조건(사장님의 한마디 포함)은 제안을 받은 사장님과 제안한 학생에게만 내린다
        Job job = jobService.findJobByProposalId(proposalId).orElse(null);
        boolean party = viewer.getId().equals(owner.getUserId()) || viewer.getId().equals(student.getUserId());
        ProposalAgreementResult agreement = job != null && party
                ? ProposalAgreementResult.of(job, paymentService.getProposalPaidAt(proposalId))
                : null;
        return ProposalDetailResult.of(data.getProposal(), owner.getStoreName(), owner.getStoreAddress(),
                student, studentUser, averageRating, completedJobCount,
                groupSpecialties(data.getSpecialtyIds(), specialtiesById), isLikedByViewer(viewer, proposalId),
                LocalDate.now(clock.withZone(DEADLINE_ZONE)), job == null ? null : job.getId(), agreement);
    }

    /**
     * 학생이 제안에 공감한다. 본인 제안과 취소되지 않은 모든 상태의 제안에 공감할 수 있다.
     * 학생 검증과 공감 기록·공감 수 변경을 한 트랜잭션으로 처리하고, 이미 공감한 제안의 재요청은 공감 수를 바꾸지 않는다.
     */
    @Transactional
    public ProposalLikeResult likeProposal(String username, Long proposalId) {
        User user = userService.getActiveUser(username);
        Student student = getLikingStudent(user);
        Proposal proposal = proposalService.likeProposal(proposalId, student.getId(), user.getDemoSessionId());
        return ProposalLikeResult.of(proposal, true);
    }

    /**
     * 학생이 제안의 공감을 취소한다. 공감하지 않은 제안의 재요청도 성공하고 공감 수를 바꾸지 않는다.
     * 학생 검증과 공감 기록·공감 수 변경을 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public ProposalLikeResult unlikeProposal(String username, Long proposalId) {
        User user = userService.getActiveUser(username);
        Student student = getLikingStudent(user);
        Proposal proposal = proposalService.unlikeProposal(proposalId, student.getId(), user.getDemoSessionId());
        return ProposalLikeResult.of(proposal, false);
    }

    /**
     * 제안한 학생이 결제 전(PENDING) 제안을 취소한다. 이미 취소한 본인 제안의 재요청은 아무것도 바꾸지 않고 성공한다.
     * 제안 행을 잠근 채 격리 범위 → 작성자 → 상태 → 결제 대기 주문 순서로 확인하고,
     * 상태 전환·공감 기록 삭제·공감 수 초기화를 한 트랜잭션으로 처리한다. 결제·의뢰 데이터는 바꾸지 않는다.
     * 결제 준비도 같은 제안 행을 잠그므로 취소와 결제 준비는 순서대로 처리되고, 결제 대기 주문이 남아 있으면 409다.
     */
    @Transactional
    public ProposalCancelResult cancelProposal(String username, Long proposalId) {
        User user = userService.getActiveUser(username);
        Student student = getCancellingStudent(user);
        Proposal proposal = proposalService.getCancellableProposalForUpdate(
                proposalId, student.getId(), user.getDemoSessionId());
        if (proposal.getStatus() != ProposalStatus.CANCELLED) {
            if (paymentService.findPendingProposalPayment(proposalId).isPresent()) {
                throw new BusinessException(ErrorCode.PROPOSAL_CANCEL_PAYMENT_PENDING);
            }
            proposalService.cancelProposal(proposal);
        }
        return ProposalCancelResult.from(proposal);
    }

    /**
     * 제안한 학생이 결제된 제안 의뢰의 작업을 시작한다. 제출과 함께 확정 작업 조건(마감일·패널티)에 동의한 것으로 본다.
     * 제안의 수락 전환, 의뢰의 진행 중 전환, 시작 시각 기록, 채팅방 생성을 한 트랜잭션으로 처리한다.
     * 잠금 순서는 결제 승인과 같이 제안 → 의뢰다. 의뢰의 제안 ID를 먼저 읽고, 잠근 뒤 연결 관계를 다시 확인한다.
     * 이미 시작한 의뢰의 재요청은 기존 시작 시각과 채팅방을 그대로 반환한다.
     */
    @Transactional
    public ProposalJobStartResult startProposalJob(StartProposalJobCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_START_FORBIDDEN);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_START_FORBIDDEN));

        Long proposalId = jobService.getStartableProposalId(command.getJobId(), student.getId());
        Proposal proposal = proposalService.getStartableProposalForUpdate(proposalId, student.getId());
        Job job = jobService.startJob(command.getJobId(), proposalId, student.getId());
        proposal.accept();
        ChatRoom chatRoom = chatRoomService.getOrCreate(job.getId());
        return ProposalJobStartResult.of(job, proposal, chatRoom.getId());
    }

    /**
     * 제안한 학생이 결제된 제안 의뢰서를 작업 시작 전에 거절한다. 거절 사유는 받지 않는다.
     * 제안의 거절 전환, 의뢰의 취소 전환, 결제의 전액 환불 기록(학생 보상금 0원)을 한 트랜잭션으로 처리하고 채팅방은 만들지 않는다.
     * 잠금 순서는 제안 → 의뢰 → 결제다. 의뢰의 제안 ID를 먼저 읽고, 잠근 뒤 연결 관계·작성자·상태를 다시 확인한다.
     * 이미 시작·거절·종료된 의뢰의 재요청은 409로 거부해 환불을 다시 처리하지 않는다.
     * 결제 완료 주문이 없거나 주문의 제안·결제한 사장님이 의뢰와 맞지 않으면 500으로 전체 변경을 되돌린다.
     */
    @Transactional
    public ProposalJobDeclineResult declineProposalJob(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.JOB_DECLINE_FORBIDDEN);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.JOB_DECLINE_FORBIDDEN));

        Long proposalId = jobService.getDeclinableProposalId(jobId, student.getId(), user.getDemoSessionId());
        Proposal proposal = proposalService.getDeclinableProposalForUpdate(proposalId, student.getId());
        Job job = jobService.declineJob(jobId, proposalId, student.getId());
        proposal.reject();
        // 환불 대상 결제가 이 의뢰의 사장님이 결제한 주문인지 확인하도록 의뢰한 사장님을 넘긴다
        Owner owner = ownerService.getOwnerProfileById(job.getOwnerProfileId());
        RefundedPaymentData refund = paymentService.refundOnDecline(jobId, proposalId, owner.getUserId());
        return ProposalJobDeclineResult.of(job, proposal, refund);
    }

    /**
     * 내가 보낸 제안 목록. 활성 학생만 조회할 수 있다.
     * 제안이 있으면 사장님 프로필과 소분류를 중복 없이 모아 한 번씩만 조회한다.
     */
    @Transactional(readOnly = true)
    public MyProposalListResult getMyProposals(String username) {
        User user = userService.getActiveUser(username);
        Student student = getListingStudent(user);
        List<ExploreProposalData> proposals =
                proposalService.getMyProposals(GetMyProposalsCommand.of(student.getId()));
        if (proposals.isEmpty()) {
            return MyProposalListResult.of(List.of());
        }

        Set<Long> ownerProfileIds = proposals.stream()
                .map(data -> data.getProposal().getOwnerProfileId())
                .collect(Collectors.toSet());
        Set<Long> specialtyIds = proposals.stream()
                .flatMap(data -> data.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, Owner> ownersById = ownerService.getOwnerProfilesByIds(ownerProfileIds);
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);
        Map<Long, Job> jobsByProposalId = jobService.getJobsByProposalIds(proposals.stream()
                .map(data -> data.getProposal().getId())
                .toList());

        return MyProposalListResult.of(proposals.stream()
                .map(data -> MyProposalResult.of(
                        data.getProposal(),
                        ownersById.get(data.getProposal().getOwnerProfileId()),
                        groupSpecialties(data.getSpecialtyIds(), specialtiesById),
                        jobsByProposalId.get(data.getProposal().getId())))
                .toList());
    }

    /**
     * 받은 제안 목록. 활성 사장님만 본인 프로필로 조회할 수 있다.
     * 제안이 있으면 학생 프로필·사용자·소분류·연결 의뢰를 중복 없이 모아 한 번씩만 조회한다. 참조 누락은 각 일괄 조회가 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public ReceivedProposalListResult getReceivedProposals(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.OWNER) {
            throw new BusinessException(ErrorCode.PROPOSAL_LIST_OWNER_REQUIRED);
        }
        Owner owner = ownerService.getOwnerProfile(user.getId());
        List<ExploreProposalData> proposals =
                proposalService.getReceivedProposals(GetReceivedProposalsCommand.of(owner.getId()));
        if (proposals.isEmpty()) {
            return ReceivedProposalListResult.of(List.of());
        }

        Map<Long, Student> studentsById = studentService.getStudentProfilesByIds(proposals.stream()
                .map(data -> data.getProposal().getStudentProfileId())
                .distinct()
                .toList());
        Map<String, User> studentUsersById = userService.getUsersByIds(studentsById.values().stream()
                .map(Student::getUserId)
                .distinct()
                .toList());
        Set<Long> specialtyIds = proposals.stream()
                .flatMap(data -> data.getSpecialtyIds().stream())
                .collect(Collectors.toSet());
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(specialtyIds);
        // 결제로 만들어진 의뢰. 결제 전 제안은 키가 없다
        Map<Long, Job> jobsByProposalId = jobService.getJobsByProposalIds(proposals.stream()
                .map(data -> data.getProposal().getId())
                .toList());

        return ReceivedProposalListResult.of(proposals.stream()
                .map(data -> {
                    Student student = studentsById.get(data.getProposal().getStudentProfileId());
                    return ReceivedProposalResult.of(
                            data.getProposal(), student, studentUsersById.get(student.getUserId()),
                            groupSpecialties(data.getSpecialtyIds(), specialtiesById),
                            jobsByProposalId.get(data.getProposal().getId()));
                })
                .toList());
    }

    private Student getListingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_LIST_STUDENT_REQUIRED));
    }

    /** 학생 프로필이 없는 사용자(사장님·가입 대기 사용자 포함)는 공감을 켜거나 끌 수 없다. */
    private Student getLikingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_LIKE_STUDENT_REQUIRED));
    }

    /** 학생 프로필이 없는 사용자(사장님·가입 대기 사용자 포함)는 제안을 취소할 수 없다. */
    private Student getCancellingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_CANCEL_FORBIDDEN);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_CANCEL_FORBIDDEN));
    }

    // 학생이 아니거나 학생 프로필이 없는 조회자는 공감 기록이 없는 것으로 본다
    private boolean isLikedByViewer(User viewer, Long proposalId) {
        if (viewer.getRole() != UserRole.STUDENT) {
            return false;
        }
        return studentService.findStudentProfileByUserId(viewer.getId())
                .map(student -> proposalService.getLikedProposalIds(student.getId(), List.of(proposalId))
                        .contains(proposalId))
                .orElse(false);
    }

    /** 학생 프로필이 없는 사용자(사장님 포함)는 제안을 보낼 수 없다. */
    private Student getProposingStudent(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.PROPOSAL_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_STUDENT_REQUIRED));
    }

    /** 모든 URL이 이 학생의 제안용으로 발급한 경로인지 먼저 확인한 뒤 실제 업로드 여부를 확인한다. */
    private void validateUploadedImages(List<String> imageUrls, String userId) {
        List<String> keys = imageUrls.stream()
                .map(imageUrl -> mediaService.findImageKey(userId, ImagePurpose.PROPOSAL, imageUrl)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PROPOSAL_IMAGE_URL_INVALID)))
                .toList();
        for (String key : keys) {
            if (!mediaService.isImageUploaded(key)) {
                throw new BusinessException(ErrorCode.PROPOSAL_IMAGE_NOT_UPLOADED);
            }
        }
    }

    // 의뢰 API와 같은 형식으로 대분류 ID 순, 대분류 안에서는 소분류 ID 순으로 묶는다
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
