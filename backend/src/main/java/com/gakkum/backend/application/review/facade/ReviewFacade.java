package com.gakkum.backend.application.review.facade;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.job.dto.JobQueryDto.ReviewedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.service.JobService;
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

    /**
     * 사장님 본인의 완료된 의뢰에 담당 학생 리뷰를 한 번 작성한다. 리뷰 대상은 의뢰에 선택된 학생이다.
     * 의뢰 행 잠금과 리뷰 저장을 한 트랜잭션으로 묶어 같은 의뢰의 동시 작성을 순서대로 처리한다.
     */
    @Transactional
    public ReviewCreateResult createReview(CreateReviewCommand command) {
        User user = userService.getActiveUser(command.getUsername());
        Owner owner = ownerService.getOwnerProfile(user.getId());
        Job job = jobService.getReviewableJobForUpdate(command.getJobId(), owner.getId());

        Review review = reviewService.createReview(command, owner.getId(), job.getSelectedStudentProfileId());
        return ReviewCreateResult.from(review);
    }

    /**
     * 담당 학생이 받은 의뢰 리뷰를 조회한다. 매장 이름은 리뷰 작성 당시가 아닌 현재 사장님 프로필 값을 쓴다.
     * 학생 프로필이 없는 사용자(사장님 포함)는 403으로 거부한다.
     */
    @Transactional(readOnly = true)
    public StudentReviewResult getStudentReview(String username, Long jobId) {
        User user = userService.getActiveUser(username);
        if (user.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.REVIEW_STUDENT_REQUIRED);
        }
        Student student = studentService.findStudentProfileByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_STUDENT_REQUIRED));

        Review review = reviewService.getStudentReview(jobId, student.getId());
        ReviewedJobData data = jobService.getReviewedJob(review.getJobId());
        Owner owner = ownerService.getOwnerProfileById(data.getJob().getOwnerProfileId());
        return StudentReviewResult.of(
                review, data.getApprovedSubmission().getId(), data.getJob().getTitle(), owner.getStoreName());
    }
}
