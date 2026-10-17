package com.gakkum.backend.config;

import java.util.List;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.filter.JWTFilter;
import com.gakkum.backend.filter.LoginOriginFilter;
import com.gakkum.backend.filter.RefreshCookieOriginFilter;
import com.gakkum.backend.handler.ApiLogoutSuccessHandler;
import com.gakkum.backend.handler.RefreshTokenLogoutHandler;
import com.gakkum.backend.util.JWTUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.gakkum.backend.global.exception.RestAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {


    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final AuthenticationSuccessHandler socialSuccessHandler;
    private final JwtService jwtService;
    private final JWTFilter jwtFilter;
    private final JWTUtil jwtUtil;
    private final UserService userService;
    private final FrontendOrigins frontendOrigins;
    private final boolean demoLoginEnabled;

    /**
     * 401 응답을 우리 형식에 맞춰서 주기위한 메서드
     */
    public SecurityConfig(RestAuthenticationEntryPoint authenticationEntryPoint,
                          @Qualifier("SocialSuccessHandler") AuthenticationSuccessHandler socialSuccessHandler,
                          JwtService jwtService,
                          JWTFilter jwtFilter,
                          UserService userService,
                          JWTUtil jwtUtil,
                          FrontendOrigins frontendOrigins,
                          @Value("${demo-login.enabled:false}") boolean demoLoginEnabled) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.socialSuccessHandler = socialSuccessHandler;
        this.jwtService = jwtService;
        this.jwtFilter = jwtFilter;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.frontendOrigins = frontendOrigins;
        this.demoLoginEnabled = demoLoginEnabled;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        http
                .httpBasic(AbstractHttpConfigurer::disable);

        http
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                        .successHandler(socialSuccessHandler));

        // 로그아웃은 POST /logout 만 받는다 (CSRF 를 꺼 두면 기본값이 GET 도 받아 다른 사이트 링크로 로그아웃될 수 있다).
        // access token 없이 refreshToken 쿠키만으로 처리하고, 쿠키를 지운 뒤 공통 응답 200 을 준다
        http
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout"))
                        .addLogoutHandler(new RefreshTokenLogoutHandler(jwtService, jwtUtil))
                        .logoutSuccessHandler(new ApiLogoutSuccessHandler()));

        http
                .addFilterBefore(jwtFilter, LogoutFilter.class);

        // refreshToken 쿠키로 처리하는 /refresh · /logout · /jwt/exchange 와 쿠키를 심는 /demo/login 은
        // 허용된 프론트에서 보낸 요청만 받는다 (CSRF 를 대신한다).
        // CORS 처리보다 앞에 두어 다른 사이트에서 보낸 요청은 CORS 설정과 상관없이 공통 오류 응답 403 으로 거부한다
        http
                .addFilterBefore(new RefreshCookieOriginFilter(frontendOrigins), CorsFilter.class);

        // 소셜 로그인을 시작한 프론트를 기억해 두었다가 로그인이 끝나면 그쪽으로 돌려보낸다 (SocialSuccessHandler)
        http
                .addFilterBefore(new LoginOriginFilter(frontendOrigins), OAuth2AuthorizationRequestRedirectFilter.class);

        http
            .authorizeHttpRequests(authorize -> {
                authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/oauth2/authorization/kakao", "/login/oauth2/code/kakao").permitAll()
                    .requestMatchers(HttpMethod.POST, "/jwt/exchange", "/refresh").permitAll();
                // 데모 로그인은 demo-login.enabled가 켜진 서버에서만 공개한다
                if (demoLoginEnabled) {
                    authorize.requestMatchers(HttpMethod.POST, "/demo/login").permitAll();
                }
                authorize.anyRequest().authenticated();
            })
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
            );

        return http.build();
    }

    /**
     * JWTFilter는 시큐리티 필터 체인 안에서만 실행한다.
     * 서블릿 필터로도 자동 등록되면 체인보다 먼저 실행된 뒤 체인 안에서는 건너뛰어져 인증 정보가 사라질 수 있다.
     */
    @Bean
    public FilterRegistrationBean<JWTFilter> jwtFilterRegistration(JWTFilter jwtFilter) {
        FilterRegistrationBean<JWTFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    /** 브라우저가 preflight 허용 결과를 기억하는 시간(초). Chrome 은 2시간까지만 기억한다 */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 7200L;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = frontendCorsConfiguration();

        // 브라우저가 내려받는 파일명을 읽을 수 있게 다운로드 경로에서만 Content-Disposition을 노출한다. 허용 출처와 메서드는 같다
        CorsConfiguration downloadConfiguration = frontendCorsConfiguration();
        downloadConfiguration.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));

        // 먼저 등록한 경로가 먼저 적용되므로 다운로드 경로를 /** 앞에 둔다
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/jobs/submissions/download", downloadConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 프론트 허용 목록(FrontendOrigins)으로만 출처를 판정하는 CORS 설정. 소셜 로그인 뒤 돌아갈 주소와 같은 규칙이다.
     * Spring 의 allowedOriginPatterns 는 * 를 아무 글자로 풀어 http://192.168.evil.com:5173 도 허용하므로 쓰지 않는다.
     */
    private CorsConfiguration frontendCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration() {
            @Override
            public String checkOrigin(String origin) {
                return frontendOrigins.isAllowed(origin) ? origin : null;
            }
        };
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // 프론트와 API 도메인이 달라 Authorization 을 단 요청마다 preflight 가 먼저 나간다. 값이 없으면 Chrome 은 5초만
        // 기억해 화면을 열 때마다 왕복이 한 번 더 붙으므로, Chrome 이 허용하는 최대인 2시간 동안 기억하게 한다
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);
        return configuration;
    }
}
