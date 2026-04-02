package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthenticatedRequestResolverTest {

    @Mock
    private SupabaseJwtVerifier supabaseJwtVerifier;

    private SupabaseAuthProperties properties;
    private AuthenticatedRequestResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new SupabaseAuthProperties();
        properties.setRequireJwt(true);
        properties.setAllowEmailHeaderFallback(false);
        resolver = new AuthenticatedRequestResolver(supabaseJwtVerifier, properties);
    }

    @Test
    void resolvesBearerTokenLocallyAndCachesVerifiedIdentityPerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-token");
        when(supabaseJwtVerifier.verify("jwt-token"))
            .thenReturn(Optional.of(new SupabaseJwtVerifier.VerifiedUser("member@hei.gg", "민식")));

        AuthenticatedRequestResolver.ResolvedRequestIdentity first = resolver.resolve(request);
        AuthenticatedRequestResolver.ResolvedRequestIdentity second = resolver.resolve(request);

        assertThat(first.isAuthenticated()).isTrue();
        assertThat(first.jwtVerified()).isTrue();
        assertThat(first.email()).isEqualTo("member@hei.gg");
        assertThat(second.jwtVerified()).isTrue();
        verify(supabaseJwtVerifier, times(1)).verify("jwt-token");
    }

    @Test
    void preservesNonJwtVerificationStateForHeaderFallbackWithinSameRequest() {
        properties.setRequireJwt(false);
        properties.setAllowEmailHeaderFallback(true);
        resolver = new AuthenticatedRequestResolver(supabaseJwtVerifier, properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", "member@hei.gg");
        request.addHeader("X-USER-NICKNAME", "%EB%AF%BC%EC%8B%9D");

        AuthenticatedRequestResolver.ResolvedRequestIdentity first = resolver.resolve(request);
        AuthenticatedRequestResolver.ResolvedRequestIdentity second = resolver.resolve(request);

        assertThat(first.isAuthenticated()).isTrue();
        assertThat(first.jwtVerified()).isFalse();
        assertThat(first.nickname()).isEqualTo("민식");
        assertThat(second.jwtVerified()).isFalse();
    }

    @Test
    void rejectsEmailHeaderFallbackWhenJwtIsRequired() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-USER-EMAIL", "member@hei.gg");

        AuthenticatedRequestResolver.ResolvedRequestIdentity resolved = resolver.resolve(request);

        assertThat(resolved.isAuthenticated()).isFalse();
    }
}
