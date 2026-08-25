package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.shared.config.RateLimitProperties;
import gov.bf.ascelc.univers_audits.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private FilterChain filterChain;

    private RateLimitProperties properties;
    private CorsConfigurationSource corsConfigurationSource;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        corsConfigurationSource = buildCorsConfigurationSource();
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper(), corsConfigurationSource);
    }

    private static CorsConfigurationSource buildCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://portail.asce-lc.bf"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Test
    void doFilter_laisseSurPasserSiLimiteNonAtteinte() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dossiers/public/submit");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void doFilter_bloqueAvec429SiLimiteAtteinte() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dossiers/public/track/ABCD1234");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_ecritRetryAfterEtCorpsErreur() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dossiers/public/track/ABCD1234");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getContentAsString()).contains("Trop de requêtes");
    }

    @Test
    void doFilter_retryAfterJamaisZero() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dossiers/public/track/ABCD1234");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 0));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void doFilter_ajouteLesEntetesCorsSurLa429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dossiers/public/track/ABCD1234");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Origin", "https://portail.asce-lc.bf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://portail.asce-lc.bf");
    }

    @Test
    void doFilter_laisseSurPasserRouteNonCouverte() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/config/enums");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_laisseSurPasserSiDesactive() throws Exception {
        properties.setEnabled(false);
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper(), corsConfigurationSource);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dossiers/public/submit");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_failOpenSiRedisIndisponible() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dossiers/public/submit");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(429);
    }

    @Test
    void doFilter_appliqueLaBonneRoutePourChaqueMethodeEtPatron() throws Exception {
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        assertRouteMatched("GET", "/api/v1/stats/public", "rl:statsPublic:10.0.0.1", 60, Duration.ofMinutes(1));
        assertRouteMatched("POST", "/api/v1/attachments/dossier/abc-123", "rl:attachmentUpload:10.0.0.1", 10, Duration.ofMinutes(10));
        assertRouteMatched("POST", "/api/v1/dossiers/public/submit", "rl:submit:10.0.0.1", 5, Duration.ofMinutes(10));
        assertRouteMatched("GET", "/api/v1/dossiers/public/track/ABCD1234", "rl:track:10.0.0.1", 20, Duration.ofMinutes(1));
    }

    private void assertRouteMatched(String method, String uri, String expectedKey,
                                     int expectedCapacity, Duration expectedRefill) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiter).tryConsume(eq(expectedKey), eq(expectedCapacity), eq(expectedRefill));
    }

    @Test
    void doFilter_ignoreXForwardedForEtUtiliseRemoteAddr() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dossiers/public/submit");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(rateLimiter).tryConsume(eq("rl:submit:10.0.0.1"), eq(5), eq(Duration.ofMinutes(10)));
    }
}
