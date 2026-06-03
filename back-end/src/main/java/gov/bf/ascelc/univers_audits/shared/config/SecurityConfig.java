package gov.bf.ascelc.univers_audits.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── Endpoints publics (sans token) ────────────
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/dossiers/public/track/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/dossiers/public/submit").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/attachments/dossier/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/attachments/dossier/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/attachments/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/stats/public").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/config/types-declarant").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/config/enums").permitAll()

                        // ── Actuator ──────────────────────────────────
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").permitAll()

                        // ── Swagger ───────────────────────────────────
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api-docs/**").permitAll()

                        // ── PDF ───────────────────────────────────────
                        .requestMatchers("/api/v1/pdf/**").permitAll()

                        // ── Tout le reste nécessite une auth ──────────
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "https://portail.asce-lc.bf",
                "https://app.asce-lc.bf"
        ));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept",
                "X-Requested-With", "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        config.setExposedHeaders(List.of(
                "Authorization", "Content-Disposition"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}