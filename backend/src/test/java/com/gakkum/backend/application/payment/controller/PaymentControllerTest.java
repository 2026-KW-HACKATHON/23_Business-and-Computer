package com.gakkum.backend.application.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gakkum.backend.application.payment.dto.PaymentPrepareRequest;
import com.gakkum.backend.application.payment.facade.PaymentFacade;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.global.exception.GlobalExceptionHandler;

class PaymentControllerTest {

    private static final String USERNAME = "KAKAO_123";

    private final PaymentFacade paymentFacade = mock(PaymentFacade.class);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(USERNAME, null);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("결제 준비 요청은 인증 사용자와 의뢰 ID를 Facade에 전달하고 주문 정보를 반환한다")
    void preparesPayment() throws Exception {
        when(paymentFacade.preparePayment(eq(USERNAME), eq(11L), any(PaymentPrepareRequest.class)))
                .thenReturn(PreparePaymentResult.of("order-123", 100_000L, "포스터 제작"));

        mockMvc.perform(post("/jobs/11/payments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobApplicationId\":21,\"refundPolicyAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order-123"))
                .andExpect(jsonPath("$.data.amount").value(100000))
                .andExpect(jsonPath("$.data.orderName").value("포스터 제작"));

        ArgumentCaptor<PaymentPrepareRequest> request = ArgumentCaptor.forClass(PaymentPrepareRequest.class);
        verify(paymentFacade).preparePayment(eq(USERNAME), eq(11L), request.capture());
        assertThat(request.getValue().getJobApplicationId()).isEqualTo(21L);
        assertThat(request.getValue().getRefundPolicyAgreed()).isTrue();
    }

    @Test
    @DisplayName("지원서 ID가 없거나 양수가 아니면 400을 반환하고 Facade를 호출하지 않는다")
    void rejectsInvalidApplicationId() throws Exception {
        for (String body : new String[] {
                "{\"refundPolicyAgreed\":true}",
                "{\"jobApplicationId\":0,\"refundPolicyAgreed\":true}",
                "{\"jobApplicationId\":-1,\"refundPolicyAgreed\":true}" }) {
            mockMvc.perform(post("/jobs/11/payments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("약관에 동의하지 않았거나 동의 값이 없으면 400을 반환한다")
    void rejectsMissingAgreement() throws Exception {
        for (String body : new String[] {
                "{\"jobApplicationId\":21}",
                "{\"jobApplicationId\":21,\"refundPolicyAgreed\":false}" }) {
            mockMvc.perform(post("/jobs/11/payments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("양수가 아닌 의뢰 ID는 400을 반환하고 Facade를 호출하지 않는다")
    void rejectsInvalidJobId() throws Exception {
        mockMvc.perform(post("/jobs/0/payments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobApplicationId\":21,\"refundPolicyAgreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        verifyNoInteractions(paymentFacade);
    }
}
