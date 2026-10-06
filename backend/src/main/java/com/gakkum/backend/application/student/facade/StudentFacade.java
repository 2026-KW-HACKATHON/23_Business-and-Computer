package com.gakkum.backend.application.student.facade;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest.CertificateRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.UpdateStudentCertificateCommand;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.UpdateStudentMeCommand;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentMeResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentReceivedReviewResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentSpecialtyCategoryResult;
import com.gakkum.backend.domain.student.dto.StudentQueryDto.StudentSpecialtyResult;
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
public class StudentFacade {

    // 내 정보에 함께 내리는 최신 리뷰·정산 완료 내역의 최대 개수
    private static final int ME_PREVIEW_SIZE = 3;

    private final UserService userService;
    private final StudentService studentService;
    private final SpecialtyService specialtyService;
    private final CertificateService certificateService;
    private final JwtService jwtService;
    private final AuthService authService;
    private final SpecialtyCategoryService specialtyCategoryService;
    private final ProposalService proposalService;
    private final JobService jobService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;
    private final OwnerService ownerService;

    @Transactional
    public StudentRegistrationResponse register(String username, StudentRegistrationRequest request) {
        String normalizedEmail = request.getNormalizedEmail();
        User user = userService.validateStudentRegistration(username, normalizedEmail);
        studentService.validateStudentNumberAvailable(request.getStudentNumber());
        List<Long> specialtyIds = request.getNormalizedSpecialtyIds();
        specialtyService.validateSpecialtyIds(specialtyIds);
        authService.consumeVerifiedStudentEmail(user.getId(), normalizedEmail);

        userService.completeStudentRegistration(user, request.getStudentName(), normalizedEmail);
        Student student = studentService.createStudentProfile(request.toCommand(user.getId()));

        for (Long specialtyId : specialtyIds) {
            specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
        }

        for (CertificateRequest certificate : request.getNormalizedCertificates()) {
            certificateService.addStudentCertificate(AddStudentCertificateCommand.of(
                    student.getId(),
                    certificate.getNormalizedCertificateName(),
                    certificate.getAcquiredYear()
            ));
        }

        String accessToken = jwtService.issueAccessToken(username, UserRole.STUDENT);
        String refreshToken = jwtService.replaceRefreshToken(username, UserRole.STUDENT);
        return StudentRegistrationResponse.of(accessToken, refreshToken);
    }

    /**
     * 학생 본인의 정보와 활동 요약. 리뷰 수와 평균 별점은 받은 전체 리뷰 기준이고, 리뷰와 정산 완료 내역은 최신 세 개만 내린다.
     * 리뷰와 정산에 필요한 의뢰·매장은 합쳐서 한 번씩만 조회하고, 리뷰 의뢰의 소분류와 학생 특기의 분류 상세도 한 번에 조회한다.
     * 매장 이름과 분류는 조회 시점의 현재 값이다. 학생 프로필이나 참조하는 데이터가 없으면 500으로 거부한다.
     */
    @Transactional(readOnly = true)
    public StudentMeResult getMe(String username) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.STUDENT_ME_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        List<Review> reviews = reviewService.getLatestStudentReviews(student.getId(), ME_PREVIEW_SIZE);
        List<SettlementHistoryData> payments = paymentService.getLatestSettledPayments(student.getId(), ME_PREVIEW_SIZE);

