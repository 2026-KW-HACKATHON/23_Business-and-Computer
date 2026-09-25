package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.domain.job.dto.JobCommandDto.GetOpenJobsCommand;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobData;
import com.gakkum.backend.domain.job.dto.JobQueryDto.OpenJobListResult;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyQueryDto.SpecialtyDetail;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobFacadeOpenListTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final OwnerService ownerService = new OwnerService(ownerRepository);
    private final JobService jobService = mock(JobService.class);
    private final SpecialtyCategoryService specialtyCategoryService = mock(SpecialtyCategoryService.class);
    private final JobFacade jobFacade = new JobFacade(
            userService, ownerService, jobService, specialtyCategoryService);

    @Test
    @DisplayName("인증된 사업주의 OPEN 의뢰에 특기를 대분류별로 묶어 반환한다")
    void assemblesOpenJobsWithSpecialtyCategories() {
        givenOwner();
        when(jobService.getOpenJobs(any(GetOpenJobsCommand.class))).thenReturn(List.of(
                OpenJobData.of(job(42L, "웹사이트 제작"), List.of(12L, 21L, 11L), 3),
                OpenJobData.of(job(41L, "포스터 제작"), List.of(21L), 0)));
        when(specialtyCategoryService.getSpecialtyDetails(Set.of(11L, 12L, 21L))).thenReturn(Map.of(
                11L, SpecialtyDetail.of(11L, "백엔드", 1L, "개발"),
                12L, SpecialtyDetail.of(12L, "프론트엔드", 1L, "개발"),
                21L, SpecialtyDetail.of(21L, "디자인", 2L, "디자인")));

        OpenJobListResult result = jobFacade.getOpenJobs(USERNAME);

        assertThat(result.getJobs()).extracting(item -> item.getJobId()).containsExactly(42L, 41L);
        assertThat(result.getJobs().get(0).getTitle()).isEqualTo("웹사이트 제작");
        assertThat(result.getJobs().get(0).getDraftDeadline()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(result.getJobs().get(0).getFinalDeadline()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.getJobs().get(0).getRevisionCount()).isEqualTo(2);
        assertThat(result.getJobs().get(0).getApplicantCount()).isEqualTo(3);
        assertThat(result.getJobs().get(0).getSpecialtyCategories())
                .extracting(category -> category.getName()).containsExactly("개발", "디자인");
        assertThat(result.getJobs().get(0).getSpecialtyCategories().get(0).getSpecialties())
                .extracting(specialty -> specialty.getName()).containsExactly("백엔드", "프론트엔드");
        assertThat(result.getJobs().get(1).getSpecialtyCategories().get(0).getSpecialties())
                .extracting(specialty -> specialty.getName()).containsExactly("디자인");

        ArgumentCaptor<GetOpenJobsCommand> command = ArgumentCaptor.forClass(GetOpenJobsCommand.class);
        verify(jobService).getOpenJobs(command.capture());
        assertThat(command.getValue().getOwnerProfileId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("OPEN 의뢰가 없으면 특기 조회 없이 빈 목록을 반환한다")
    void returnsEmptyListWithoutSpecialtyLookup() {
        givenOwner();
        when(jobService.getOpenJobs(any(GetOpenJobsCommand.class))).thenReturn(List.of());

        assertThat(jobFacade.getOpenJobs(USERNAME).getJobs()).isEmpty();
        verifyNoInteractions(specialtyCategoryService);
    }

    @Test
    @DisplayName("사업주 프로필이 없으면 의뢰와 특기를 조회하지 않는다")
    void rejectsUserWithoutOwnerProfile() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).build());

        assertThatThrownBy(() -> jobFacade.getOpenJobs(USERNAME))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OWNER_PROFILE_NOT_FOUND));
        verifyNoInteractions(jobService, specialtyCategoryService);
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).build());
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private Job job(Long id, String title) {
        return Job.builder()
                .id(id)
                .title(title)
                .draftDeadline(LocalDate.of(2026, 10, 10))
                .finalDeadline(LocalDate.of(2026, 10, 20))
                .revisionCount(2)
                .build();
    }
}
