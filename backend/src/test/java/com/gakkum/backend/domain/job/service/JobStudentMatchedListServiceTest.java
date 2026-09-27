package com.gakkum.backend.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetStudentMatchedJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.StudentMatchedJobData;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobSpecialty;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.entity.JobSubmissionReviewStatus;
import com.gakkum.backend.domain.job.entity.JobSubmissionType;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;

class JobStudentMatchedListServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final JobSpecialtyRepository jobSpecialtyRepository = mock(JobSpecialtyRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);
    private final JobSubmissionRepository jobSubmissionRepository = mock(JobSubmissionRepository.class);
    private final JobService jobService = new JobService(
            jobRepository, jobSpecialtyRepository, jobApplicationRepository,
            jobSubmissionRepository, mock(SpecialtyService.class));

    @Test
    @DisplayName("학생 본인의 MATCHED 의뢰를 최신순으로 조회하고 초안 미제출·검토 대기·수정 요청·승인 의뢰를 모두 최신 제출물과 연결한다")
    void returnsAllMatchedJobsWithLatestSubmission() {
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of(job(45L), job(44L), job(43L), job(42L)));
        when(jobSpecialtyRepository.findByJobIdIn(List.of(45L, 44L, 43L, 42L))).thenReturn(List.of(
                JobSpecialty.create(42L, 12L),
                JobSpecialty.create(43L, 21L)));
        when(jobSubmissionRepository.findByJobIdIn(List.of(45L, 44L, 43L, 42L))).thenReturn(List.of(
                // 44: 수정안 승인
                submission(44L, 1, JobSubmissionReviewStatus.APPROVED),
                submission(44L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED),
                // 43: 초안 수정 요청
                submission(43L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED),
                // 42: 수정안 검토 대기
                submission(42L, 0, JobSubmissionReviewStatus.REVISION_REQUESTED),
                submission(42L, 1, JobSubmissionReviewStatus.PENDING)));

        List<StudentMatchedJobData> result = jobService.getStudentMatchedJobs(GetStudentMatchedJobsCommand.of(7L));

        assertThat(result).extracting(data -> data.getJob().getId()).containsExactly(45L, 44L, 43L, 42L);
        assertThat(result.get(0).getLatestSubmission()).isNull();
        assertThat(result.get(1).getLatestSubmission().getSubmissionType()).isEqualTo(JobSubmissionType.REVISION);
        assertThat(result.get(1).getLatestSubmission().getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.APPROVED);
        assertThat(result.get(2).getLatestSubmission().getSubmissionType()).isEqualTo(JobSubmissionType.DRAFT);
        assertThat(result.get(2).getLatestSubmission().getReviewStatus())
                .isEqualTo(JobSubmissionReviewStatus.REVISION_REQUESTED);
        assertThat(result.get(3).getLatestSubmission().getRevisionNumber()).isEqualTo(1);
        assertThat(result.get(3).getLatestSubmission().getReviewStatus()).isEqualTo(JobSubmissionReviewStatus.PENDING);
        assertThat(result.get(3).getSpecialtyIds()).containsExactly(12L);
        assertThat(result.get(0).getSpecialtyIds()).isEmpty();
        // 다른 학생·CLOSED 의뢰는 조회 조건(학생 프로필 ID + MATCHED)으로 제외한다
        verify(jobRepository).findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED);
        verifyNoInteractions(jobApplicationRepository);
    }

    @Test
    @DisplayName("매칭된 MATCHED 의뢰가 없으면 빈 목록을 반환하고 연관 데이터를 조회하지 않는다")
    void returnsEmptyListWithoutRelatedQueries() {
        when(jobRepository.findBySelectedStudentProfileIdAndStatusOrderByCreatedAtDescIdDesc(7L, JobStatus.MATCHED))
                .thenReturn(List.of());

        assertThat(jobService.getStudentMatchedJobs(GetStudentMatchedJobsCommand.of(7L))).isEmpty();
        verifyNoInteractions(jobSpecialtyRepository, jobSubmissionRepository);
    }

    private Job job(Long id) {
        return Job.builder().id(id).selectedStudentProfileId(7L).status(JobStatus.MATCHED).build();
    }

    private JobSubmission submission(Long jobId, int revisionNumber, JobSubmissionReviewStatus reviewStatus) {
        return JobSubmission.builder()
                .jobId(jobId)
                .submissionType(revisionNumber == 0 ? JobSubmissionType.DRAFT : JobSubmissionType.REVISION)
                .revisionNumber(revisionNumber)
                .reviewStatus(reviewStatus)
                .build();
    }
}
