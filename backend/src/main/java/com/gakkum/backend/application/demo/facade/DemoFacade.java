package com.gakkum.backend.application.demo.facade;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import com.gakkum.backend.util.UlidGenerator;

/**
 * 로그인 없는 체험용 데모 로그인. demo-login.enabled가 켜진 서버에서만 등록된다.
 * 방문자마다 demoSessionId가 같은 데모 사장님·학생 한 쌍을 만들고, 이 값이 같은 데이터끼리만 서로 보인다.
 * 새 쌍의 예시 데이터는 DemoSampleDataSeeder가 채운다.
 */
@Component
@ConditionalOnProperty(name = "demo-login.enabled", havingValue = "true")
public class DemoFacade {

    private static final int SAMPLE_SPECIALTY_COUNT = 2;

    private final UserService userService;
    private final OwnerService ownerService;
    private final StudentService studentService;
    private final BusinessCategoryService businessCategoryService;
    private final SpecialtyService specialtyService;
    private final DemoSampleDataSeeder sampleDataSeeder;
    private final JwtService jwtService;
    private final Clock clock;
    private final long maxNewSessionsPerHour;

    public DemoFacade(UserService userService,
                      OwnerService ownerService,
                      StudentService studentService,
                      BusinessCategoryService businessCategoryService,
                      SpecialtyService specialtyService,
                      DemoSampleDataSeeder sampleDataSeeder,
                      JwtService jwtService,
                      Clock clock,
                      @Value("${demo-login.max-new-sessions-per-hour}") long maxNewSessionsPerHour) {
        this.userService = userService;
        this.ownerService = ownerService;
        this.studentService = studentService;
        this.businessCategoryService = businessCategoryService;
        this.specialtyService = specialtyService;
        this.sampleDataSeeder = sampleDataSeeder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.maxNewSessionsPerHour = maxNewSessionsPerHour;
    }

    /**
     * demoSessionId가 없으면 새 데모 계정 쌍과 예시 데이터를 만들고, 있으면 새로 만들지 않고 그 쌍의 요청한 역할로 전환한다.
     * 모르는 demoSessionId는 인증 오류로 거부한다.
     */
    @Transactional
    public DemoLoginResponse login(DemoLoginRequest request) {
        UserRole role = request.getRole() == DemoRole.OWNER ? UserRole.OWNER : UserRole.STUDENT;
        User user = request.getDemoSessionId() == null
                ? createSession(role)
                : userService.getDemoUser(request.getDemoSessionId(), role);

        String accessToken = jwtService.issueAccessToken(user.getUsername(), role);
        String refreshToken = jwtService.replaceRefreshToken(user.getUsername(), role);
        return DemoLoginResponse.of(accessToken, refreshToken, user.getDemoSessionId());
    }

    /** 인증 없이 호출마다 행이 생기므로 최근 1시간에 만든 세션 수가 상한에 닿으면 새로 만들지 않는다. */
    private User createSession(UserRole requestedRole) {
        LocalDateTime oneHourAgo = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).minusHours(1);
        if (userService.countDemoSessionsCreatedAfter(oneHourAgo) >= maxNewSessionsPerHour) {
            throw new BusinessException(ErrorCode.DEMO_SESSION_LIMIT_EXCEEDED);
        }

        String demoSessionId = UlidGenerator.generate();
        User owner = userService.createDemoUser(demoSessionId, UserRole.OWNER, "데모 사장님", null);
        User student = userService.createDemoUser(demoSessionId, UserRole.STUDENT, "데모 학생",
                "demo-" + demoSessionId.toLowerCase(Locale.ROOT) + "@example.com");

        List<Long> specialtyIds = specialtyService.getFirstSpecialtyIds(SAMPLE_SPECIALTY_COUNT);
        Owner store = ownerService.createOwnerProfile(CreateOwnerProfileCommand.of(
                owner.getId(),
                "DEMO-" + demoSessionId,
                null,
                "데모 사장님",
                "[데모] 가꿈 분식",
                businessCategoryService.getFirstCategoryId(),
                "서울 노원구 광운로 20",
                "광운대 앞에서 10년째 운영 중인 분식집입니다. 체험용 데모 매장입니다.",
                null,
                List.of()), demoSessionId);
        Student profile = studentService.createStudentProfile(CreateStudentProfileCommand.of(
                student.getId(),
                "광운대학교",
                DemoStudentNumbers.next(studentService),
                "소프트웨어학부",
                null,
                "디자인과 SNS 홍보에 관심이 많은 체험용 데모 학생입니다.",
                null));
        for (Long specialtyId : specialtyIds) {
            specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(profile.getId(), specialtyId));
        }
        sampleDataSeeder.seed(new DemoSampleDataSeeder.Visitor(demoSessionId, store, profile));

        return requestedRole == UserRole.OWNER ? owner : student;
    }
}
