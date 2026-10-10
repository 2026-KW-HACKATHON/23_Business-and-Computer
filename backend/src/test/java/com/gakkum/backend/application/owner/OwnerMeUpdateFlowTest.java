package com.gakkum.backend.application.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import com.gakkum.backend.application.owner.controller.OwnerController;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.auth.service.AuthService;
import com.gakkum.backend.domain.category.repository.BusinessCategoryRepository;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.job.repository.JobApplicationRepository;
import com.gakkum.backend.domain.job.repository.JobRepository;
import com.gakkum.backend.domain.job.repository.JobSpecialtyRepository;
import com.gakkum.backend.domain.job.repository.JobSubmissionRepository;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.media.client.MediaImageStorageClient;
import com.gakkum.backend.domain.media.service.MediaService;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.repository.OwnerRepository;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.proposal.repository.ProposalLikeRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalRepository;
import com.gakkum.backend.domain.proposal.repository.ProposalSpecialtyRepository;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.repository.UserRepository;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;
import com.gakkum.backend.global.transaction.ImmediateTransactionTemplate;
import com.gakkum.backend.domain.owner.service.StoreConcernService;
import com.gakkum.backend.domain.specialty.service.SpecialtyCategoryService;

@DisplayName("사장님 내 정보 수정 전체 흐름 (PUT /owners/me)")
class OwnerMeUpdateFlowTest {

