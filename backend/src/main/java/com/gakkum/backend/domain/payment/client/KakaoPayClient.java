package com.gakkum.backend.domain.payment.client;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.PendingPaymentData;
import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KakaoPayClient {

    private static final String BASE_URL = "https://open-api.kakaopay.com";

    private final RestClient restClient;
    private final String cid;
    private final String secretKey;
    private final String frontendBaseUrl;

    @Autowired
    public KakaoPayClient(
            @Value("${kakao-pay.cid}") String cid,
            @Value("${kakao-pay.secret-key}") String secretKey,
            @Value("${kakao-pay.frontend-base-url}") String frontendBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.cid = cid;
        this.secretKey = secretKey;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    KakaoPayClient(RestClient restClient, String cid, String secretKey, String frontendBaseUrl) {
        this.restClient = restClient;
        this.cid = cid;
        this.secretKey = secretKey;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public ReadyResult ready(PendingPaymentData payment) {
        String itemName = payment.orderName().substring(0, Math.min(payment.orderName().length(), 100));
        Map<String, Object> body = Map.of(
                "cid", cid,
                "partner_order_id", payment.orderId(),
                "partner_user_id", payment.ownerUserId(),
                "item_name", itemName,
                "quantity", 1,
                "total_amount", payment.amount(),
                "tax_free_amount", 0,
                "approval_url", callbackUrl("approval", payment.orderId()),
                "cancel_url", callbackUrl("cancel", payment.orderId()),
                "fail_url", callbackUrl("fail", payment.orderId()));
        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri("/online/v1/payment/ready")
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException exception) {
            try {
                Map<?, ?> error = exception.getResponseBodyAs(Map.class);
                log.warn("KakaoPay ready rejected: HTTP {}, error_code={}, error_message={}",
                        exception.getStatusCode().value(),
                        error == null ? null : error.get("error_code"),
                        error == null ? null : error.get("error_message"));
            } catch (RuntimeException parseException) {
                log.warn("KakaoPay ready rejected: HTTP {}", exception.getStatusCode().value());
            }
            throw unavailable();
        } catch (RestClientException exception) {
            log.warn("KakaoPay ready transport failed: {}", exception.toString());
            throw unavailable();
        }
        if (response == null
                || !(response.get("tid") instanceof String tid) || tid.isBlank() || tid.length() > 20
                || !(response.get("next_redirect_pc_url") instanceof String pcUrl) || pcUrl.isBlank()
                || !(response.get("next_redirect_mobile_url") instanceof String mobileUrl) || mobileUrl.isBlank()) {
            log.warn("KakaoPay ready response is missing a required field");
            throw unavailable();
        }
        return new ReadyResult(tid, pcUrl, mobileUrl);
    }

    public PaymentResult order(String tid) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/online/v1/payment/order")
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("cid", cid, "tid", tid))
                    .retrieve()
                    .body(Map.class);
            return paymentResult(response, true);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE);
        }
    }

    public PaymentResult approve(String tid, String orderId, String ownerUserId, String pgToken) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/online/v1/payment/approve")
                    .header("Authorization", "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "cid", cid,
                            "tid", tid,
                            "partner_order_id", orderId,
                            "partner_user_id", ownerUserId,
                            "pg_token", pgToken))
                    .retrieve()
                    .body(Map.class);
            return paymentResult(response, false);
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_UNAVAILABLE);
        }
    }

    private PaymentResult paymentResult(Map<?, ?> response, boolean orderResponse) {
        if (response == null
                || !(response.get("tid") instanceof String tid)
                || !(response.get("cid") instanceof String responseCid)
                || !(response.get("partner_order_id") instanceof String orderId)
                || !(response.get("partner_user_id") instanceof String ownerUserId)
                || !(response.get("amount") instanceof Map<?, ?> amount)
                || (!(amount.get("total") instanceof Integer) && !(amount.get("total") instanceof Long))
                || (orderResponse && !(response.get("status") instanceof String))) {
            throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
        }
        Number total = (Number) amount.get("total");
        Instant approvedAt = null;
        if (response.get("approved_at") instanceof String value) {
            try {
                approvedAt = LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Seoul")).toInstant();
            } catch (DateTimeParseException exception) {
                throw new BusinessException(ErrorCode.PAYMENT_RESULT_MISMATCH);
            }
        }
        return new PaymentResult(tid, responseCid, orderId, ownerUserId, total.longValue(),
                orderResponse ? (String) response.get("status") : null, approvedAt);
    }

    public String cid() {
        return cid;
    }

    private String callbackUrl(String outcome, String orderId) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/payments/kakao/" + outcome)
                .queryParam("orderId", orderId)
                .build().encode().toUriString();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.PAYMENT_READY_FAILED);
    }

    public record ReadyResult(String tid, String nextRedirectPcUrl, String nextRedirectMobileUrl) {
    }

    public record PaymentResult(String tid, String cid, String orderId, String ownerUserId,
                                Long amount, String status, Instant approvedAt) {
    }
}
