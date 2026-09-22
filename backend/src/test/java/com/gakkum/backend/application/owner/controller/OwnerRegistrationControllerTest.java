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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.gakkum.backend.application.owner.dto.OwnerRegistrationCommand;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationRequest;
import com.gakkum.backend.application.owner.dto.OwnerRegistrationResponse;
import com.gakkum.backend.application.owner.facade.OwnerRegistrationFacade;
import com.gakkum.backend.global.response.ApiResponse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

class OwnerRegistrationControllerTest {

    private final OwnerRegistrationFacade ownerRegistrationFacade = mock(OwnerRegistrationFacade.class);
    private final OwnerRegistrationController controller =
            new OwnerRegistrationController(ownerRegistrationFacade);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void returnsAccessTokenAndSetsRefreshTokenCookie() throws Exception {
        when(ownerRegistrationFacade.register(eq("KAKAO_12345"), any(OwnerRegistrationCommand.class)))
                .thenReturn(OwnerRegistrationResponse.of("access-token", "refresh-token"));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        ApiResponse<OwnerRegistrationResponse> response = controller.register(
                new UsernamePasswordAuthenticationToken("KAKAO_12345", null),
                validRequest("123-45-67890", null),
                servletResponse);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAccessToken()).isEqualTo("access-token");
        assertThat(servletResponse.getHeader("Set-Cookie"))
                .contains("refreshToken=refresh-token", "HttpOnly", "SameSite=Lax", "Path=/");

        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"accessToken\":\"access-token\"");
        assertThat(json).doesNotContain("refresh-token", "refreshToken");

        ArgumentCaptor<OwnerRegistrationCommand> commandCaptor =
                ArgumentCaptor.forClass(OwnerRegistrationCommand.class);
        verify(ownerRegistrationFacade).register(eq("KAKAO_12345"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().getBusinessNumber()).isEqualTo("1234567890");
        assertThat(commandCaptor.getValue().getStoreImageUrls()).isEmpty();
    }

    @Test
    void acceptsBusinessNumberWithOrWithoutHyphens() {
        assertThat(validator.validate(validRequest("1234567890", List.of()))).isEmpty();
        assertThat(validator.validate(validRequest("123-45-67890", List.of()))).isEmpty();
    }

    @Test
    void rejectsInvalidBusinessNumberCategoryAndImageUrl() {
        OwnerRegistrationRequest request = OwnerRegistrationRequest.of(
                "김사장",
                "치킨플러스",
                null,
                null,
                "1234",
                LocalDate.now().plusDays(1),
                null,
                null,
                List.of("not-a-url"),
                null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("categoryId", "businessNumber", "openedAt", "storeImageUrls[0].<list element>");
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