        Set<Long> reviewJobIds = reviews.stream()
                .map(Review::getJobId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> jobIds = new LinkedHashSet<>(reviewJobIds);
        payments.forEach(payment -> jobIds.add(payment.getJobId()));
        Map<Long, Job> jobsById = jobService.getJobsByIds(jobIds);
        Map<Long, String> storeNamesByOwnerProfileId = ownerService.getStoreNames(jobsById.values().stream()
                .map(Job::getOwnerProfileId)
                .collect(Collectors.toSet()));

        List<Long> specialtyIds = specialtyService.getSpecialtyIdsByStudentProfileIds(List.of(student.getId()))
                .getOrDefault(student.getId(), List.of());
        Map<Long, List<Long>> specialtyIdsByJobId = jobService.getSpecialtyIdsByJobIds(reviewJobIds);
        Set<Long> allSpecialtyIds = new LinkedHashSet<>(specialtyIds);
        specialtyIdsByJobId.values().forEach(allSpecialtyIds::addAll);
        Map<Long, SpecialtyDetail> specialtiesById = specialtyCategoryService.getSpecialtyDetails(allSpecialtyIds);

        // 제안 결제는 지원서가 없다
        Map<Long, JobApplication> applicationsById = jobService.getJobApplicationsByIds(payments.stream()
                .map(SettlementHistoryData::getJobApplicationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        return StudentMeResult.of(
                student,
                user,
                admissionYear(student.getStudentNumber()),
                proposalService.countProposalsExcludingCancelled(student.getId()),
                jobService.countClosedJobs(student.getId()),
                reviewService.getAverageRating(student.getId()),
                groupSpecialties(specialtyIds, specialtiesById),
                certificateService.getStudentCertificates(student.getId()),
                reviewService.countStudentReviews(student.getId()),
                reviews.stream()
                        .map(review -> StudentReceivedReviewResult.of(
                                review,
                                storeNamesByOwnerProfileId.get(jobsById.get(review.getJobId()).getOwnerProfileId()),
                                groupSpecialties(
                                        specialtyIdsByJobId.getOrDefault(review.getJobId(), List.of()),
                                        specialtiesById)))
                        .toList(),
                payments.stream()
                        .map(payment -> settledItem(payment, jobsById.get(payment.getJobId()), student,
                                applicationsById, storeNamesByOwnerProfileId))
                        .toList());
    }

    /**
     * 학생 본인의 프로필 사진·소개·포트폴리오와 특기·자격증 목록을 요청 값으로 전체 교체한다.
     * 입력을 모두 검증한 뒤에 쓰기 시작하고, 도중에 실패하면 전부 롤백한다. 학생 프로필이 없으면 500으로 거부한다.
     */
    @Transactional
    public void updateMe(UpdateStudentMeCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.STUDENT_ME_UPDATE_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
        specialtyService.validateSpecialtyIds(command.getSpecialtyIds());
        command.getCertificates().forEach(
                certificate -> certificateService.validateAcquiredYear(certificate.getAcquiredYear()));

        studentService.updateStudentProfile(student, command);

        specialtyService.deleteStudentSpecialties(student.getId());
        for (Long specialtyId : command.getSpecialtyIds()) {
            specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
        }

        certificateService.deleteStudentCertificates(student.getId());
        for (UpdateStudentCertificateCommand certificate : command.getCertificates()) {
            certificateService.addStudentCertificate(AddStudentCertificateCommand.of(
                    student.getId(),
                    certificate.getCertificateName(),
                    certificate.getAcquiredYear()));
        }
    }

    // 정산 내역 조회와 같은 무결성 기준으로 확인한다. 금액은 저장된 결제 금액, 정산일은 의뢰 완료일이다
    private SettlementHistoryItemResult settledItem(
            SettlementHistoryData payment, Job job, Student student, Map<Long, JobApplication> applicationsById,
            Map<Long, String> storeNamesByOwnerProfileId) {
        // 예외: 결제된 의뢰에 선택된 학생이 본인이 아닌 경우
        if (!student.getId().equals(job.getSelectedStudentProfileId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (payment.getProposalId() != null) {
            // 예외: 제안 결제가 가리키는 제안이 의뢰를 만든 제안이 아닌 경우
            if (!payment.getProposalId().equals(job.getProposalId())) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } else {
            JobApplication application = applicationsById.get(payment.getJobApplicationId());
            // 예외: 본인 지원서가 아니거나, 결제가 가리키는 지원서가 다른 의뢰의 지원서인 경우
            if (application == null || !application.getStudentProfileId().equals(student.getId())
                    || !application.getJobId().equals(payment.getJobId())) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
        if (job.getCompletedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return SettlementHistoryItemResult.of(job.getId(), job.getTitle(), payment.getAmount(),
                job.getCompletedAt().toLocalDate(), storeNamesByOwnerProfileId.get(job.getOwnerProfileId()),
                SettlementHistoryStatus.SETTLED);
    }

    // 학번 열 자리의 셋째·넷째 자리가 입학연도 두 자리다: 2024402001 → "24"
    private String admissionYear(String studentNumber) {
        if (studentNumber == null || studentNumber.length() < 4) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return studentNumber.substring(2, 4);
    }

    // 대분류 ID 오름차순, 같은 대분류 안에서는 소분류 ID 오름차순으로 묶는다
    private List<StudentSpecialtyCategoryResult> groupSpecialties(
            Collection<Long> specialtyIds, Map<Long, SpecialtyDetail> specialtiesById) {
        Map<Long, List<SpecialtyDetail>> byCategory = specialtyIds.stream()
                .map(id -> {
                    SpecialtyDetail detail = specialtiesById.get(id);
                    if (detail == null) {
                        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
                    }
                    return detail;
                })
                .sorted(Comparator.comparing(SpecialtyDetail::getId))
                .collect(Collectors.groupingBy(
                        SpecialtyDetail::getCategoryId,
                        TreeMap::new,
                        Collectors.toList()));

        return byCategory.entrySet().stream()
                .map(entry -> StudentSpecialtyCategoryResult.of(
                        entry.getKey(),
                        entry.getValue().get(0).getCategoryName(),
                        entry.getValue().stream()
                                .map(detail -> StudentSpecialtyResult.of(detail.getId(), detail.getName()))
                                .toList()))
                .toList();
    }
}
