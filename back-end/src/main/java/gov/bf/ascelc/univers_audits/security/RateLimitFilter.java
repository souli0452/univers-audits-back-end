package gov.bf.ascelc.univers_audits.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.shared.config.RateLimitProperties;
import gov.bf.ascelc.univers_audits.shared.exceptions.GlobalExceptionHandler;
import gov.bf.ascelc.univers_audits.shared.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();
    private final CorsProcessor corsProcessor = new DefaultCorsProcessor();
    private final List<RouteRule> rules;

    public RateLimitFilter(@Lazy RateLimiter rateLimiter, RateLimitProperties properties,
                            ObjectMapper objectMapper,
                            CorsConfigurationSource corsConfigurationSource) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
        this.rules = List.of(
                new RouteRule("POST", "/api/v1/dossiers/public/submit", "submit",
                        properties.getSubmit().getCapacity(),
                        Duration.ofMinutes(properties.getSubmit().getRefillMinutes())),
                new RouteRule("GET", "/api/v1/dossiers/public/track/**", "track",
                        properties.getTrack().getCapacity(),
                        Duration.ofMinutes(properties.getTrack().getRefillMinutes())),
                new RouteRule("POST", "/api/v1/attachments/dossier/**", "attachmentUpload",
                        properties.getAttachmentUpload().getCapacity(),
                        Duration.ofMinutes(properties.getAttachmentUpload().getRefillMinutes())),
                new RouteRule("GET", "/api/v1/stats/public", "statsPublic",
                        properties.getStatsPublic().getCapacity(),
                        Duration.ofMinutes(properties.getStatsPublic().getRefillMinutes())),
                new RouteRule("GET", "/api/v1/dossiers/public/complement/**", "complementRead",
                        properties.getComplementRead().getCapacity(),
                        Duration.ofMinutes(properties.getComplementRead().getRefillMinutes())),
                new RouteRule("POST", "/api/v1/dossiers/public/complement/**", "complementSubmit",
                        properties.getComplementSubmit().getCapacity(),
                        Duration.ofMinutes(properties.getComplementSubmit().getRefillMinutes()))
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RouteRule matched = findMatchingRule(request);
        if (matched == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        String key = "rl:" + matched.name() + ":" + ip;

        RateLimiter.RateLimitResult result;
        try {
            result = rateLimiter.tryConsume(key, matched.capacity(), matched.refillPeriod());
        } catch (RuntimeException e) {
            log.warn("Rate limiter indisponible, requête laissée passer (fail-open) : {}",
                    e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (!result.allowed()) {
            writeRateLimitedResponse(response, request, result.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RouteRule findMatchingRule(HttpServletRequest request) {
        String path = urlPathHelper.getPathWithinApplication(request);
        for (RouteRule rule : rules) {
            if (rule.method().equalsIgnoreCase(request.getMethod())
                    && pathMatcher.match(rule.pattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private void writeRateLimitedResponse(HttpServletResponse response, HttpServletRequest request,
                                           long retryAfterSeconds) throws IOException {
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(request);
        if (corsConfig != null) {
            corsProcessor.processRequest(corsConfig, request, response);
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType("application/json;charset=UTF-8");

        GlobalExceptionHandler.ErrorResponse body = GlobalExceptionHandler.ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED",
                "Trop de requêtes, veuillez réessayer plus tard.",
                request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);
    }

    private record RouteRule(String method, String pattern, String name, int capacity,
                              Duration refillPeriod) {}
}
