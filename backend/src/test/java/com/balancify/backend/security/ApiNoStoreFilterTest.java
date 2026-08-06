package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiNoStoreFilterTest {

    private final ApiNoStoreFilter filter = new ApiNoStoreFilter();

    @Test
    void preventsCachingForApiResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups/1/ranking");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesNonApiResponsesUnchanged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Cache-Control")).isNull();
        verify(chain).doFilter(request, response);
    }
}
