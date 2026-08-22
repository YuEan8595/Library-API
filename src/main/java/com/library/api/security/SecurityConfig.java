package com.library.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Everything that is NOT an authorization-server endpoint: the JSON API (protected as an
 * OAuth2 resource server) and the login form the authorization server redirects browsers to
 * during the authorization_code flow (see AuthorizationServerConfig, filter chain @Order(1),
 * which runs first and only matches /oauth2/** and /.well-known/**).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Dev-only demo member accounts, keyed by the email a librarian would register as a
     * borrower. AuthorizationServerConfig's token customizer looks that email up in
     * BorrowerRepository at token-issue time, so any borrower already on file can log in as
     * themselves with no further wiring. Production would replace this with a real
     * credential store (or drop the local authorization server for an external IdP).
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails ada = User.withUsername("ada@example.com")
                .password(passwordEncoder.encode("member-password"))
                .roles("MEMBER")
                .build();
        UserDetails grace = User.withUsername("grace@example.com")
                .password(passwordEncoder.encode("member-password"))
                .roles("MEMBER")
                .build();
        return new InMemoryUserDetailsManager(ada, grace);
    }

    /** Reads the "roles" claim the token customizer adds and maps each entry to a ROLE_* authority. */
    @Bean
    public JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        return authoritiesConverter;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return converter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                       JwtDecoder jwtDecoder,
                                                       JwtAuthenticationConverter jwtAuthenticationConverter,
                                                       ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/login", "/login/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/borrowers").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/borrowers").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/books").hasRole("LIBRARIAN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                // The API is stateless bearer-token auth, not cookies, so CSRF doesn't apply to
                // it; the login form below keeps CSRF protection via its session cookie.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                problemDetailEntryPoint(objectMapper),
                                request -> request.getRequestURI().startsWith("/api/v1/"))
                        .defaultAccessDeniedHandlerFor(
                                problemDetailAccessDeniedHandler(objectMapper),
                                request -> request.getRequestURI().startsWith("/api/v1/")))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    /** Matches the RFC 7807 shape GlobalExceptionHandler uses, so 401s look like every other error. */
    private AuthenticationEntryPoint problemDetailEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeProblem(response, request, objectMapper,
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required");
    }

    private AccessDeniedHandler problemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeProblem(response, request, objectMapper,
                HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Your token does not grant access to this resource");
    }

    private static void writeProblem(HttpServletResponse response, HttpServletRequest request,
                                     ObjectMapper objectMapper, HttpStatus status, String errorCode,
                                     String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://library.example.com/problems/" + errorCode.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());

        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
