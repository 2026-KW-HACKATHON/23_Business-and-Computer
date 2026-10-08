package com.gakkum.backend.application.review.facade;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobQueryDto.ReviewedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.dto.ReviewQueryDto.ReviewCreateResult;
import com.gakkum.backend.domain.review.dto.ReviewQueryDto.StudentReviewResult;
import com.gakkum.backend.domain.review.entity.Review;
import com.gakkum.backend.domain.review.service.ReviewService;
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
public class ReviewFacade {

    private final UserService userService;
    private final OwnerService ownerService;
    private final StudentService studentService;
    private final JobService jobService;
    private final ReviewService reviewService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 사장님 본인의 완료된 의뢰에 담당 학생 리뷰를 한 번 작성한다. 리뷰 대상은 의뢰에 선택된 학생이다.
     * 의뢰 행 잠금과 리뷰 저장을 한 트랜잭션으로 묶어 같은 의뢰의 동시 작성을 순서대로 처리한다.
     * 저장에 성공하면 담당 학생에게 후기 도착 알림을 발행한다.
     */
    @Transactional
    public ReviewCreateResult createReview(CreateReviewCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Job job = jobService.getReviewableJobForUpdate(command.getJobId(), owner.getId());

        Review review = reviewService.createReview(command, owner.getId(), job.getSelectedStudentProfileId());
        Student student = studentService.getStudentProfile(job.getSelectedStudentProfileId());
        eventPublisher.publishEvent(NotificationEventFactory.jobReviewReceived(
                student.getUserId(), review.getId(), job.getId(), job.getTitle(), owner.getStoreName()));
        return ReviewCreateResult.from(review);
    }

    /**
     * 의뢰 리뷰를 조회한다. 사장님은 본인이 작성한 리뷰만, 학생은 본인이 받은 리뷰만 볼 수 있고
     * 그 밖의 리뷰는 없는 리뷰와 같은 404로 거부한다. 매장 이름은 리뷰 작성 당시가 아닌 현재 사장님 프로필 값을 쓴다.
     * 사장님 프로필이 없는 사장님과, 학생 프로필이 없거나 역할이 정해지지 않은 사용자는 403으로 거부한다.
     */
    @Transactional(readOnly = true)
    public StudentReviewResult getJobReview(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        Review review = user.getRole() == UserRole.OWNER
                ? reviewService.getOwnerReview(jobId, ownerService.getOwnerProfile(user.getId()).getId())
                : reviewService.getStudentReview(jobId, getStudentProfileId(user));

        ReviewedJobData data = jobService.getReviewedJob(review.getJobId());
        Owner owner = ownerService.getOwnerProfileById(data.getJob().getOwnerProfileId());
        return StudentReviewResult.of(
                review, data.getApprovedSubmission().getId(), data.getJob().getTitle(), owner.getStoreName());
    }

    private Long getStudentProfileId(User user) {
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.REVIEW_STUDENT_REQUIRED);
        }
        return studentService.findStudentProfileByUserId(user.getId())
                .map(Student::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_STUDENT_REQUIRED));
    }
}
