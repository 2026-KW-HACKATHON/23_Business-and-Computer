package com.gakkum.backend.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.gakkum.backend.config.FrontendOrigins;

@DisplayName("소셜 로그인 시작 주소 기억")
class LoginOriginFilterTest {

    private final LoginOriginFilter filter = new LoginOriginFilter(new FrontendOrigins(
            List.of("http://localhost:5173", "https://gakkum.hubspacekw.com"), "https://gakkum.hubspacekw.com"));

    @Test
    @DisplayName("redirect_origin 파라미터의 허용된 주소를 세션에 담고 다음 필터로 넘긴다")
    void storesOriginFromParameter() throws Exception {
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter(LoginOriginFilter.REDIRECT_ORIGIN_PARAMETER, "http://localhost:5173");
        request.addHeader(HttpHeaders.REFERER, "https://gakkum.hubspacekw.com/login");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(storedOrigin(request)).isEqualTo("http://localhost:5173");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("파라미터가 없으면 Referer 의 주소를 담는다")
    void storesOriginFromReferer() throws Exception {
        MockHttpServletRequest request = authorizationRequest();
        request.addHeader(HttpHeaders.REFERER, "https://gakkum.hubspacekw.com/login");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(storedOrigin(request)).isEqualTo("https://gakkum.hubspacekw.com");
    }

    @Test
    @DisplayName("허용 목록 밖의 주소는 담지 않는다")
    void ignoresUnknownOrigin() throws Exception {
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter(LoginOriginFilter.REDIRECT_ORIGIN_PARAMETER, "https://evil.example.com");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("소셜 로그인 시작이 아닌 요청은 건드리지 않는다")
    void skipsOtherPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me/jobs");
        request.setParameter(LoginOriginFilter.REDIRECT_ORIGIN_PARAMETER, "http://localhost:5173");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }

    private MockHttpServletRequest authorizationRequest() {
        return new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
    }

    private Object storedOrigin(MockHttpServletRequest request) {
        return request.getSession().getAttribute(FrontendOrigins.LOGIN_ORIGIN_SESSION_KEY);
    }
}
