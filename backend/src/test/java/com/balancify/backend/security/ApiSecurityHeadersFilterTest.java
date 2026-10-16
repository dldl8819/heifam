package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiSecurityHeadersFilterTest {

    private final ApiSecurityHeadersFilter filter = new ApiSecurityHeadersFilter();

    @Test
    void marksEveryAnswerAsDataNeverToBeFramedOrSniffed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/1/ranking");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; frame-ancestors 'none'");
        verify(chain).doFilter(request, response);
    }

    @Test
    void setsTheHeadersOutsideTheApiToo() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void asksForHttpsOnlyOnAnswersThatCameOverHttps() throws Exception {
        MockHttpServletRequest plain = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse plainResponse = new MockHttpServletResponse();
        filter.doFilter(plain, plainResponse, mock(FilterChain.class));

        MockHttpServletRequest forwarded = new MockHttpServletRequest("GET", "/api/health");
        forwarded.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse forwardedResponse = new MockHttpServletResponse();
        filter.doFilter(forwarded, forwardedResponse, mock(FilterChain.class));

        MockHttpServletRequest secure = new MockHttpServletRequest("GET", "/api/health");
        secure.setSecure(true);
        MockHttpServletResponse secureResponse = new MockHttpServletResponse();
        filter.doFilter(secure, secureResponse, mock(FilterChain.class));

        assertThat(plainResponse.getHeader("Strict-Transport-Security")).isNull();
        assertThat(forwardedResponse.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000");
        assertThat(secureResponse.getHeader("Strict-Transport-Security")).isEqualTo("max-age=31536000");
    }
}
