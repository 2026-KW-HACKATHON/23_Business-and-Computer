package com.gakkum.backend.application.demo.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.gakkum.backend.application.demo.dto.DemoLoginRequest;
import com.gakkum.backend.application.demo.dto.DemoLoginResponse;
import com.gakkum.backend.application.demo.dto.DemoRole;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
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

@DisplayName("데모 로그인")
class DemoFacadeTest {

    private static final String SESSION = "01K6DEMO00000000000000000A";
    private static final long MAX_NEW_SESSIONS_PER_HOUR = 3;

    private final UserService userService = mock(UserService.class);
    private final OwnerService ownerService = mock(OwnerService.class);
    private final StudentService studentService = mock(StudentService.class);
    private final BusinessCategoryService businessCategoryService = mock(BusinessCategoryService.class);
    private final SpecialtyService specialtyService = mock(SpecialtyService.class);
    private final DemoSampleDataSeeder sampleDataSeeder = mock(DemoSampleDataSeeder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T23:00:00Z"), ZoneOffset.UTC);
    private final DemoFacade facade = new DemoFacade(userService, ownerService, studentService,
            businessCategoryService, specialtyService, sampleDataSeeder, jwtService, clock, MAX_NEW_SESSIONS_PER_HOUR);

    @BeforeEach
    void givenTokens() {
        when(jwtService.issueAccessToken(anyString(), any())).thenAnswer(
                invocation -> "access:" + invocation.getArgument(0) + ":" + invocation.getArgument(1));
        when(jwtService.replaceRefreshToken(anyString(), any())).thenAnswer(
                invocation -> "refresh:" + invocation.getArgument(0) + ":" + invocation.getArgument(1));
    }

    @ParameterizedTest
    @EnumSource(DemoRole.class)
    @DisplayName("세션 ID 없이 요청하면 같은 세션의 데모 사장님·학생 한 쌍을 만들고 요청한 역할의 토큰과 세션 ID를 반환한다")
    void createsPairAndIssuesRequestedRoleTokens(DemoRole role) {
        givenNewSessionDependencies(List.of(11L, 12L));

        DemoLoginResponse response = facade.login(DemoLoginRequest.of(role, null));

        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).createDemoUser(sessionCaptor.capture(), eq(UserRole.OWNER), eq("데모 사장님"), eq(null));
        String session = sessionCaptor.getValue();
        assertThat(session).matches("^[0-9A-Z]{26}$");
        verify(userService).createDemoUser(session, UserRole.STUDENT, "데모 학생",
                "demo-" + session.toLowerCase() + "@example.com");

        String username = "DEMO_" + session + "_" + role.name();
        assertThat(response.getDemoSessionId()).isEqualTo(session);
        assertThat(response.getAccessToken()).isEqualTo("access:" + username + ":" + role.name());
        assertThat(response.getRefreshToken()).isEqualTo("refresh:" + username + ":" + role.name());
        verify(userService, never()).getDemoUser(any(), any());
    }

