package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.shared.config.RateLimitProperties;
import gov.bf.ascelc.univers_audits.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper());
    }

    @Test
    void doFilter_laisseSurPasserSiLimiteNonAtteinte() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/submit");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilter_bloqueAvec429SiLimiteAtteinte() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/track/ABCD1234");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_ecritRetryAfterEtCorpsErreur() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/track/ABCD1234");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(false, 42));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("Retry-After", "42");
        assertThat(sw.toString()).contains("RATE_LIMITED");
        assertThat(sw.toString()).contains("Trop de requêtes");
    }

    @Test
    void doFilter_laisseSurPasserRouteNonCouverte() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/config/enums");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_laisseSurPasserSiDesactive() throws Exception {
        properties.setEnabled(false);
        filter = new RateLimitFilter(rateLimiter, properties, new ObjectMapper());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void doFilter_failOpenSiRedisIndisponible() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/dossiers/public/submit");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void doFilter_appliqueLaBonneRoutePourChaqueMethodeEtPatron() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(rateLimiter.tryConsume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.RateLimitResult(true, 0));

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/stats/public");
        filter.doFilterInternal(request, response, filterChain);
        verify(rateLimiter).tryConsume(eq("rl:statsPublic:10.0.0.1"), eq(60), eq(Duration.ofMinutes(1)));

        reset(request, filterChain);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/attachments/dossier/abc-123");
        filter.doFilterInternal(request, response, filterChain);
        verify(rateLimiter).tryConsume(eq("rl:attachmentUpload:10.0.0.1"), eq(10), eq(Duration.ofMinutes(10)));
    }
}
