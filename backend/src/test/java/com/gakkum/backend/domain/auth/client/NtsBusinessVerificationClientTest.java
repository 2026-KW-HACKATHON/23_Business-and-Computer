package com.gakkum.backend.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@DisplayName("국세청 사업자등록정보 진위 확인 클라이언트")
class NtsBusinessVerificationClientTest {

    private static final String URL = "https://api.odcloud.kr/api/nts-businessman/v1/validate"
            + "?serviceKey=test-key&returnType=JSON";

    private MockRestServiceServer server;
    private NtsBusinessVerificationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.odcloud.kr/api/nts-businessman/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NtsBusinessVerificationClient(builder.build(), "test-key");
    }

    @Test
    @DisplayName("사업자번호와 개업일을 국세청 형식으로 보내고 일치 결과를 반환한다")
    void sendsNtsRequestAndReturnsMatch() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"businesses":[{"b_no":"1234567890","start_dt":"20200301","p_nm":"김사장"}]}
                        """))
                .andRespond(withSuccess("{" +
                        "\"status_code\":\"OK\",\"data\":[{\"valid\":\"01\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify("1234567890", LocalDate.of(2020, 3, 1), "김사장")).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("국세청 정보가 불일치하면 false를 반환한다")
    void returnsFalseForMismatch() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status_code\":\"OK\",\"data\":[{\"valid\":\"02\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify("1234567890", LocalDate.of(2020, 3, 1), "김사장")).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("외부 서버 오류는 공통 서비스 장애로 반환한다")
    void mapsUpstreamFailureToServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertUnavailable();
        server.verify();
    }

    @Test
    @DisplayName("외부 연결 오류는 공통 서비스 장애로 반환한다")
    void mapsConnectionFailureToServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("connection failed");
        });

        assertUnavailable();
        server.verify();
    }

    @Test
    @DisplayName("해석할 수 없는 외부 응답은 성공으로 처리하지 않는다")
    void rejectsUnexpectedResponse() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status_code\":\"OK\",\"data\":[{}]}",
                        MediaType.APPLICATION_JSON));

        assertUnavailable();
        server.verify();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> client.verify("1234567890", LocalDate.of(2020, 3, 1), "김사장"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.OWNER_BUSINESS_VERIFICATION_UNAVAILABLE));
    }
}
