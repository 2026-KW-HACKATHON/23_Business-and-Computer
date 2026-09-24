package com.gakkum.backend.domain.auth.client;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.ErrorCode;

@Component
public class NtsBusinessVerificationClient {

    private static final String BASE_URL = "https://api.odcloud.kr/api/nts-businessman/v1";
    private static final DateTimeFormatter NTS_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final String serviceKey;

    @Autowired
    public NtsBusinessVerificationClient(@Value("${nts-businessman.service-key}") String serviceKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
        this.serviceKey = serviceKey;
    }

    NtsBusinessVerificationClient(RestClient restClient, String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    public boolean verify(String businessNumber, LocalDate openedAt, String representativeName) {
        Map<String, Object> body = Map.of("businesses", List.of(Map.of(
                "b_no", businessNumber,
                "start_dt", openedAt.format(NTS_DATE_FORMAT),
                "p_nm", representativeName)));

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/validate")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("returnType", "JSON")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            throw unavailable();
        }

        if (response == null || !"OK".equals(response.get("status_code"))
                || !(response.get("data") instanceof List<?> rows)
                || rows.size() != 1 || !(rows.getFirst() instanceof Map<?, ?> result)) {
            throw unavailable();
        }
        if ("01".equals(result.get("valid"))) {
            return true;
        }
        if ("02".equals(result.get("valid"))) {
            return false;
        }
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.OWNER_BUSINESS_VERIFICATION_UNAVAILABLE);
    }
}
