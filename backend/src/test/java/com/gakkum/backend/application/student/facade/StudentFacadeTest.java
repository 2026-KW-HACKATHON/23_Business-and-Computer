package com.gakkum.backend.application.student.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.gakkum.backend.application.student.dto.StudentRegistrationRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationRequest.CertificateRequest;
import com.gakkum.backend.application.student.dto.StudentRegistrationResponse;
import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.media.dto.ImagePurpose;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class StudentFacadeTest {

    private final UserService userService = mock(UserService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final CertificateService certificateService = mock(CertificateService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthService authService = mock(AuthService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final StudentFacade facade = new StudentFacade(
            userService,
            studentService,
            specialtyService,
            certificateService,
            jwtService,
            authService,
            mock(SpecialtyCategoryService.class),
            mock(ProposalService.class),
            mock(JobService.class),
            mock(ReviewService.class),
            mock(PaymentService.class),
            mock(OwnerService.class),
            mediaService);

    @Test
    void coordinatesStudentRegistrationWithGeneratedProfileId() {
        User user = User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username("KAKAO_12345")
                .isLock(false)
                .role(UserRole.PENDING)
                .build();
        Student student = Student.builder().id(10L).userId(user.getId()).build();
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr"))
                .thenReturn(user);
        when(userService.completeStudentRegistration(user, "김광운", "kwangwoon@kw.ac.kr"))
                .thenReturn(user);
        when(studentService.createStudentProfile(any(CreateStudentProfileCommand.class))).thenReturn(student);
        when(jwtService.issueAccessToken("KAKAO_12345", UserRole.STUDENT)).thenReturn("access-token");
        when(jwtService.replaceRefreshToken("KAKAO_12345", UserRole.STUDENT)).thenReturn("refresh-token");
        StudentRegistrationRequest request = StudentRegistrationRequest.of(
                "김광운",
                "kwangwoon@kw.ac.kr",
                "광운대학교",
                "2024402001",
                "컴퓨터정보공학부",
                null,
                null,
                null,
                List.of(1L, 2L),
                List.of(CertificateRequest.of(
                        "정보처리기사", 2025)));

        StudentRegistrationResponse response = facade.register("KAKAO_12345", request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        InOrder order = inOrder(userService, studentService, specialtyService,
                certificateService, jwtService, authService);
        order.verify(userService).validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr");
        order.verify(studentService).validateStudentNumberAvailable("2024402001");
        order.verify(specialtyService).validateSpecialtyIds(List.of(1L, 2L));
        order.verify(authService).consumeVerifiedStudentEmail(user.getId(), "kwangwoon@kw.ac.kr");
        order.verify(userService).completeStudentRegistration(user, "김광운", "kwangwoon@kw.ac.kr");
        order.verify(studentService).createStudentProfile(any(CreateStudentProfileCommand.class));

        ArgumentCaptor<AddStudentSpecialtyCommand> specialtyCaptor =
                ArgumentCaptor.forClass(AddStudentSpecialtyCommand.class);
        order.verify(specialtyService, org.mockito.Mockito.times(2))
                .addStudentSpecialty(specialtyCaptor.capture());
        assertThat(specialtyCaptor.getAllValues())
                .extracting(AddStudentSpecialtyCommand::getStudentProfileId)
                .containsOnly(10L);

        ArgumentCaptor<AddStudentCertificateCommand> certificateCaptor =
                ArgumentCaptor.forClass(AddStudentCertificateCommand.class);
        order.verify(certificateService).addStudentCertificate(certificateCaptor.capture());
        assertThat(certificateCaptor.getValue().getStudentProfileId()).isEqualTo(10L);

        order.verify(jwtService).issueAccessToken("KAKAO_12345", UserRole.STUDENT);
        order.verify(jwtService).replaceRefreshToken("KAKAO_12345", UserRole.STUDENT);
    }

    @Test
    @DisplayName("이메일 미인증이면 학생 정보 저장 전에 가입을 거부한다")
    void rejectsRegistrationBeforeWritingWhenEmailIsNotVerified() {
        User user = User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username("KAKAO_12345")
                .isLock(false)
                .role(UserRole.PENDING)
                .build();
        StudentRegistrationRequest request = StudentRegistrationRequest.of(
                "김광운", "kwangwoon@kw.ac.kr", "광운대학교", "2024402001",
                "컴퓨터정보공학부", null, null, null, List.of(), List.of());
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr"))
                .thenReturn(user);
        doThrow(new BusinessException(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED))
                .when(authService).consumeVerifiedStudentEmail(user.getId(), "kwangwoon@kw.ac.kr");

        assertThatThrownBy(() -> facade.register("KAKAO_12345", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.STUDENT_EMAIL_VERIFICATION_REQUIRED));
        verify(userService, never()).completeStudentRegistration(any(User.class), any(), any());
        verify(studentService, never()).createStudentProfile(any());
    }

    @Test
    @DisplayName("프로필 사진은 본인이 프로필 용도로 올린 사진인지 이메일 인증을 쓰기 전에 확인한다")
    void validatesProfileImageBeforeConsumingEmailVerification() {
        User user = pendingUser();
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr")).thenReturn(user);
        when(studentService.createStudentProfile(any(CreateStudentProfileCommand.class)))
                .thenReturn(Student.builder().id(10L).userId(user.getId()).build());

        facade.register("KAKAO_12345", requestWithProfileImage(" https://image.example.com/profile.png "));

        InOrder order = inOrder(mediaService, authService);
        order.verify(mediaService).validateUploadedImages(user.getId(), ImagePurpose.PROFILE,
                List.of("https://image.example.com/profile.png"));
        order.verify(authService).consumeVerifiedStudentEmail(user.getId(), "kwangwoon@kw.ac.kr");
    }

    @Test
    @DisplayName("본인이 올리지 않은 프로필 사진이면 그 오류로 거부하고 이메일 인증을 쓰거나 학생 정보를 저장하지 않는다")
    void rejectsProfileImageNotUploadedByUser() {
        User user = pendingUser();
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr")).thenReturn(user);
        doThrow(new BusinessException(ErrorCode.MEDIA_IMAGE_URL_INVALID))
                .when(mediaService).validateUploadedImages(any(), any(), any());

        assertThatThrownBy(() -> facade.register("KAKAO_12345",
                requestWithProfileImage("https://evil.example.com/tracker.png")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_IMAGE_URL_INVALID));
        verify(authService, never()).consumeVerifiedStudentEmail(any(), any());
        verify(userService, never()).completeStudentRegistration(any(User.class), any(), any());
        verify(studentService, never()).createStudentProfile(any());
    }

    @Test
    @DisplayName("프로필 사진이 없으면 사진 확인을 하지 않는다")
    void skipsProfileImageCheckWhenAbsent() {
        User user = pendingUser();
        when(userService.validateStudentRegistration("KAKAO_12345", "kwangwoon@kw.ac.kr")).thenReturn(user);
        when(studentService.createStudentProfile(any(CreateStudentProfileCommand.class)))
                .thenReturn(Student.builder().id(10L).userId(user.getId()).build());

        facade.register("KAKAO_12345", requestWithProfileImage(""));

        verifyNoInteractions(mediaService);
    }

    private static User pendingUser() {
        return User.builder()
                .id("01K58M6PJV8VAJMXHBHJ2PNB5C")
                .username("KAKAO_12345")
                .isLock(false)
                .role(UserRole.PENDING)
                .build();
    }

    private static StudentRegistrationRequest requestWithProfileImage(String profileImageUrl) {
        return StudentRegistrationRequest.of(
                "김광운", "kwangwoon@kw.ac.kr", "광운대학교", "2024402001",
                "컴퓨터정보공학부", null, null, profileImageUrl, List.of(), List.of());
    }
}