    @Test
    @DisplayName("새 세션에는 데모 표시가 붙은 매장과 특기가 있는 학생 프로필을 만들고 같은 세션의 예시 데이터를 채운다")
    void seedsStoreStudentAndSampleData() {
        givenNewSessionDependencies(List.of(11L, 12L));

        String session = facade.login(DemoLoginRequest.of(DemoRole.OWNER, null)).getDemoSessionId();

        ArgumentCaptor<CreateOwnerProfileCommand> ownerCaptor = ArgumentCaptor.forClass(CreateOwnerProfileCommand.class);
        verify(ownerService).createOwnerProfile(ownerCaptor.capture(), eq(session));
        CreateOwnerProfileCommand store = ownerCaptor.getValue();
        assertThat(store.getUserId()).isEqualTo("owner-user-" + session);
        assertThat(store.getStoreName()).startsWith("[데모]");
        assertThat(store.getBusinessNumber()).isEqualTo("DEMO-" + session);
        assertThat(store.getCategoryId()).isEqualTo(3L);

        ArgumentCaptor<CreateStudentProfileCommand> studentCaptor =
                ArgumentCaptor.forClass(CreateStudentProfileCommand.class);
        verify(studentService).createStudentProfile(studentCaptor.capture());
        assertThat(studentCaptor.getValue().getUserId()).isEqualTo("student-user-" + session);
        assertThat(studentCaptor.getValue().getStudentNumber()).matches("^20240\\d{5}$");
        ArgumentCaptor<AddStudentSpecialtyCommand> specialtyCaptor =
                ArgumentCaptor.forClass(AddStudentSpecialtyCommand.class);
        verify(specialtyService, times(2)).addStudentSpecialty(specialtyCaptor.capture());
        assertThat(specialtyCaptor.getAllValues()).extracting(AddStudentSpecialtyCommand::getStudentProfileId)
                .containsOnly(7L);
        assertThat(specialtyCaptor.getAllValues()).extracting(AddStudentSpecialtyCommand::getSpecialtyId)
                .containsExactly(11L, 12L);

        ArgumentCaptor<DemoSampleDataSeeder.Visitor> visitorCaptor =
                ArgumentCaptor.forClass(DemoSampleDataSeeder.Visitor.class);
        verify(sampleDataSeeder).seed(visitorCaptor.capture());
        assertThat(visitorCaptor.getValue().demoSessionId()).isEqualTo(session);
        assertThat(visitorCaptor.getValue().store().getId()).isEqualTo(5L);
        assertThat(visitorCaptor.getValue().student().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("특기 기준 데이터가 없어도 특기 없이 계정을 만들고 예시 데이터를 채운다")
    void seedsWithoutSpecialties() {
        givenNewSessionDependencies(List.of());

        facade.login(DemoLoginRequest.of(DemoRole.STUDENT, null));

        verify(specialtyService, never()).addStudentSpecialty(any());
        verify(sampleDataSeeder).seed(any());
    }

    @Test
    @DisplayName("이미 쓰는 학번이 뽑히면 다른 학번을 다시 뽑는다")
    void retriesTakenStudentNumber() {
        givenNewSessionDependencies(List.of());
        when(studentService.existsStudentNumber(anyString())).thenReturn(true, true, false);

        facade.login(DemoLoginRequest.of(DemoRole.OWNER, null));

        verify(studentService, times(3)).existsStudentNumber(anyString());
        verify(studentService).createStudentProfile(any());
    }

    @Test
    @DisplayName("최근 1시간에 만든 세션 수가 상한에 닿으면 DEMO_429로 거부하고 아무것도 만들지 않는다")
    void rejectsNewSessionOverHourlyLimit() {
        LocalDateTime oneHourAgo = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).minusHours(1);
        when(userService.countDemoSessionsCreatedAfter(oneHourAgo)).thenReturn(MAX_NEW_SESSIONS_PER_HOUR);

        assertThatThrownBy(() -> facade.login(DemoLoginRequest.of(DemoRole.OWNER, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEMO_SESSION_LIMIT_EXCEEDED));

        verify(userService, never()).createDemoUser(any(), any(), any(), any());
        verifyNoInteractions(ownerService, studentService, sampleDataSeeder, jwtService);
    }

    @Test
    @DisplayName("상한 바로 아래면 새 세션을 만든다")
    void createsSessionJustBelowHourlyLimit() {
        givenNewSessionDependencies(List.of());
        when(userService.countDemoSessionsCreatedAfter(any())).thenReturn(MAX_NEW_SESSIONS_PER_HOUR - 1);

        assertThat(facade.login(DemoLoginRequest.of(DemoRole.OWNER, null)).getDemoSessionId()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(DemoRole.class)
    @DisplayName("세션 ID와 함께 요청하면 새로 만들지 않고 그 세션의 요청한 역할 토큰을 반환하며 상한도 세지 않는다")
    void switchesRoleWithinExistingSession(DemoRole role) {
        UserRole userRole = UserRole.valueOf(role.name());
        when(userService.getDemoUser(SESSION, userRole)).thenReturn(User.builder()
                .id("user-1").username("DEMO_" + SESSION + "_" + role.name()).role(userRole)
                .demoSessionId(SESSION).build());

        DemoLoginResponse response = facade.login(DemoLoginRequest.of(role, SESSION));

        assertThat(response.getDemoSessionId()).isEqualTo(SESSION);
        assertThat(response.getAccessToken()).isEqualTo("access:DEMO_" + SESSION + "_" + role.name() + ":" + role.name());
        assertThat(response.getRefreshToken())
                .isEqualTo("refresh:DEMO_" + SESSION + "_" + role.name() + ":" + role.name());
        verify(userService, never()).createDemoUser(any(), any(), any(), any());
        verify(userService, never()).countDemoSessionsCreatedAfter(any());
        verifyNoInteractions(ownerService, studentService, sampleDataSeeder, specialtyService, businessCategoryService);
    }

    @Test
    @DisplayName("모르는 세션 ID면 인증 오류로 거부하고 새 세션을 만들지 않는다")
    void rejectsUnknownSession() {
        when(userService.getDemoUser(SESSION, UserRole.OWNER)).thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThatThrownBy(() -> facade.login(DemoLoginRequest.of(DemoRole.OWNER, SESSION)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(userService, never()).createDemoUser(any(), any(), any(), any());
        verifyNoInteractions(jwtService);
    }

    private void givenNewSessionDependencies(List<Long> specialtyIds) {
        when(userService.createDemoUser(anyString(), any(), anyString(), any())).thenAnswer(invocation -> {
            String session = invocation.getArgument(0);
            UserRole role = invocation.getArgument(1);
            return User.builder()
                    .id((role == UserRole.OWNER ? "owner-user-" : "student-user-") + session)
                    .username("DEMO_" + session + "_" + role.name())
                    .role(role)
                    .demoSessionId(session)
                    .build();
        });
        when(specialtyService.getFirstSpecialtyIds(2)).thenReturn(specialtyIds);
        when(businessCategoryService.getFirstCategoryId()).thenReturn(3L);
        when(ownerService.createOwnerProfile(any(), anyString())).thenReturn(Owner.builder().id(5L).build());
        when(studentService.createStudentProfile(any())).thenReturn(Student.builder().id(7L).build());
    }
}
