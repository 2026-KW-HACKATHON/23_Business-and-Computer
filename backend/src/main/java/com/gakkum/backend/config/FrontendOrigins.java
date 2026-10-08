package com.gakkum.backend.config;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

/**
 * 프론트 주소 허용 목록. CORS와 소셜 로그인 뒤 돌아갈 주소가 같은 목록을 쓴다.
 * 로그인은 시작한 프론트(배포 사이트 · 로컬 개발 서버)로 돌려보내고, 목록에 없거나 알 수 없으면 기본 주소로 보낸다.
 */
@Component
public class FrontendOrigins {

    /** 소셜 로그인을 시작한 프론트 주소를 로그인이 끝날 때까지 담아 두는 세션 속성 */
    public static final String LOGIN_ORIGIN_SESSION_KEY = FrontendOrigins.class.getName() + ".LOGIN_ORIGIN";

    private final List<String> allowedOriginPatterns;
    private final String defaultOrigin;
    private final CorsConfiguration matcher = new CorsConfiguration();

    public FrontendOrigins(
            @Value("${frontend.allowed-origin-patterns:http://localhost:5173,http://192.168.*.*:5173,https://gakkum.hubspacekw.com}")
            List<String> allowedOriginPatterns,
            @Value("${frontend.default-origin:https://gakkum.hubspacekw.com}") String defaultOrigin) {
        this.allowedOriginPatterns = List.copyOf(allowedOriginPatterns);
        this.defaultOrigin = trimTrailingSlash(defaultOrigin);
        this.matcher.setAllowedOriginPatterns(this.allowedOriginPatterns);
    }

    public List<String> allowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public boolean isAllowed(String origin) {
        return origin != null && matcher.checkOrigin(origin) != null;
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

    private static String trimTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }
}
