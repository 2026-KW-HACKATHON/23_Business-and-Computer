package com.gakkum.backend.application.job.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.springframework.context.ApplicationEventPublisher;

import com.gakkum.backend.application.job.dto.JobCreateRequest;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.service.ChatAttachmentPolicy;
import com.gakkum.backend.domain.job.client.JobSubmissionFileStorageClient;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class JobFacadeCreateTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";

    private final UserService userService = mock(UserService.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final JobService jobService = mock(JobService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final JobFacade jobFacade = new JobFacade(
            userService, new OwnerService(ownerRepository), jobService, mock(SpecialtyCategoryService.class),
            specialtyService, mock(StudentService.class),
            mock(JobSubmissionFileStorageClient.class), mock(ChatAttachmentPolicy.class), mock(PaymentService.class),
                mock(ReviewService.class), mock(CertificateService.class), mock(ProposalService.class), mock(MediaService.class), mock(ApplicationEventPublisher.class));

    @Test
    @DisplayName("의뢰 생성 시 퍼사드가 특기 ID를 먼저 검증한 뒤 사업주 프로필 ID로 의뢰 생성을 맡긴다")
    void validatesSpecialtiesBeforeCreatingJob() {
        givenOwner();

        jobFacade.createJob(USERNAME, request(List.of(1L, 2L)));

        InOrder order = inOrder(specialtyService, jobService);
        order.verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        ArgumentCaptor<CreateJobCommand> captor = ArgumentCaptor.forClass(CreateJobCommand.class);
        order.verify(jobService).createJob(captor.capture(), any());
        assertThat(captor.getValue().getOwnerProfileId()).isEqualTo(5L);
        assertThat(captor.getValue().getSpecialtyIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("특기 검증에 실패하면 예외를 그대로 전달하고 의뢰를 생성하지 않는다")
    void doesNotCreateJobWhenSpecialtyInvalid() {
        givenOwner();
        doThrow(new BusinessException(ErrorCode.SPECIALTY_NOT_FOUND))
                .when(specialtyService).validateSpecialtyIds(List.of(1L, 99L));

        assertThatThrownBy(() -> jobFacade.createJob(USERNAME, request(List.of(1L, 99L))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPECIALTY_NOT_FOUND));
        verify(jobService, never()).createJob(any(), any());
    }

    private void givenOwner() {
        when(userService.getActiveUser(USERNAME)).thenReturn(User.builder().id(USER_ID).role(UserRole.OWNER).build());
        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(Owner.builder().id(5L).build()));
    }

    private JobCreateRequest request(List<Long> specialtyIds) {
        JobCreateRequest request = mock(JobCreateRequest.class);
        when(request.toCommand(5L)).thenReturn(CreateJobCommand.of(
                5L, specialtyIds, "의뢰 제목", "맡기고 싶은 일", 500000L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15), 1));
        return request;
    }

    @Test
    @DisplayName("의뢰는 의뢰한 사장님 프로필의 격리 범위로 만들어 실제 사장님은 null, 데모 사장님은 자기 데모 세션 ID를 넘긴다")
    void createsJobInOwnerDemoSession() {
        givenOwner();
        jobFacade.createJob(USERNAME, request(List.of(1L, 2L)));
        verify(jobService).createJob(any(), org.mockito.ArgumentMatchers.isNull());

        when(ownerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                Owner.builder().id(5L).demoSessionId("01K6DEMO00000000000000000A").build()));
        jobFacade.createJob(USERNAME, request(List.of(1L, 2L)));
        verify(jobService).createJob(any(), org.mockito.ArgumentMatchers.eq("01K6DEMO00000000000000000A"));
    }
}
