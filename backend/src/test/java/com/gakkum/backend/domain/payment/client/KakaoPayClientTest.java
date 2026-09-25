package com.gakkum.backend.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.gakkum.backend.domain.payment.client.KakaoPayClient.ReadyResult;
import com.gakkum.backend.domain.payment.client.KakaoPayClient.PaymentResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

class KakaoPayClientTest {

    private static final String URL = "https://open-api.kakaopay.com/online/v1/payment/ready";
    private static final PendingPaymentData PAYMENT =
            new PendingPaymentData("order-123", 100_000L, "포스터 제작", "owner-123");

    private MockRestServiceServer server;
    private KakaoPayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://open-api.kakaopay.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoPayClient(builder.build(), "TC0ONETIME", "test-secret", "http://localhost:5173/");
    }

    @Test
    @DisplayName("서버 주문 정보와 콜백 URL을 카카오페이에 보내고 PC 및 모바일 결제 URL을 반환한다")
    void sendsReadyRequest() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "SECRET_KEY test-secret"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "cid":"TC0ONETIME",
                          "partner_order_id":"order-123",
                          "partner_user_id":"owner-123",
                          "item_name":"포스터 제작",
                          "quantity":1,
                          "total_amount":100000,
                          "tax_free_amount":0,
                          "approval_url":"http://localhost:5173/payments/kakao/approval?orderId=order-123",
                          "cancel_url":"http://localhost:5173/payments/kakao/cancel?orderId=order-123",
                          "fail_url":"http://localhost:5173/payments/kakao/fail?orderId=order-123"
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {"tid":"T1234567890123456789",
                         "next_redirect_pc_url":"https://pay.example/pc",
                         "next_redirect_mobile_url":"https://pay.example/mobile"}
                        """, MediaType.APPLICATION_JSON));

        ReadyResult result = client.ready(PAYMENT);

        assertThat(result.tid()).isEqualTo("T1234567890123456789");
        assertThat(result.nextRedirectPcUrl()).isEqualTo("https://pay.example/pc");
        assertThat(result.nextRedirectMobileUrl()).isEqualTo("https://pay.example/mobile");
        server.verify();
    }

    @Test
    @DisplayName("의뢰 제목은 카카오페이 요청에서 100자로 제한한다")
    void truncatesItemName() {
        server.expect(requestTo(URL))
                .andExpect(content().json("{\"item_name\":\"" + "가".repeat(100) + "\"}"))
                .andRespond(withSuccess("""
                        {"tid":"T1234567890123456789",
                         "next_redirect_pc_url":"https://pay.example/pc",
                         "next_redirect_mobile_url":"https://pay.example/mobile"}
                        """, MediaType.APPLICATION_JSON));

        client.ready(new PendingPaymentData("order-123", 100_000L, "가".repeat(101), "owner-123"));
        server.verify();
    }

    @Test
    @DisplayName("카카오페이 서버 오류는 내부 정보 없는 준비 실패로 변환한다")
    void mapsProviderFailure() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertReadyFailed();
        server.verify();
    }

    @Test
    @DisplayName("카카오페이 연결 오류는 준비 실패로 변환한다")
    void mapsConnectionFailure() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("connection failed");
        });

        assertReadyFailed();
        server.verify();
    }

    @Test
    @DisplayName("거래번호나 모바일 결제 URL이 없는 응답은 거절한다")
    void rejectsIncompleteResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"tid\":\"T1234567890123456789\",\"next_redirect_pc_url\":\"https://pay.example/pc\"}",
                MediaType.APPLICATION_JSON));

        assertReadyFailed();
        server.verify();
    }

    @Test
    @DisplayName("저장된 거래번호로 주문 상태를 조회한다")
    void queriesOrder() {
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/order"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"cid\":\"TC0ONETIME\",\"tid\":\"T123\"}"))
                .andRespond(withSuccess(paymentResponse("SUCCESS_PAYMENT"), MediaType.APPLICATION_JSON));

        PaymentResult result = client.order("T123");

        assertThat(result.status()).isEqualTo("SUCCESS_PAYMENT");
        assertThat(result.amount()).isEqualTo(100_000L);
        assertThat(result.approvedAt()).isEqualTo(java.time.Instant.parse("2026-09-26T03:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("주문 정보와 토큰으로 카카오페이에 승인을 요청한다")
    void approvesPayment() {
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/approve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "SECRET_KEY test-secret"))
                .andExpect(content().json("""
                        {"cid":"TC0ONETIME","tid":"T123","partner_order_id":"order-123",
                         "partner_user_id":"owner-123","pg_token":"pg-123"}
                        """, true))
                .andRespond(withSuccess(paymentResponse(null), MediaType.APPLICATION_JSON));

        PaymentResult result = client.approve("T123", "order-123", "owner-123", "pg-123");

        assertThat(result.orderId()).isEqualTo("order-123");
        assertThat(result.approvedAt()).isNotNull();
        server.verify();
    }

    @Test
    @DisplayName("승인 요청의 서버 오류는 결과 불명 오류로 변환한다")
    void approvalFailureIsUncertain() {
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/approve"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.approve("T123", "order-123", "owner-123", "pg-123"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE));
        server.verify();
    }

    private String paymentResponse(String status) {
        return """
                {"tid":"T123","cid":"TC0ONETIME","partner_order_id":"order-123",
                 "partner_user_id":"owner-123","amount":{"total":100000},
                 "approved_at":"2026-09-26T12:00:00"%s}
                """.formatted(status == null ? "" : ",\"status\":\"" + status + "\"");
    }

    private void assertReadyFailed() {
        assertThatThrownBy(() -> client.ready(PAYMENT))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_READY_FAILED));
    }
}