    private static final String USERNAME = "KAKAO_12345";
    private static final String OWNER_USER_ID = "01K58M6PJV8VAJMXHBHJ2PNB5C";
    private static final Long OWNER_PROFILE_ID = 5L;
    private static final String URL = "/owners/me";
    private static final String OLD_STORE_NAME = "예전 상호";
    private static final Long OLD_CATEGORY_ID = 1L;
    private static final String OLD_IMAGE_URL = "https://cdn.gakkum.test/old.png";
    private static final String OLD_ADDRESS = "서울시 노원구 광운로 20";
    private static final String OLD_DESCRIPTION = "예전 소개";
    private static final List<String> STORE_IMAGE_URLS = List.of("https://cdn.gakkum.test/store.png");
    private static final String PROFILE_KEY_PREFIX = "images/profile/" + OWNER_USER_ID + "/";
    private static final String UPLOADED_IMAGE_URL = "https://bucket.s3.amazonaws.com/" + PROFILE_KEY_PREFIX
            + "0b6f3c1e-8a4d-4f7e-9d2a-1c5b7e9f0a12.png";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OwnerRepository ownerRepository = mock(OwnerRepository.class);
    private final BusinessCategoryRepository businessCategoryRepository = mock(BusinessCategoryRepository.class);
    // 사진 저장소만 가짜로 두고 본인 업로드 확인은 실제 MediaService가 한다
    private final MediaImageStorageClient imageStorageClient = mock(MediaImageStorageClient.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;
    private Owner owner;

    @BeforeEach
    void setUp() {
        JwtService jwtService = mock(JwtService.class);
        OwnerFacade facade = new OwnerFacade(
                new UserService(userRepository, jwtService),
                new OwnerService(ownerRepository),
                new BusinessCategoryService(businessCategoryRepository),
                jwtService,
                new JobService(mock(JobRepository.class), mock(JobSpecialtyRepository.class),
                        mock(JobApplicationRepository.class), mock(JobSubmissionRepository.class),
                        Clock.systemUTC()),
                new ProposalService(mock(ProposalRepository.class), mock(ProposalSpecialtyRepository.class),
                        mock(ProposalLikeRepository.class)),
                mock(AuthService.class),
                new MediaService(imageStorageClient, DataSize.ofMegabytes(10)),
                new ImmediateTransactionTemplate(), mock(StoreConcernService.class), mock(SpecialtyCategoryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // 업종 기준 데이터는 1·2·3번만 있다
        when(businessCategoryRepository.existsById(anyLong())).thenAnswer(
                invocation -> invocation.<Long>getArgument(0) <= 3);
    }

    @Test
    @DisplayName("사장님 본인의 상호명·업종·사진·주소·소개를 앞뒤 공백을 뺀 값으로 저장하고 성공 여부만 반환한다")
    void replacesOwnEditableInformation() throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();
        givenUploadedProfileImage(UPLOADED_IMAGE_URL);

        perform("""
                {
                  "storeName": "  가꿈 베이커리  ",
                  "categoryId": 2,
                  "profileImageUrl": "%s",
                  "storeAddress": "  서울시 노원구 광운로 1  ",
                  "description": "  매일 굽는 빵집입니다.  "
                }
                """.formatted(UPLOADED_IMAGE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.success").value(true));

        assertThat(owner.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(owner.getCategoryId()).isEqualTo(2L);
        assertThat(owner.getProfileImageUrl()).isEqualTo(UPLOADED_IMAGE_URL);
        assertThat(owner.getStoreAddress()).isEqualTo("서울시 노원구 광운로 1");
        assertThat(owner.getDescription()).isEqualTo("매일 굽는 빵집입니다.");
        verify(ownerRepository).save(owner);
        assertUneditableFieldsKept();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 2}",
            "{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 2, \"profileImageUrl\": null,"
                    + " \"storeAddress\": null, \"description\": null}",
            "{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 2, \"profileImageUrl\": \"\","
                    + " \"storeAddress\": \"\", \"description\": \"\"}",
            "{\"storeName\": \"가꿈 베이커리\", \"categoryId\": 2, \"storeAddress\": \"   \","
                    + " \"description\": \"  \\n \"}" })
    @DisplayName("선택 항목을 생략하거나 null·빈 문자열로 보내면 저장된 값을 지우고 주소와 소개는 공백만 보내도 지운다")
    void clearsOptionalFields(String body) throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();

        perform(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(owner.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(owner.getCategoryId()).isEqualTo(2L);
        assertThat(owner.getProfileImageUrl()).isNull();
        assertThat(owner.getStoreAddress()).isNull();
        assertThat(owner.getDescription()).isNull();
        verify(ownerRepository).save(owner);
        assertUneditableFieldsKept();
    }

    @Test
    @DisplayName("같은 요청을 반복해 보내도 매번 성공하고 저장된 값은 같다")
    void savesSameRequestRepeatedly() throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();
        String body = body("\"가꿈 베이커리\"", "3", "\"storeAddress\": \"서울시 노원구 광운로 1\"");

        for (int attempt = 0; attempt < 3; attempt++) {
            perform(body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        assertThat(owner.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(owner.getCategoryId()).isEqualTo(3L);
        assertThat(owner.getProfileImageUrl()).isNull();
        assertThat(owner.getStoreAddress()).isEqualTo("서울시 노원구 광운로 1");
        assertThat(owner.getDescription()).isNull();
        verify(ownerRepository, times(3)).save(owner);
        assertUneditableFieldsKept();
    }

    @Test
    @DisplayName("상호명·사진 URL·주소는 255자까지 저장하고 소개는 길이 제한 없이 저장한다")
    void acceptsMaximumLengths() throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();
        String storeName = "가".repeat(255);
        String imageUrl = "https://example.com/" + "a".repeat(235);
        givenUploadedProfileImage(imageUrl);
        String address = "나".repeat(255);
        String description = "다".repeat(2000);

        perform("{\"storeName\": \"" + storeName + "\", \"categoryId\": 1, \"profileImageUrl\": \"" + imageUrl
                + "\", \"storeAddress\": \"" + address + "\", \"description\": \"" + description + "\"}")
                .andExpect(status().isOk());

        assertThat(owner.getStoreName()).isEqualTo(storeName);
        assertThat(owner.getProfileImageUrl()).isEqualTo(imageUrl);
        assertThat(owner.getStoreAddress()).isEqualTo(address);
        assertThat(owner.getDescription()).isEqualTo(description);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("잘못된 입력은 400으로 거부하고 기존 정보를 그대로 둔 채 저장하지 않는다")
    void rejectsInvalidInput(String description, String body, String errorCode) throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();

        perform(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(errorCode));

        assertNothingWritten();
    }

    static Stream<Arguments> invalidBodies() {
        String longUrl = "https://example.com/" + "a".repeat(236);
        return Stream.of(
                invalid("상호명 누락", "{\"categoryId\": 1}"),
                invalid("상호명 null", body("null", "1")),
                invalid("빈 상호명", body("\"\"", "1")),
                invalid("공백만 있는 상호명", body("\"   \"", "1")),
                invalid("255자를 넘는 상호명", body("\"" + "가".repeat(256) + "\"", "1")),
                invalid("업종 ID 누락", "{\"storeName\": \"가꿈 베이커리\"}"),
                invalid("업종 ID null", body("\"가꿈 베이커리\"", "null")),
                invalid("0인 업종 ID", body("\"가꿈 베이커리\"", "0")),
                invalid("음수 업종 ID", body("\"가꿈 베이커리\"", "-1")),
                invalid("숫자가 아닌 업종 ID", body("\"가꿈 베이커리\"", "\"카페\"")),
                Arguments.of("존재하지 않는 업종 ID", body("\"가꿈 베이커리\"", "99"), "CATEGORY_400"),
                invalid("HTTP(S)가 아닌 사진 URL", withOptional("\"profileImageUrl\": \"ftp://example.com/a.png\"")),
                invalid("공백이 있는 사진 URL", withOptional("\"profileImageUrl\": \"https://example.com/a b.png\"")),
                invalid("공백만 있는 사진 URL", withOptional("\"profileImageUrl\": \"  \"")),
                invalid("255자를 넘는 사진 URL", withOptional("\"profileImageUrl\": \"" + longUrl + "\"")),
                invalid("255자를 넘는 주소", withOptional("\"storeAddress\": \"" + "나".repeat(256) + "\"")),
                invalid("본문 없음", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example.com/tracker.png",
            "https://bucket.s3.amazonaws.com/images/profile/OTHERUSER0000000000000001/0b6f3c1e-8a4d-4f7e-9d2a-1c5b7e9f0a12.png" })
    @DisplayName("외부 주소나 다른 사용자의 사진 URL로 바꾸면 400 MEDIA_400_IMAGE_URL로 거부하고 저장하지 않는다")
    void rejectsProfileImageNotUploadedByOwner(String imageUrl) throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();

        perform(withOptional("\"profileImageUrl\": \"" + imageUrl + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEDIA_400_IMAGE_URL"));

        verify(imageStorageClient).findKey(imageUrl, PROFILE_KEY_PREFIX);
        verify(imageStorageClient, never()).exists(any());
        assertNothingWritten();
    }

    @Test
    @DisplayName("본인 프로필용으로 발급만 받고 올리지 않은 사진이면 409 MEDIA_409_IMAGE_NOT_UPLOADED로 거부하고 저장하지 않는다")
    void rejectsProfileImageNotYetUploaded() throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();
        givenUploadedProfileImage(UPLOADED_IMAGE_URL);
        when(imageStorageClient.exists(any())).thenReturn(false);

        perform(withOptional("\"profileImageUrl\": \"" + UPLOADED_IMAGE_URL + "\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MEDIA_409_IMAGE_NOT_UPLOADED"));

        assertNothingWritten();
    }

    @Test
    @DisplayName("저장된 프로필 사진 URL을 그대로 보내면 우리 저장소 주소가 아니어도 확인 없이 받아 다른 항목을 저장한다")
    void keepsUnchangedStoredProfileImage() throws Exception {
        givenUser(UserRole.OWNER);
        givenProfile();

        perform(body("\"가꿈 베이커리\"", "2", "\"profileImageUrl\": \"" + OLD_IMAGE_URL + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(owner.getStoreName()).isEqualTo("가꿈 베이커리");
        assertThat(owner.getCategoryId()).isEqualTo(2L);
        assertThat(owner.getProfileImageUrl()).isEqualTo(OLD_IMAGE_URL);
        verify(ownerRepository).save(owner);
        verifyNoInteractions(imageStorageClient);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STUDENT", "PENDING"})
    @DisplayName("학생이나 가입 미완료 사용자가 수정하면 403 OWNER_403_ME_UPDATE를 반환하고 사장님 데이터를 건드리지 않는다")
    void rejectsNonOwner(UserRole role) throws Exception {
        givenUser(role);

        perform(body("\"가꿈 베이커리\"", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OWNER_403_ME_UPDATE"));

        verifyNoInteractions(ownerRepository, businessCategoryRepository);
    }

    @Test
    @DisplayName("잠겼거나 존재하지 않는 사용자는 401 COMMON_401로 거부하고 사장님 데이터를 건드리지 않는다")
    void rejectsInactiveUser() throws Exception {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.empty());

        perform(body("\"가꿈 베이커리\"", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));

        verifyNoInteractions(ownerRepository, businessCategoryRepository);
    }

    @Test
    @DisplayName("사장님 역할인데 사장님 프로필이 없으면 500 COMMON_500을 반환하고 아무것도 저장하지 않는다")
    void rejectsOwnerWithoutProfile() throws Exception {
        givenUser(UserRole.OWNER);
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.empty());

        perform(body("\"가꿈 베이커리\"", "2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));

        verify(ownerRepository, never()).save(any());
        verifyNoInteractions(businessCategoryRepository);
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(put(URL)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void givenUser(UserRole role) {
        when(userRepository.findByUsernameAndIsLock(USERNAME, false)).thenReturn(Optional.of(
                User.builder().id(OWNER_USER_ID).role(role).name("김가입").build()));
    }

    private void givenProfile() {
        owner = Owner.builder()
                .id(OWNER_PROFILE_ID)
                .userId(OWNER_USER_ID)
                .businessNumber("1234567890")
                .openedAt(LocalDate.of(2020, 3, 1))
                .representativeName("김대표")
                .storeName(OLD_STORE_NAME)
                .categoryId(OLD_CATEGORY_ID)
                .storeAddress(OLD_ADDRESS)
                .description(OLD_DESCRIPTION)
                .profileImageUrl(OLD_IMAGE_URL)
                .storeImageUrls(STORE_IMAGE_URLS)
                .build();
        when(ownerRepository.findByUserId(OWNER_USER_ID)).thenReturn(Optional.of(owner));
    }

    // 이 사장님이 프로필용으로 발급받아 실제로 올린 사진
    private void givenUploadedProfileImage(String imageUrl) {
        String key = PROFILE_KEY_PREFIX + "0b6f3c1e-8a4d-4f7e-9d2a-1c5b7e9f0a12.png";
        when(imageStorageClient.findKey(imageUrl, PROFILE_KEY_PREFIX)).thenReturn(Optional.of(key));
        when(imageStorageClient.exists(key)).thenReturn(true);
    }

    // 가입자 이름·사업자 정보·매장 사진 목록은 수정 대상이 아니다
    private void assertUneditableFieldsKept() {
        assertThat(owner.getId()).isEqualTo(OWNER_PROFILE_ID);
        assertThat(owner.getUserId()).isEqualTo(OWNER_USER_ID);
        assertThat(owner.getBusinessNumber()).isEqualTo("1234567890");
        assertThat(owner.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(owner.getRepresentativeName()).isEqualTo("김대표");
        assertThat(owner.getStoreImageUrls()).isEqualTo(STORE_IMAGE_URLS);
        verify(userRepository, never()).save(any());
    }

    private void assertNothingWritten() {
        assertThat(owner.getStoreName()).isEqualTo(OLD_STORE_NAME);
        assertThat(owner.getCategoryId()).isEqualTo(OLD_CATEGORY_ID);
        assertThat(owner.getProfileImageUrl()).isEqualTo(OLD_IMAGE_URL);
        assertThat(owner.getStoreAddress()).isEqualTo(OLD_ADDRESS);
        assertThat(owner.getDescription()).isEqualTo(OLD_DESCRIPTION);
        verify(ownerRepository, never()).save(any());
    }

    private static Arguments invalid(String description, String body) {
        return Arguments.of(description, body, "COMMON_400");
    }

    private static String body(String storeName, String categoryId) {
        return "{\"storeName\": " + storeName + ", \"categoryId\": " + categoryId + "}";
    }

    private static String body(String storeName, String categoryId, String optionalField) {
        return "{\"storeName\": " + storeName + ", \"categoryId\": " + categoryId + ", " + optionalField + "}";
    }

    private static String withOptional(String optionalField) {
        return body("\"가꿈 베이커리\"", "1", optionalField);
    }
}
