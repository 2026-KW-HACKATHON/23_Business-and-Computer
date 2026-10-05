package com.gakkum.backend.config;

import java.util.List;

import com.gakkum.backend.domain.jwt.service.JwtService;
import com.gakkum.backend.domain.user.service.UserService;
import com.gakkum.backend.filter.JWTFilter;
import com.gakkum.backend.handler.RefreshTokenLogoutHandler;
import com.gakkum.backend.util.JWTUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
                          @Value("${demo-login.enabled:false}") boolean demoLoginEnabled) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.socialSuccessHandler = socialSuccessHandler;
        this.jwtService = jwtService;
        this.jwtFilter = jwtFilter;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
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

        http
                .logout(logout -> logout.addLogoutHandler(new RefreshTokenLogoutHandler(jwtService, jwtUtil)));

        http
                .addFilterBefore(jwtFilter, LogoutFilter.class);

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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
