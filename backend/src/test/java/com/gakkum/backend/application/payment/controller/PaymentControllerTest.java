package com.gakkum.backend.application.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
import com.gakkum.backend.domain.payment.dto.PaymentHistoryStatus;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PaymentHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PreparePaymentResult;
import com.gakkum.backend.domain.job.entity.JobStatus;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PrepareProposalPaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.ApprovedOrderData;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryItemResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryMonthResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistoryResult;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.SettlementHistorySummaryResult;
import com.gakkum.backend.domain.payment.dto.SettlementHistoryStatus;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;
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
                .thenReturn(PreparePaymentResult.of("order-123", 100_000L, "포스터 제작",
                        "https://pay.example/pc", "https://pay.example/mobile"));

        mockMvc.perform(post("/jobs/11/payments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobApplicationId\":21,\"refundPolicyAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order-123"))
                .andExpect(jsonPath("$.data.amount").value(100000))
                .andExpect(jsonPath("$.data.orderName").value("포스터 제작"))
                .andExpect(jsonPath("$.data.nextRedirectPcUrl").value("https://pay.example/pc"))
                .andExpect(jsonPath("$.data.nextRedirectMobileUrl").value("https://pay.example/mobile"));

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

    @Test
    @DisplayName("제안 결제 준비 요청은 인증 사용자·제안 ID·작업비·수정 횟수·한마디를 전달하고 기존 결제 준비 형식으로 주문 정보를 반환한다")
    void preparesProposalPayment() throws Exception {
        when(paymentFacade.prepareProposalPayment(any(PrepareProposalPaymentCommand.class)))
                .thenReturn(PreparePaymentResult.of("order-123", 120_000L, "메뉴판 개선 제안",
                        "https://pay.example/pc", "https://pay.example/mobile"));

        mockMvc.perform(post("/proposals/31/payments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":120000,\"revisionCount\":2,\"messageToStudent\":\"  매장 분위기에 맞춰 주세요.  \","
                                + "\"refundPolicyAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order-123"))
                .andExpect(jsonPath("$.data.amount").value(120000))
                .andExpect(jsonPath("$.data.orderName").value("메뉴판 개선 제안"))
                .andExpect(jsonPath("$.data.nextRedirectPcUrl").value("https://pay.example/pc"))
                .andExpect(jsonPath("$.data.nextRedirectMobileUrl").value("https://pay.example/mobile"));

        ArgumentCaptor<PrepareProposalPaymentCommand> command =
                ArgumentCaptor.forClass(PrepareProposalPaymentCommand.class);
        verify(paymentFacade).prepareProposalPayment(command.capture());
        assertThat(command.getValue().getUsername()).isEqualTo(USERNAME);
        assertThat(command.getValue().getProposalId()).isEqualTo(31L);
        assertThat(command.getValue().getBudget()).isEqualTo(120_000L);
        assertThat(command.getValue().getRevisionCount()).isEqualTo(2);
        assertThat(command.getValue().getMessageToStudent()).isEqualTo("매장 분위기에 맞춰 주세요.");
    }

    @Test
    @DisplayName("제안 결제의 한마디가 없거나 비어 있으면 입력하지 않은 것으로 전달하고 수정 횟수 0도 허용한다")
    void treatsBlankProposalMessageAsMissing() throws Exception {
        when(paymentFacade.prepareProposalPayment(any(PrepareProposalPaymentCommand.class)))
                .thenReturn(PreparePaymentResult.of("order-123", 50_000L, "메뉴판 개선 제안", "pc", "mobile"));

        for (String body : new String[] {
                "{\"budget\":120000,\"revisionCount\":0,\"refundPolicyAgreed\":true}",
                "{\"budget\":120000,\"revisionCount\":0,\"messageToStudent\":\"\",\"refundPolicyAgreed\":true}",
                "{\"budget\":120000,\"revisionCount\":0,\"messageToStudent\":\"   \",\"refundPolicyAgreed\":true}" }) {
            mockMvc.perform(post("/proposals/31/payments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        ArgumentCaptor<PrepareProposalPaymentCommand> command =
                ArgumentCaptor.forClass(PrepareProposalPaymentCommand.class);
        verify(paymentFacade, org.mockito.Mockito.times(3)).prepareProposalPayment(command.capture());
        assertThat(command.getAllValues()).allSatisfy(value -> {
            assertThat(value.getRevisionCount()).isZero();
            assertThat(value.getMessageToStudent()).isNull();
        });
    }

    @Test
    @DisplayName("제안 결제의 작업비가 없거나 null·0·음수이거나, 수정 횟수가 없거나 음수이거나, 환불 정책에 동의하지 않았거나, 제안 ID가 양수가 아니면 400을 반환한다")
    void rejectsInvalidProposalPaymentRequest() throws Exception {
        for (String body : new String[] {
                "{\"revisionCount\":2,\"refundPolicyAgreed\":true}",
                "{\"budget\":null,\"revisionCount\":2,\"refundPolicyAgreed\":true}",
                "{\"budget\":0,\"revisionCount\":2,\"refundPolicyAgreed\":true}",
                "{\"budget\":-1,\"revisionCount\":2,\"refundPolicyAgreed\":true}",
                "{\"budget\":120000,\"refundPolicyAgreed\":true}",
                "{\"budget\":120000,\"revisionCount\":-1,\"refundPolicyAgreed\":true}",
                "{\"budget\":120000,\"revisionCount\":2}",
                "{\"budget\":120000,\"revisionCount\":2,\"refundPolicyAgreed\":false}" }) {
            mockMvc.perform(post("/proposals/31/payments")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        mockMvc.perform(post("/proposals/0/payments")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":120000,\"revisionCount\":2,\"refundPolicyAgreed\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("제안 결제 승인은 기존 응답에 수락 대기 의뢰 ID와 상태를 더해 반환한다")
    void approvesProposalPayment() throws Exception {
        when(paymentFacade.approvePayment(USERNAME, "order-123", "pg-123"))
                .thenReturn(new ApprovedOrderData("order-123", 50_000L, Instant.parse("2026-10-05T03:00:00Z"),
                        42L, JobStatus.AWAITING_START));

        mockMvc.perform(post("/payments/order-123/approve")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgToken\":\"pg-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.jobStatus").value("AWAITING_START"));
    }

    @Test
    @DisplayName("승인 요청은 인증 사용자와 주문번호 및 토큰을 전달하고 PAID를 반환한다")
    void approvesPayment() throws Exception {
        when(paymentFacade.approvePayment(USERNAME, "order-123", "pg-123"))
                .thenReturn(new ApprovedOrderData("order-123", 100_000L, Instant.parse("2026-09-26T00:00:00Z"),
                        42L, JobStatus.MATCHED));

        mockMvc.perform(post("/payments/order-123/approve")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgToken\":\"pg-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value("order-123"))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(100000))
                .andExpect(jsonPath("$.data.approvedAt").value("2026-09-26T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.jobId").value(42))
                .andExpect(jsonPath("$.data.jobStatus").value("MATCHED"));
        verify(paymentFacade).approvePayment(USERNAME, "order-123", "pg-123");
    }

    @Test
    @DisplayName("승인 토큰이 없거나 공백이면 400을 반환한다")
    void rejectsMissingPgToken() throws Exception {
        for (String body : new String[] {"{}", "{\"pgToken\":\" \"}"}) {
            mockMvc.perform(post("/payments/order-123/approve")
                            .principal(authentication)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 내역 조회는 인증 사용자를 Facade에 전달하고 요약 금액과 월별 결제 내역을 반환한다")
    void returnsPaymentHistory() throws Exception {
        Payment payment = Payment.pending(42L, 21L, "owner-123", "order-123", 100_000L, Instant.EPOCH);
        payment.recordKakaoTid("T1234567890123456789");
        payment.approve(Instant.parse("2026-10-02T03:00:00Z"));
        Payment refunded = Payment.pending(41L, 20L, "owner-123", "order-122", 100_000L, Instant.EPOCH);
        refunded.recordKakaoTid("T1234567890123456788");
        refunded.approve(Instant.parse("2026-10-01T03:00:00Z"));
        refunded.refundOnCancel(Instant.parse("2026-10-05T03:00:00Z"));
        Payment settled = Payment.pending(40L, 19L, "owner-123", "order-121", 100_000L, Instant.EPOCH);
        settled.recordKakaoTid("T1234567890123456787");
        settled.approve(Instant.parse("2026-10-01T02:00:00Z"));
        when(paymentFacade.getPaymentHistory(USERNAME)).thenReturn(PaymentHistoryResult.of(
                PaymentHistorySummaryResult.of(300_000L, 100_000L, 3_000_000_000L), List.of(
                PaymentHistoryMonthResult.of("2026-10", List.of(
                        PaymentHistoryItemResult.of(
                                PaymentHistoryData.from(payment), "매장 홍보 포스터 제작", 0L, "김학생",
                                PaymentHistoryStatus.HELD, null, null),
                        PaymentHistoryItemResult.of(
                                PaymentHistoryData.from(refunded), "메뉴판 디자인", 80_000L, "김학생",
                                PaymentHistoryStatus.PARTIALLY_REFUNDED, null, LocalDate.of(2026, 10, 5)),
                        PaymentHistoryItemResult.of(
                                PaymentHistoryData.from(settled), "로고 제작", 0L, "김학생",
                                PaymentHistoryStatus.SETTLED, LocalDate.of(2026, 10, 8), null))))));

        mockMvc.perform(get("/payments").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.summary.length()").value(3))
                .andExpect(jsonPath("$.data.summary.thisMonthPaymentAmount").value(300000))
                .andExpect(jsonPath("$.data.summary.heldAmount").value(100000))
                .andExpect(jsonPath("$.data.summary.totalSettledAmount").value(3000000000L))
                .andExpect(jsonPath("$.data.months.length()").value(1))
                .andExpect(jsonPath("$.data.months[0].yearMonth").value("2026-10"))
                .andExpect(jsonPath("$.data.months[0].payments.length()").value(3))
                .andExpect(jsonPath("$.data.months[0].payments[0].length()").value(9))
                .andExpect(jsonPath("$.data.months[0].payments[0].jobId").value(42))
                .andExpect(jsonPath("$.data.months[0].payments[0].title").value("매장 홍보 포스터 제작"))
                .andExpect(jsonPath("$.data.months[0].payments[0].amount").value(100000))
                .andExpect(jsonPath("$.data.months[0].payments[0].refundAmount").value(0))
                .andExpect(jsonPath("$.data.months[0].payments[0].approvedAt").value("2026-10-02T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.months[0].payments[0].studentName").value("김학생"))
                .andExpect(jsonPath("$.data.months[0].payments[0].status").value("HELD"))
                // 해당하지 않는 날짜는 필드를 생략하지 않고 null로 내려준다
                .andExpect(jsonPath("$.data.months[0].payments[0]", hasEntry("settledDate", null)))
                .andExpect(jsonPath("$.data.months[0].payments[0]", hasEntry("refundedDate", null)))
                .andExpect(jsonPath("$.data.months[0].payments[1].length()").value(9))
                .andExpect(jsonPath("$.data.months[0].payments[1].status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.months[0].payments[1]", hasEntry("settledDate", null)))
                .andExpect(jsonPath("$.data.months[0].payments[1].refundedDate").value("2026-10-05"))
                .andExpect(jsonPath("$.data.months[0].payments[2].length()").value(9))
                .andExpect(jsonPath("$.data.months[0].payments[2].status").value("SETTLED"))
                .andExpect(jsonPath("$.data.months[0].payments[2].settledDate").value("2026-10-08"))
                .andExpect(jsonPath("$.data.months[0].payments[2]", hasEntry("refundedDate", null)));
        verify(paymentFacade).getPaymentHistory(USERNAME);
    }

    @Test
    @DisplayName("결제 내역이 없으면 200과 0원 요약, 빈 월 목록을 반환한다")
    void returnsEmptyPaymentHistory() throws Exception {
        when(paymentFacade.getPaymentHistory(USERNAME)).thenReturn(
                PaymentHistoryResult.of(PaymentHistorySummaryResult.of(0L, 0L, 0L), List.of()));

        mockMvc.perform(get("/payments").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.thisMonthPaymentAmount").value(0))
                .andExpect(jsonPath("$.data.summary.heldAmount").value(0))
                .andExpect(jsonPath("$.data.summary.totalSettledAmount").value(0))
                .andExpect(jsonPath("$.data.months").isArray())
                .andExpect(jsonPath("$.data.months").isEmpty());
    }

    @Test
    @DisplayName("사장님이 아닌 사용자의 결제 내역 조회는 403을, 잠긴 사용자는 401을 반환한다")
    void rejectsPaymentHistoryForNonOwnerAndLockedUser() throws Exception {
        when(paymentFacade.getPaymentHistory(USERNAME))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_LIST_OWNER_REQUIRED))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/payments").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_403_LIST_OWNER"));
        mockMvc.perform(get("/payments").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("정산 내역 조회는 인증 사용자를 Facade에 전달하고 요약 금액과 월별 정산 내역을 반환한다")
    void returnsSettlementHistory() throws Exception {
        when(paymentFacade.getSettlementHistory(USERNAME)).thenReturn(SettlementHistoryResult.of(
                SettlementHistorySummaryResult.of(120_000L, 100_000L, 3_000_000_000L), List.of(
                SettlementHistoryMonthResult.of("2026-10", List.of(
                        SettlementHistoryItemResult.of(43L, "매장 홍보 포스터 제작", 100_000L, null, "가꿈 카페",
                                SettlementHistoryStatus.SCHEDULED),
                        SettlementHistoryItemResult.of(42L, "메뉴판 디자인", 20_000L, LocalDate.of(2026, 10, 5),
                                "가꿈 분식", SettlementHistoryStatus.START_COMPENSATION))))));

        mockMvc.perform(get("/settlements").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.summary.length()").value(3))
                .andExpect(jsonPath("$.data.summary.thisMonthWorkAmount").value(120000))
                .andExpect(jsonPath("$.data.summary.scheduledAmount").value(100000))
                .andExpect(jsonPath("$.data.summary.totalSettledAmount").value(3000000000L))
                .andExpect(jsonPath("$.data.months.length()").value(1))
                .andExpect(jsonPath("$.data.months[0].yearMonth").value("2026-10"))
                .andExpect(jsonPath("$.data.months[0].settlements.length()").value(2))
                .andExpect(jsonPath("$.data.months[0].settlements[0].length()").value(6))
                .andExpect(jsonPath("$.data.months[0].settlements[0].jobId").value(43))
                .andExpect(jsonPath("$.data.months[0].settlements[0].title").value("매장 홍보 포스터 제작"))
                .andExpect(jsonPath("$.data.months[0].settlements[0].amount").value(100000))
                .andExpect(jsonPath("$.data.months[0].settlements[0].storeName").value("가꿈 카페"))
                .andExpect(jsonPath("$.data.months[0].settlements[0].status").value("SCHEDULED"))
                // 정산 예정의 날짜는 필드를 생략하지 않고 명시적인 null로 내려간다
                .andExpect(content().string(containsString("\"settledDate\":null")))
                .andExpect(jsonPath("$.data.months[0].settlements[1].amount").value(20000))
                .andExpect(jsonPath("$.data.months[0].settlements[1].settledDate").value("2026-10-05"))
                .andExpect(jsonPath("$.data.months[0].settlements[1].status").value("START_COMPENSATION"));
        verify(paymentFacade).getSettlementHistory(USERNAME);
    }

    @Test
    @DisplayName("정산 내역이 없으면 200과 0원 요약, 빈 월 목록을 반환한다")
    void returnsEmptySettlementHistory() throws Exception {
        when(paymentFacade.getSettlementHistory(USERNAME)).thenReturn(
                SettlementHistoryResult.of(SettlementHistorySummaryResult.of(0L, 0L, 0L), List.of()));

        mockMvc.perform(get("/settlements").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.thisMonthWorkAmount").value(0))
                .andExpect(jsonPath("$.data.summary.scheduledAmount").value(0))
                .andExpect(jsonPath("$.data.summary.totalSettledAmount").value(0))
                .andExpect(jsonPath("$.data.months").isArray())
                .andExpect(jsonPath("$.data.months").isEmpty());
    }

    @Test
    @DisplayName("학생이 아닌 사용자의 정산 내역 조회는 403을, 잠긴 사용자는 401을, 데이터 불일치는 500을 반환한다")
    void rejectsSettlementHistoryForNonStudentLockedUserAndBrokenData() throws Exception {
        when(paymentFacade.getSettlementHistory(USERNAME))
                .thenThrow(new BusinessException(ErrorCode.SETTLEMENT_LIST_STUDENT_REQUIRED))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        mockMvc.perform(get("/settlements").principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_403_LIST_STUDENT"));
        mockMvc.perform(get("/settlements").principal(authentication))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_401"));
        mockMvc.perform(get("/settlements").principal(authentication))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }
}
