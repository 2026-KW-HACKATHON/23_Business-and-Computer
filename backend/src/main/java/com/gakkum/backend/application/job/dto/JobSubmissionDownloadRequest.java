package com.gakkum.backend.application.job.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.gakkum.backend.domain.job.dto.JobCommandDto.DownloadJobSubmissionFilesCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 여러 의뢰에서 고른 작업물 파일의 ZIP 다운로드 요청. 의뢰 수와 파일 수는 제한하지 않는다. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobSubmissionDownloadRequest {

    @NotEmpty
    private List<@NotNull @Valid JobFiles> jobs;

    public static JobSubmissionDownloadRequest of(List<JobFiles> jobs) {
        return JobSubmissionDownloadRequest.builder()
                .jobs(jobs)
                .build();
    }

    @AssertTrue
    private boolean isJobIdsDistinct() {
        return jobs == null || jobs.stream().filter(Objects::nonNull).map(JobFiles::getJobId).distinct().count()
                == jobs.stream().filter(Objects::nonNull).count();
    }

    public DownloadJobSubmissionFilesCommand toCommand(String username) {
        return DownloadJobSubmissionFilesCommand.of(username, jobs.stream()
                .map(job -> DownloadJobSubmissionFilesCommand.JobFiles.of(job.getJobId(), job.getFileUrls()))
                .toList());
    }

    /** 한 의뢰에서 고른 파일. fileUrls는 그 의뢰의 제출물에 등록된 공개 파일 URL이다. */
    @Getter
    @Builder(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class JobFiles {

        @NotNull
        @Positive
        private Long jobId;

        @NotEmpty
        private List<@NotBlank @Size(max = 2048) String> fileUrls;

        public static JobFiles of(Long jobId, List<String> fileUrls) {
            return JobFiles.builder()
                    .jobId(jobId)
                    .fileUrls(fileUrls)
                    .build();
        }

        @AssertTrue
        private boolean isFileUrlsDistinct() {
            return fileUrls == null || new HashSet<>(fileUrls).size() == fileUrls.size();
        }
    }
}
