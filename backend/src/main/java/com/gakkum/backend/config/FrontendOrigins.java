package com.gakkum.backend.config;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 프론트 주소 허용 목록. CORS와 소셜 로그인 뒤 돌아갈 주소가 같은 목록을 쓴다.
 * 로그인은 시작한 프론트(배포 사이트 · 로컬 개발 서버)로 돌려보내고, 목록에 없거나 알 수 없으면 기본 주소로 보낸다.
 * 목록의 주소는 정확히 같아야 하고, *는 IP 주소의 숫자 한 칸(0~255)에만 맞는다 (예: http://192.168.*.*:5173).
 * Spring 패턴처럼 *가 아무 글자에나 맞으면 http://192.168.evil.com:5173 같은 남의 도메인도 허용되기 때문이다.
 */
@Component
public class FrontendOrigins {

    /** 소셜 로그인을 시작한 프론트 주소를 로그인이 끝날 때까지 담아 두는 세션 속성 */
    public static final String LOGIN_ORIGIN_SESSION_KEY = FrontendOrigins.class.getName() + ".LOGIN_ORIGIN";

    /** 허용 목록의 * 한 칸에 맞는 값: IP 주소의 숫자 한 칸 (0~255) */
    private static final String IP_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";

    private final List<Pattern> allowedOrigins;
    private final String defaultOrigin;

    public FrontendOrigins(
            @Value("${frontend.allowed-origin-patterns:http://localhost:5173,https://gakkum.hubspacekw.com}")
            List<String> allowedOriginPatterns,
            @Value("${frontend.default-origin:https://gakkum.hubspacekw.com}") String defaultOrigin) {
        this.allowedOrigins = allowedOriginPatterns.stream()
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .map(FrontendOrigins::toPattern)
                .toList();
        this.defaultOrigin = trimTrailingSlash(defaultOrigin);
    }

    public boolean isAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        String candidate = trimTrailingSlash(origin.trim());
        return allowedOrigins.stream().anyMatch(pattern -> pattern.matcher(candidate).matches());
    }

    /** 허용된 주소면 그대로, 아니면 기본 주소 */
    public String resolve(String origin) {
        return isAllowed(origin) ? origin : defaultOrigin;
    }

    /**
     * 주소에서 scheme://host[:port] 만 뽑는다 (Referer 처럼 경로가 붙은 주소용).
     * @return 읽을 수 없는 주소면 null
     */
    public static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 목록의 주소 하나를 정규식으로. * 밖의 글자는 그대로 비교하고(대소문자 무시), * 는 숫자 한 칸 */
    private static Pattern toPattern(String allowedOrigin) {
        String[] parts = trimTrailingSlash(allowedOrigin).split("\\*", -1);
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(IP_OCTET);
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
