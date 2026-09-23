package com.gakkum.backend.application.owner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.application.owner.facade.OwnerFacade;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

class OwnerControllerTest {

    private final OwnerFacade ownerFacade = mock(OwnerFacade.class);
    private final OwnerController controller =
            new OwnerController(ownerFacade);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void returnsAccessTokenAndSetsRefreshTokenCookie() throws Exception {
        when(ownerFacade.register(eq("KAKAO_12345"), any(OwnerRegistrationRequest.class)))
                .thenReturn(OwnerRegistrationResponse.of("access-token", "refresh-token"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<OwnerRegistrationResponse> response = controller.register(
                new UsernamePasswordAuthenticationToken("KAKAO_12345", null),
                validRequest("123-45-67890", null),
                servletResponse);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(servletResponse.getHeader("Set-Cookie"))
                .contains("refreshToken=refresh-token", "HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=604800");

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"accessToken\":\"access-token\"");
        assertThat(json).doesNotContain("refresh-token", "refreshToken");

        ArgumentCaptor<OwnerRegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(OwnerRegistrationRequest.class);
        verify(ownerFacade).register(eq("KAKAO_12345"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getNormalizedBusinessNumber()).isEqualTo("1234567890");
        assertThat(requestCaptor.getValue().getStoreImageUrls()).isNull();
    }

    @Test
    void acceptsValidRequest() {
        assertThat(validator.validate(validRequest("1234567890", List.of("https://image.example.com/store.png"))))
                .isEmpty();
    }

    @Test
    void acceptsRequestWithOnlyRequiredFields() {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                "김사장", "치킨플러스", null, 2L, "1234567890", null, null, null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123-45-67890"})
    void acceptsBusinessNumberWithOrWithoutHyphens(String businessNumber) {
        assertThat(validator.validate(validRequest(businessNumber, List.of()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12341453312", "123456789", "abc-de-fghij", "123 45 67890", "123-45-6789"})
    void rejectsMalformedBusinessNumber(String businessNumber) {
        assertThat(validator.validate(validRequest(businessNumber, List.of())))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("businessNumber");
    }

    @Test
    void rejectsMissingRequiredFields() {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                " ", "", null, null, null, null, null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "storeName", "categoryId", "businessNumber");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveCategoryId(long categoryId) {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                "김사장", "치킨플러스", null, categoryId, "1234567890", null, null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("categoryId");
    }

    @Test
    void rejectsTooLongTextFields() {
        String tooLong = "가".repeat(256);
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                tooLong, tooLong, tooLong, 2L, "1234567890", null, tooLong, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("name", "storeName", "storeAddress", "representativeName");
    }

    @Test
    void acceptsTodayButRejectsFutureOpenedAt() {
        OwnerRegistrationRequest today = OwnerRegistrationRequest.of(
                "김사장", "치킨플러스", null, 2L, "1234567890", LocalDate.now(), null, null, null, null);
        OwnerRegistrationRequest tomorrow = OwnerRegistrationRequest.of(
                "김사장", "치킨플러스", null, 2L, "1234567890", LocalDate.now().plusDays(1), null, null, null, null);

        assertThat(validator.validate(today)).isEmpty();
        assertThat(validator.validate(tomorrow))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("openedAt");
    }

    @Test
    void rejectsInvalidImageUrls() {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                "김사장", "치킨플러스", null, 2L, "1234567890", null, null, null,
                List.of("https://image.example.com/store.png", "not-a-url", " "),
                "ftp://image.example.com/profile.png");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("storeImageUrls[1].<list element>", "storeImageUrls[2].<list element>", "profileImageUrl");
    }

    @Test
    void toCommandTrimsValuesAndNormalizesBlankOptionalFields() {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                "  김사장  ",
                " 치킨플러스 ",
                "   ",
                2L,
                "123-45-67890",
                LocalDate.of(2020, 3, 1),
                "",
                " 매장 한 줄 소개 ",
                List.of(" https://image.example.com/store.png "),
                "");

        assertThat(request.getOwnerName()).isEqualTo("김사장");

        CreateOwnerProfileCommand command = request.toCommand("01K58M6PJV8VAJMXHBHJ2PNB5C");

        assertThat(command.getUserId()).isEqualTo("01K58M6PJV8VAJMXHBHJ2PNB5C");
        assertThat(command.getStoreName()).isEqualTo("치킨플러스");
        assertThat(command.getStoreAddress()).isNull();
        assertThat(command.getCategoryId()).isEqualTo(2L);
        assertThat(command.getBusinessNumber()).isEqualTo("1234567890");
        assertThat(command.getOpenedAt()).isEqualTo(LocalDate.of(2020, 3, 1));
        assertThat(command.getRepresentativeName()).isNull();
        assertThat(command.getDescription()).isEqualTo("매장 한 줄 소개");
        assertThat(command.getStoreImageUrls()).containsExactly("https://image.example.com/store.png");
        assertThat(command.getProfileImageUrl()).isNull();
    }

    private OwnerRegistrationRequest validRequest(String businessNumber, List<String> storeImageUrls) {
        return OwnerRegistrationRequest.of(
                "김사장",
                "치킨플러스",
                "서울시 월계1동 광운로23",
                2L,
                businessNumber,
                LocalDate.of(2020, 3, 1),
                "김사장",
                "",
                storeImageUrls,
                null);
    }
}
