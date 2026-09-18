package com.gakkum.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gakkum.backend.common.exception.RestAuthenticationEntryPoint;
import com.gakkum.backend.common.response.ApiResponse;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestReturnsCommonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.error.code").value("COMMON_401"))
            .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/protected")
        ApiResponse<String> protectedEndpoint() {
            return ApiResponse.success("protected");
        }
    }
}
